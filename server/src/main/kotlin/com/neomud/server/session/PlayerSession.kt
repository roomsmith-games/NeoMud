package com.neomud.server.session

import com.neomud.server.game.GameConfig
import com.neomud.server.game.progression.Perception
import com.neomud.server.persistence.repository.PlayerRepository
import com.neomud.shared.model.*
import com.neomud.shared.model.FollowState
import com.neomud.shared.protocol.MessageSerializer
import com.neomud.shared.protocol.ServerMessage
import io.ktor.websocket.*

sealed class PendingSkill {
    data class Bash(val targetId: String?) : PendingSkill()
    data class Kick(val targetId: String?, val direction: Direction) : PendingSkill()
    object Meditate : PendingSkill()
    object Rest : PendingSkill()
    data class Track(val targetId: String?) : PendingSkill()
    data class UseItem(val itemId: String) : PendingSkill()
    data class CastSpell(val spellId: String, val targetId: String?) : PendingSkill()
    object Sneak : PendingSkill()
    data class PickLock(val targetId: String) : PendingSkill()
}

class PlayerSession(
    val webSocketSession: WebSocketSession
) {
    var playerName: String? = null
    var currentRoomId: RoomId? = null
    var player: Player? = null
    val activeEffects: MutableList<ActiveEffect> = mutableListOf()
    var attackMode: Boolean = false
    var selectedTargetId: String? = null
    var isHidden: Boolean = false
    var isMeditating: Boolean = false
    var isResting: Boolean = false
    var godMode: Boolean = false
    var combatGraceTicks: Int = 0
    var readiedSpellId: String? = null
    var pendingSkill: PendingSkill? = null
    val skillCooldowns: MutableMap<String, Int> = mutableMapOf()
    val visitedRooms: MutableSet<String> = mutableSetOf()
    val discoveredHiddenExits: MutableSet<String> = mutableSetOf()
    val discoveredLockedExits: MutableSet<String> = mutableSetOf()

    // Protocol versioning
    var clientProtocolVersion: Int = 1
    var clientVersion: String = ""

    // Platform auth (set when ClientHello includes a valid platformToken)
    var platformUserId: String? = null
    var platformRole: String? = null

    // Guest/ephemeral session — data deleted on disconnect
    var isGuest: Boolean = false
    var remoteIp: String = ""

    // Party + follow
    var partyId: String? = null
    var followTarget: String? = null
    var followState: FollowState = FollowState.OFF

    // Tutorial tracking
    val seenTutorials: MutableSet<String> = mutableSetOf()
    var firstKillDone: Boolean = false
    var inCombat: Boolean = false

    // Interactable state
    val discoveredInteractables: MutableSet<String> = mutableSetOf()  // "roomId::featureId"
    val interactableCooldowns: MutableMap<String, Int> = mutableMapOf() // "roomId::featureId" -> ticks

    // Trap state — once a passive trap has fired against this character, don't
    // re-fire on subsequent room entries until something resets it. In-memory
    // for MVP; persists for the session only.
    val trippedTraps: MutableSet<String> = mutableSetOf()  // "roomId::featureId"

    fun hasDiscoveredExit(roomId: RoomId, direction: Direction): Boolean =
        "$roomId:$direction" in discoveredHiddenExits

    fun discoverExit(roomId: RoomId, direction: Direction) {
        discoveredHiddenExits.add("$roomId:$direction")
    }

    fun forgetExit(roomId: RoomId, direction: Direction) {
        discoveredHiddenExits.remove("$roomId:$direction")
    }

    fun hasDiscoveredLock(roomId: RoomId, direction: Direction): Boolean =
        "$roomId:$direction" in discoveredLockedExits

    fun discoverLock(roomId: RoomId, direction: Direction) {
        discoveredLockedExits.add("$roomId:$direction")
    }

    fun hasDiscoveredInteractable(roomId: RoomId, featureId: String): Boolean =
        "$roomId::$featureId" in discoveredInteractables

    fun discoverInteractable(roomId: RoomId, featureId: String) {
        discoveredInteractables.add("$roomId::$featureId")
    }

    fun hasTrippedTrap(roomId: RoomId, featureId: String): Boolean =
        "$roomId::$featureId" in trippedTraps

    fun markTrapTripped(roomId: RoomId, featureId: String) {
        trippedTraps.add("$roomId::$featureId")
    }

    // Rate limiting (token bucket)
    private var messageTokens: Double = GameConfig.RateLimit.BURST_CAPACITY.toDouble()
    private var lastRefillTime: Long = System.currentTimeMillis()

    fun tryConsumeMessage(): Boolean {
        val now = System.currentTimeMillis()
        val elapsed = (now - lastRefillTime) / 1000.0
        lastRefillTime = now
        messageTokens = (messageTokens + elapsed * GameConfig.RateLimit.MAX_MESSAGES_PER_SECOND)
            .coerceAtMost(GameConfig.RateLimit.BURST_CAPACITY.toDouble())
        return if (messageTokens >= 1.0) {
            messageTokens -= 1.0
            true
        } else false
    }

    val isAuthenticated: Boolean get() = player != null

    /** Base stats + active buff magnitudes. */
    fun effectiveStats(): Stats {
        val p = player ?: return Stats()
        var str = p.stats.strength
        var agi = p.stats.agility
        var int = p.stats.intellect
        var wil = p.stats.willpower
        for (e in activeEffects) {
            when (e.type) {
                EffectType.BUFF_STRENGTH -> str += e.magnitude
                EffectType.BUFF_AGILITY -> agi += e.magnitude
                EffectType.BUFF_INTELLECT -> int += e.magnitude
                EffectType.BUFF_WILLPOWER -> wil += e.magnitude
                else -> {}
            }
        }
        return p.stats.copy(strength = str, agility = agi, intellect = int, willpower = wil)
    }

    /** Derived perception from effective stats — picks up INT/AGI buffs automatically. */
    fun effectivePerception(): Int = Perception.compute(effectiveStats())

    /** Sum of BUFF_DAMAGE magnitudes from active effects. */
    fun effectiveDamageBonus(): Int =
        activeEffects.filter { it.type == EffectType.BUFF_DAMAGE }.sumOf { it.magnitude }

    /** Base maxHp + sum of BUFF_MAX_HP magnitudes from active effects. */
    fun effectiveMaxHp(): Int {
        val p = player ?: return 0
        return p.maxHp + activeEffects.filter { it.type == EffectType.BUFF_MAX_HP }.sumOf { it.magnitude }
    }

    fun toPlayerInfo(): PlayerInfo? {
        val p = player ?: return null
        return PlayerInfo(
            name = p.name,
            characterClass = p.characterClass,
            race = p.race,
            gender = p.gender,
            level = p.level,
            spriteUrl = PlayerRepository.pcSpriteRelativePath(p.race, p.gender, p.characterClass)
        )
    }

    suspend fun send(message: ServerMessage) {
        val text = MessageSerializer.encodeServerMessage(message)
        webSocketSession.send(Frame.Text(text))
    }

    companion object {
        val MAX_MESSAGES_PER_SECOND = GameConfig.RateLimit.MAX_MESSAGES_PER_SECOND
        val BURST_CAPACITY = GameConfig.RateLimit.BURST_CAPACITY
    }
}
