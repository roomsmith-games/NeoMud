package com.neomud.server.game.commands

import com.neomud.server.game.GameConfig
import com.neomud.server.game.MeditationUtils
import com.neomud.server.game.RestUtils
import com.neomud.server.game.StealthUtils
import com.neomud.server.game.npc.NpcManager
import com.neomud.server.game.npc.NpcState
import com.neomud.server.game.party.PartyService
import com.neomud.server.persistence.repository.PlayerRepository
import com.neomud.server.session.PendingSkill
import com.neomud.server.session.PlayerSession
import com.neomud.server.session.SessionManager
import com.neomud.server.world.ClassCatalog
import com.neomud.server.world.SpellCatalog
import com.neomud.shared.model.*
import com.neomud.shared.protocol.ServerMessage

class SpellCommand(
    private val spellCatalog: SpellCatalog,
    private val classCatalog: ClassCatalog,
    private val npcManager: NpcManager,
    private val sessionManager: SessionManager,
    private val playerRepository: PlayerRepository,
    private val partyService: PartyService? = null
) {
    /**
     * Auto-cast a readied spell during the combat tick.
     * Returns true if the spell was cast, false if skipped (cooldown/no target).
     * Clears readiedSpellId if out of mana.
     */
    suspend fun autoCast(session: PlayerSession, spellId: String, targetId: String?, roomId: String): Boolean {
        val player = session.player ?: return false
        val playerName = session.playerName ?: return false

        val spell = spellCatalog.getSpell(spellId) ?: run {
            session.readiedSpellId = null
            return false
        }

        // Cooldown — silently skip, wait for next tick
        val cooldown = session.skillCooldowns[spellId]
        if (cooldown != null && cooldown > 0) return false

        // ALLY auto-cast: find best target in room
        if (spell.targetType == TargetType.ALLY) {
            return autoCastAlly(session, spell, spellId, roomId, playerName)
        }

        // PARTY_ROOM auto-cast: same as manual, hits all party in room
        if (spell.targetType == TargetType.PARTY_ROOM) {
            return autoCastPartyRoom(session, spell, spellId, roomId, playerName)
        }

        // SELF spells: skip heal if at full HP
        if (spell.targetType == TargetType.SELF && spell.spellType == SpellType.HEAL) {
            if (player.currentHp >= session.effectiveMaxHp()) return false
        }

        // MP check — clear readied spell if out of mana
        if (player.currentMp < spell.manaCost) {
            session.send(ServerMessage.SpellCastResult(false, spell.name, "Not enough mana! (need ${spell.manaCost}, have ${player.currentMp})", player.currentMp))
            session.readiedSpellId = null
            return false
        }

        // Deduct MP
        val newMp = player.currentMp - spell.manaCost
        session.player = player.copy(currentMp = newMp)

        // Set cooldown
        if (spell.cooldownTicks > 0) {
            session.skillCooldowns[spellId] = spell.cooldownTicks
        }

        val power = calculatePower(session, spell)

        when (spell.spellType) {
            SpellType.DAMAGE -> handleDamage(session, spell, power, targetId, roomId, playerName)
            SpellType.DOT -> handleDot(session, spell, power, targetId, roomId, playerName)
            SpellType.HEAL -> handleHeal(session, session, spell, power, playerName)
            SpellType.BUFF -> handleBuff(session, session, spell, power, playerName)
            SpellType.HOT -> handleHot(session, session, spell, power, playerName)
        }

        session.player?.let { playerRepository.savePlayerState(it) }
        return true
    }

    private suspend fun autoCastAlly(
        session: PlayerSession, spell: SpellDef, spellId: String,
        roomId: String, playerName: String
    ): Boolean {
        val player = session.player ?: return false
        val targets = resolvePartyMembersInRoom(session, roomId)

        // Self-priority below threshold
        val selfHpPercent = player.currentHp.toDouble() / session.effectiveMaxHp()
        if (selfHpPercent < GameConfig.Skills.AUTO_HEAL_SELF_PRIORITY_THRESHOLD) {
            if (spell.spellType == SpellType.HEAL && player.currentHp < session.effectiveMaxHp()) {
                return castOnAlly(session, session, spell, spellId, playerName)
            }
        }

        // Find lowest HP% ally in room (including self)
        val bestTarget = when (spell.spellType) {
            SpellType.HEAL -> {
                targets.filter {
                    val tp = it.player ?: return@filter false
                    tp.currentHp < it.effectiveMaxHp()
                }.minByOrNull { s ->
                    val p = s.player!!
                    p.currentHp.toDouble() / s.effectiveMaxHp()
                }
            }
            else -> targets.firstOrNull { it != session } ?: session
        }

        if (bestTarget == null) return false

        return castOnAlly(session, bestTarget, spell, spellId, playerName)
    }

    private suspend fun autoCastPartyRoom(
        session: PlayerSession, spell: SpellDef, spellId: String,
        roomId: String, playerName: String
    ): Boolean {
        val player = session.player ?: return false
        val targets = resolvePartyMembersInRoom(session, roomId)

        // For heals, skip if no one is injured
        if (spell.spellType == SpellType.HEAL) {
            val anyInjured = targets.any { s ->
                val p = s.player ?: return@any false
                p.currentHp < s.effectiveMaxHp()
            }
            if (!anyInjured) return false
        }

        // MP check
        if (player.currentMp < spell.manaCost) {
            session.send(ServerMessage.SpellCastResult(false, spell.name, "Not enough mana! (need ${spell.manaCost}, have ${player.currentMp})", player.currentMp))
            session.readiedSpellId = null
            return false
        }

        val newMp = player.currentMp - spell.manaCost
        session.player = player.copy(currentMp = newMp)

        if (spell.cooldownTicks > 0) {
            session.skillCooldowns[spellId] = spell.cooldownTicks
        }

        val power = calculatePower(session, spell)
        handleGroupSpell(session, spell, power, targets, playerName)

        session.player?.let { playerRepository.savePlayerState(it) }
        return true
    }

    private suspend fun castOnAlly(
        caster: PlayerSession, target: PlayerSession, spell: SpellDef,
        spellId: String, casterName: String
    ): Boolean {
        val player = caster.player ?: return false

        if (player.currentMp < spell.manaCost) {
            caster.send(ServerMessage.SpellCastResult(false, spell.name, "Not enough mana! (need ${spell.manaCost}, have ${player.currentMp})", player.currentMp))
            caster.readiedSpellId = null
            return false
        }

        val newMp = player.currentMp - spell.manaCost
        caster.player = player.copy(currentMp = newMp)

        if (spell.cooldownTicks > 0) {
            caster.skillCooldowns[spellId] = spell.cooldownTicks
        }

        val power = calculatePower(caster, spell)

        when (spell.spellType) {
            SpellType.HEAL -> handleHeal(caster, target, spell, power, casterName)
            SpellType.BUFF -> handleBuff(caster, target, spell, power, casterName)
            SpellType.HOT -> handleHot(caster, target, spell, power, casterName)
            else -> return false
        }

        registerHealThreat(caster, target, casterName)
        caster.player?.let { playerRepository.savePlayerState(it) }
        return true
    }

    /**
     * Validate a manually-cast spell and queue it for resolution on the next game tick.
     * No MP deduction, damage, or state changes happen here — only validation.
     */
    suspend fun execute(session: PlayerSession, spellId: String, targetId: String?) {
        val player = session.player ?: return
        if (player.currentHp <= 0) return

        // Casting a spell breaks meditation, rest, and stealth
        MeditationUtils.breakMeditation(session, "You stop meditating.")
        RestUtils.breakRest(session, "You stop resting.")
        StealthUtils.breakStealth(session, sessionManager, "Casting a spell reveals your presence!")

        val spell = spellCatalog.getSpell(spellId)
        if (spell == null) {
            session.send(ServerMessage.SpellCastResult(false, spellId, "Unknown spell.", player.currentMp))
            return
        }

        // Validate class has school access at required tier
        val classDef = classCatalog.getClass(player.characterClass)
        val schoolLevel = classDef?.magicSchools?.get(spell.school)
        if (schoolLevel == null) {
            session.send(ServerMessage.SpellCastResult(false, spell.name, "Your class cannot cast ${spell.school} spells.", player.currentMp))
            return
        }
        if (schoolLevel < spell.schoolLevel) {
            session.send(ServerMessage.SpellCastResult(false, spell.name, "Your training in ${spell.school} magic is not advanced enough.", player.currentMp))
            return
        }

        // Validate level
        if (player.level < spell.levelRequired) {
            session.send(ServerMessage.SpellCastResult(false, spell.name, "You need level ${spell.levelRequired} to cast ${spell.name}.", player.currentMp))
            return
        }

        // Validate MP
        if (player.currentMp < spell.manaCost) {
            session.send(ServerMessage.SpellCastResult(false, spell.name, "Not enough mana! (need ${spell.manaCost}, have ${player.currentMp})", player.currentMp))
            return
        }

        // Validate cooldown
        val cooldown = session.skillCooldowns[spellId]
        if (cooldown != null && cooldown > 0) {
            session.send(ServerMessage.SpellCastResult(false, spell.name, "${spell.name} is on cooldown ($cooldown ticks remaining).", player.currentMp))
            return
        }

        // Queue for resolution on next game tick
        session.pendingSkill = PendingSkill.CastSpell(spellId, targetId)
    }

    /**
     * Resolve a queued spell during the game tick. Deducts MP, applies effects,
     * and returns the NPC target (if offensive) for kill checking by GameLoop.
     */
    suspend fun resolve(session: PlayerSession, spellId: String, targetId: String?): NpcState? {
        val roomId = session.currentRoomId ?: return null
        val playerName = session.playerName ?: return null
        val player = session.player ?: return null

        val spell = spellCatalog.getSpell(spellId) ?: return null

        // Re-validate MP (may have changed between queue and tick)
        if (player.currentMp < spell.manaCost) {
            session.send(ServerMessage.SpellCastResult(false, spell.name, "Not enough mana! (need ${spell.manaCost}, have ${player.currentMp})", player.currentMp))
            return null
        }

        // Re-validate cooldown (another spell may have set it)
        val cooldown = session.skillCooldowns[spellId]
        if (cooldown != null && cooldown > 0) {
            session.send(ServerMessage.SpellCastResult(false, spell.name, "${spell.name} is on cooldown ($cooldown ticks remaining).", player.currentMp))
            return null
        }

        // For offensive spells, validate target BEFORE spending MP
        val isOffensive = spell.spellType == SpellType.DAMAGE || spell.spellType == SpellType.DOT
        if (isOffensive) {
            val target = resolveTarget(session, targetId, roomId)
            if (target == null) {
                session.send(ServerMessage.SpellCastResult(false, spell.name, "No valid target.", player.currentMp))
                return null
            }
        }

        // ALLY: resolve target before spending MP
        if (spell.targetType == TargetType.ALLY) {
            val allyTarget = resolveAllyTarget(session, targetId, roomId)
            if (allyTarget == null) {
                session.send(ServerMessage.SpellCastResult(false, spell.name, "No valid ally target.", player.currentMp))
                return null
            }

            // For heals, check if target is at full HP
            if (spell.spellType == SpellType.HEAL) {
                val tp = allyTarget.player
                if (tp != null && tp.currentHp >= allyTarget.effectiveMaxHp()) {
                    val targetName = allyTarget.playerName ?: "ally"
                    session.send(ServerMessage.SpellCastResult(false, spell.name, "$targetName is already at full health.", player.currentMp))
                    return null
                }
            }

            // Deduct MP + set cooldown
            val newMp = player.currentMp - spell.manaCost
            session.player = player.copy(currentMp = newMp)
            if (spell.cooldownTicks > 0) {
                session.skillCooldowns[spellId] = spell.cooldownTicks
            }

            val power = calculatePower(session, spell)
            when (spell.spellType) {
                SpellType.HEAL -> handleHeal(session, allyTarget, spell, power, playerName)
                SpellType.BUFF -> handleBuff(session, allyTarget, spell, power, playerName)
                SpellType.HOT -> handleHot(session, allyTarget, spell, power, playerName)
                else -> {}
            }
            registerHealThreat(session, allyTarget, playerName)
            session.player?.let { playerRepository.savePlayerState(it) }
            return null
        }

        // PARTY_ROOM: resolve all targets
        if (spell.targetType == TargetType.PARTY_ROOM) {
            val targets = resolvePartyMembersInRoom(session, roomId)

            // For heals, skip if nobody injured
            if (spell.spellType == SpellType.HEAL) {
                val anyInjured = targets.any { s ->
                    val p = s.player ?: return@any false
                    p.currentHp < s.effectiveMaxHp()
                }
                if (!anyInjured) {
                    session.send(ServerMessage.SpellCastResult(false, spell.name, "No allies need healing.", player.currentMp))
                    return null
                }
            }

            val newMp = player.currentMp - spell.manaCost
            session.player = player.copy(currentMp = newMp)
            if (spell.cooldownTicks > 0) {
                session.skillCooldowns[spellId] = spell.cooldownTicks
            }

            val power = calculatePower(session, spell)
            handleGroupSpell(session, spell, power, targets, playerName)
            session.player?.let { playerRepository.savePlayerState(it) }
            return null
        }

        // For heal spells, refuse if already at full HP (including buffs)
        if (spell.spellType == SpellType.HEAL && player.currentHp >= session.effectiveMaxHp()) {
            session.send(ServerMessage.SpellCastResult(false, spell.name, "You are already at full health.", player.currentMp))
            return null
        }

        // Deduct MP
        val newMp = player.currentMp - spell.manaCost
        session.player = player.copy(currentMp = newMp)

        // Set cooldown
        if (spell.cooldownTicks > 0) {
            session.skillCooldowns[spellId] = spell.cooldownTicks
        }

        val power = calculatePower(session, spell)

        val target = when (spell.spellType) {
            SpellType.DAMAGE -> handleDamage(session, spell, power, targetId, roomId, playerName)
            SpellType.DOT -> handleDot(session, spell, power, targetId, roomId, playerName)
            SpellType.HEAL -> { handleHeal(session, session, spell, power, playerName); null }
            SpellType.BUFF -> { handleBuff(session, session, spell, power, playerName); null }
            SpellType.HOT -> { handleHot(session, session, spell, power, playerName); null }
        }

        // Persist player state
        session.player?.let { playerRepository.savePlayerState(it) }
        return target
    }

    private fun calculatePower(session: PlayerSession, spell: SpellDef): Int {
        val player = session.player ?: return spell.basePower
        val effStats = session.effectiveStats()
        val statValue = when (spell.primaryStat) {
            "intellect" -> effStats.intellect
            "willpower" -> effStats.willpower
            "charm" -> effStats.charm
            "strength" -> effStats.strength
            "agility" -> effStats.agility
            else -> effStats.intellect
        }
        val isHealType = spell.spellType == SpellType.HEAL || spell.spellType == SpellType.HOT
        val statDivisor = if (isHealType) GameConfig.Skills.HEAL_STAT_DIVISOR else GameConfig.Skills.SPELL_POWER_STAT_DIVISOR
        return spell.basePower + statValue / statDivisor + player.level / GameConfig.Skills.SPELL_POWER_LEVEL_DIVISOR + (1..GameConfig.Skills.SPELL_POWER_DICE_SIZE).random()
    }

    internal fun resolveAllyTarget(session: PlayerSession, targetId: String?, roomId: String): PlayerSession? {
        val playerName = session.playerName ?: return null

        // If no target specified, fall back to self
        if (targetId == null) return session

        // Look up target by name
        val target = sessionManager.getSession(targetId)
        if (target == null) return session

        val tp = target.player ?: return session
        if (tp.currentHp <= 0) return session
        if (target.currentRoomId != roomId) return session

        // Must be in same party (or targeting self)
        if (target.playerName == playerName) return session
        val ps = partyService ?: return session
        val party = ps.getPartyForPlayer(playerName) ?: return session
        if (target.playerName !in party.members) return session

        return target
    }

    internal fun resolvePartyMembersInRoom(session: PlayerSession, roomId: String): List<PlayerSession> {
        val playerName = session.playerName ?: return listOf(session)
        val ps = partyService
        if (ps == null) return listOf(session)

        val members = ps.getMembersInRoom(playerName, roomId) { name ->
            sessionManager.getSession(name)?.currentRoomId
        }

        return members.mapNotNull { name ->
            val s = sessionManager.getSession(name)
            if (s != null && (s.player?.currentHp ?: 0) > 0) s else null
        }.ifEmpty { listOf(session) }
    }

    private suspend fun registerHealThreat(caster: PlayerSession, target: PlayerSession, casterName: String) {
        if (caster == target) return
        val targetName = target.playerName ?: return
        val roomId = target.currentRoomId ?: return

        for (npc in npcManager.getLivingHostileNpcsInRoom(roomId)) {
            if (targetName in npc.engagedPlayerIds) {
                npc.engagedPlayerIds.add(casterName)
            }
        }
    }

    private suspend fun handleDamage(
        session: PlayerSession, spell: SpellDef, power: Int,
        targetId: String?, roomId: String, playerName: String
    ): NpcState? {
        // Offensive spell breaks grace period
        session.combatGraceTicks = 0
        val target = resolveTarget(session, targetId, roomId)
        if (target == null) {
            session.send(ServerMessage.SpellCastResult(false, spell.name, "No valid target.", session.player!!.currentMp))
            return null
        }

        target.engagedPlayerIds.add(playerName)
        target.currentHp -= power
        val castMsg = "$playerName ${spell.castMessage} ${target.name} for $power damage!"

        session.send(ServerMessage.SpellCastResult(true, spell.name, castMsg, session.player!!.currentMp))

        sessionManager.broadcastToRoom(
            roomId,
            ServerMessage.SpellEffect(
                casterName = playerName,
                targetName = target.name,
                spellName = spell.name,
                effectAmount = power,
                targetNewHp = target.currentHp.coerceAtLeast(0),
                targetMaxHp = target.maxHp,
                targetId = target.id
            )
        )

        return target
    }

    private suspend fun handleDot(
        session: PlayerSession, spell: SpellDef, power: Int,
        targetId: String?, roomId: String, playerName: String
    ): NpcState? {
        // Offensive spell breaks grace period
        session.combatGraceTicks = 0
        val target = resolveTarget(session, targetId, roomId)
        if (target == null) {
            session.send(ServerMessage.SpellCastResult(false, spell.name, "No valid target.", session.player!!.currentMp))
            return null
        }

        target.engagedPlayerIds.add(playerName)
        // Apply initial damage
        val initialDmg = power / GameConfig.Skills.DOT_INITIAL_DAMAGE_DIVISOR
        target.currentHp -= initialDmg

        // Apply ongoing DoT effect to the NPC
        val effectType = try {
            EffectType.valueOf(spell.effectType)
        } catch (_: IllegalArgumentException) {
            EffectType.POISON
        }
        val effect = ActiveEffect(
            name = spell.name,
            type = effectType,
            remainingTicks = spell.effectDuration,
            magnitude = spell.tickPower,
            casterId = playerName
        )
        target.activeEffects.removeAll { it.name == spell.name }
        target.activeEffects.add(effect)

        val tickInfo = if (spell.tickPower > 0 && spell.effectDuration > 0)
            ", ${spell.tickPower} dmg/tick for ${spell.effectDuration} ticks" else ""
        val castMsg = "$playerName ${spell.castMessage} ${target.name}! ($initialDmg initial damage$tickInfo)"

        session.send(ServerMessage.SpellCastResult(true, spell.name, castMsg, session.player!!.currentMp))

        sessionManager.broadcastToRoom(
            roomId,
            ServerMessage.SpellEffect(
                casterName = playerName,
                targetName = target.name,
                spellName = spell.name,
                effectAmount = initialDmg,
                targetNewHp = target.currentHp.coerceAtLeast(0),
                targetMaxHp = target.maxHp,
                targetId = target.id
            )
        )

        return target
    }

    private suspend fun handleHeal(
        caster: PlayerSession, target: PlayerSession,
        spell: SpellDef, power: Int, casterName: String
    ) {
        val targetPlayer = target.player!!
        val effectiveMax = target.effectiveMaxHp()
        val newHp = (targetPlayer.currentHp + power).coerceAtMost(effectiveMax)
        val healed = newHp - targetPlayer.currentHp
        target.player = targetPlayer.copy(currentHp = newHp)

        val targetName = target.playerName ?: casterName
        val isSelf = caster == target

        val castMsg = if (isSelf) {
            "$casterName ${spell.castMessage} and recovers $healed HP!"
        } else {
            "$casterName ${spell.castMessage} on $targetName, restoring $healed HP!"
        }
        caster.send(ServerMessage.SpellCastResult(
            true, spell.name, castMsg, caster.player!!.currentMp, if (isSelf) newHp else null,
            targetName = if (!isSelf) targetName else null
        ))

        // Notify target if different from caster
        if (!isSelf) {
            target.send(ServerMessage.SpellCastResult(
                true, spell.name, "$casterName heals you for $healed HP!", target.player!!.currentMp, newHp,
                targetName = targetName
            ))
            if (target.player != targetPlayer) {
                target.player?.let { playerRepository.savePlayerState(it) }
            }
        }

        sessionManager.broadcastToRoom(
            caster.currentRoomId!!,
            ServerMessage.SpellEffect(
                casterName = casterName,
                targetName = targetName,
                spellName = spell.name,
                effectAmount = healed,
                targetNewHp = newHp,
                targetMaxHp = targetPlayer.maxHp,
                isPlayerTarget = true
            )
        )
    }

    private suspend fun handleBuff(
        caster: PlayerSession, target: PlayerSession,
        spell: SpellDef, power: Int, casterName: String
    ) {
        val effectType = when (spell.effectType) {
            "BUFF_STRENGTH" -> EffectType.BUFF_STRENGTH
            "BUFF_AGILITY" -> EffectType.BUFF_AGILITY
            "BUFF_INTELLECT" -> EffectType.BUFF_INTELLECT
            "BUFF_WILLPOWER" -> EffectType.BUFF_WILLPOWER
            "HASTE" -> EffectType.HASTE
            else -> EffectType.BUFF_STRENGTH
        }

        val effect = ActiveEffect(
            name = spell.name,
            type = effectType,
            remainingTicks = spell.effectDuration,
            magnitude = power
        )

        // Remove existing effect of same name, then add
        target.activeEffects.removeAll { it.name == spell.name }
        target.activeEffects.add(effect)

        val targetName = target.playerName ?: casterName
        val isSelf = caster == target

        val castMsg = if (isSelf) {
            "$casterName ${spell.castMessage}! (+$power ${effectType.name.removePrefix("BUFF_").lowercase()} for ${spell.effectDuration} ticks)"
        } else {
            "$casterName ${spell.castMessage} on $targetName! (+$power ${effectType.name.removePrefix("BUFF_").lowercase()} for ${spell.effectDuration} ticks)"
        }
        caster.send(ServerMessage.SpellCastResult(true, spell.name, castMsg, caster.player!!.currentMp))
        caster.send(ServerMessage.ActiveEffectsUpdate(caster.activeEffects.toList()))

        if (!isSelf) {
            target.send(ServerMessage.SpellCastResult(true, spell.name, "$casterName buffs you with ${spell.name}!", target.player!!.currentMp))
            target.send(ServerMessage.ActiveEffectsUpdate(target.activeEffects.toList()))
        }
    }

    private suspend fun handleHot(
        caster: PlayerSession, target: PlayerSession,
        spell: SpellDef, power: Int, casterName: String
    ) {
        val hotMagnitude = if (spell.tickPower > 0) spell.tickPower else power
        val effect = ActiveEffect(
            name = spell.name,
            type = EffectType.HEAL_OVER_TIME,
            remainingTicks = spell.effectDuration,
            magnitude = hotMagnitude
        )

        target.activeEffects.removeAll { it.name == spell.name }
        target.activeEffects.add(effect)

        val targetName = target.playerName ?: casterName
        val isSelf = caster == target

        val castMsg = if (isSelf) {
            "$casterName ${spell.castMessage}! (heals $hotMagnitude/tick for ${spell.effectDuration} ticks)"
        } else {
            "$casterName ${spell.castMessage} on $targetName! (heals $hotMagnitude/tick for ${spell.effectDuration} ticks)"
        }
        caster.send(ServerMessage.SpellCastResult(true, spell.name, castMsg, caster.player!!.currentMp))
        caster.send(ServerMessage.ActiveEffectsUpdate(caster.activeEffects.toList()))

        if (!isSelf) {
            target.send(ServerMessage.SpellCastResult(true, spell.name, "$casterName applies ${spell.name} to you!", target.player!!.currentMp))
            target.send(ServerMessage.ActiveEffectsUpdate(target.activeEffects.toList()))
        }
    }

    private suspend fun handleGroupSpell(
        caster: PlayerSession, spell: SpellDef, basePower: Int,
        targets: List<PlayerSession>, casterName: String
    ) {
        val scaledPower = (basePower * GameConfig.Skills.GROUP_HEAL_POWER_RATIO).toInt()

        when (spell.spellType) {
            SpellType.HEAL -> {
                var totalHealed = 0
                for (target in targets) {
                    val tp = target.player ?: continue
                    if (tp.currentHp >= target.effectiveMaxHp()) continue
                    val effectiveMax = target.effectiveMaxHp()
                    val newHp = (tp.currentHp + scaledPower).coerceAtMost(effectiveMax)
                    val healed = newHp - tp.currentHp
                    target.player = tp.copy(currentHp = newHp)
                    totalHealed += healed

                    target.player?.let { playerRepository.savePlayerState(it) }

                    sessionManager.broadcastToRoom(
                        caster.currentRoomId!!,
                        ServerMessage.SpellEffect(
                            casterName = casterName,
                            targetName = target.playerName ?: casterName,
                            spellName = spell.name,
                            effectAmount = healed,
                            targetNewHp = newHp,
                            targetMaxHp = tp.maxHp,
                            isPlayerTarget = true
                        )
                    )

                    registerHealThreat(caster, target, casterName)
                }
                caster.send(ServerMessage.SpellCastResult(
                    true, spell.name,
                    "$casterName ${spell.castMessage}! (healed ${targets.size} allies for $scaledPower each)",
                    caster.player!!.currentMp
                ))
            }
            SpellType.BUFF -> {
                val effectType = when (spell.effectType) {
                    "BUFF_STRENGTH" -> EffectType.BUFF_STRENGTH
                    "BUFF_AGILITY" -> EffectType.BUFF_AGILITY
                    "BUFF_INTELLECT" -> EffectType.BUFF_INTELLECT
                    "BUFF_WILLPOWER" -> EffectType.BUFF_WILLPOWER
                    "HASTE" -> EffectType.HASTE
                    else -> EffectType.BUFF_STRENGTH
                }
                for (target in targets) {
                    val effect = ActiveEffect(
                        name = spell.name,
                        type = effectType,
                        remainingTicks = spell.effectDuration,
                        magnitude = scaledPower
                    )
                    target.activeEffects.removeAll { it.name == spell.name }
                    target.activeEffects.add(effect)
                    target.send(ServerMessage.ActiveEffectsUpdate(target.activeEffects.toList()))
                    registerHealThreat(caster, target, casterName)
                }
                caster.send(ServerMessage.SpellCastResult(
                    true, spell.name,
                    "$casterName ${spell.castMessage}! (+$scaledPower ${spell.effectType.removePrefix("BUFF_").lowercase()} to ${targets.size} allies)",
                    caster.player!!.currentMp
                ))
            }
            SpellType.HOT -> {
                val hotMagnitude = if (spell.tickPower > 0) {
                    (spell.tickPower * GameConfig.Skills.GROUP_HEAL_POWER_RATIO).toInt()
                } else scaledPower
                for (target in targets) {
                    val effect = ActiveEffect(
                        name = spell.name,
                        type = EffectType.HEAL_OVER_TIME,
                        remainingTicks = spell.effectDuration,
                        magnitude = hotMagnitude
                    )
                    target.activeEffects.removeAll { it.name == spell.name }
                    target.activeEffects.add(effect)
                    target.send(ServerMessage.ActiveEffectsUpdate(target.activeEffects.toList()))
                    registerHealThreat(caster, target, casterName)
                }
                caster.send(ServerMessage.SpellCastResult(
                    true, spell.name,
                    "$casterName ${spell.castMessage}! (heals $hotMagnitude/tick for ${spell.effectDuration} ticks on ${targets.size} allies)",
                    caster.player!!.currentMp
                ))
            }
            else -> {}
        }
    }

    private fun resolveTarget(session: PlayerSession, targetId: String?, roomId: String): NpcState? {
        val resolvedId = targetId ?: session.selectedTargetId
        return if (resolvedId != null) {
            val npc = npcManager.getNpcState(resolvedId)
            if (npc != null && npc.currentRoomId == roomId && npc.isAlive && npc.hostile) npc else null
        } else {
            npcManager.getLivingHostileNpcsInRoom(roomId).firstOrNull()
        }
    }
}
