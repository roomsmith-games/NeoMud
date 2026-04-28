package com.neomud.client.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neomud.client.platform.PlatformAudioManager
import com.neomud.client.platform.PlatformLogger
import com.neomud.client.network.GameConnection
import com.neomud.client.network.ReconnectStatus
import com.neomud.client.ui.theme.MudColors
import com.neomud.shared.model.*
import com.neomud.shared.protocol.ClientMessage
import com.neomud.shared.protocol.ServerMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class GameViewModel(
    private val wsClient: GameConnection,
    var serverBaseUrl: String = "",
    private val audioManager: PlatformAudioManager? = null
) : ViewModel() {

    /** Forwarded from the underlying connection so the game screen can render
     *  the "World is reloading…" overlay during Maker-driven restarts. */
    val reconnectStatus: StateFlow<ReconnectStatus> = wsClient.reconnectStatus

    /** Manual Retry button target after all auto-reconnect attempts fail. */
    fun retryReconnect() { wsClient.retryReconnect() }

    private val _roomInfo = MutableStateFlow<ServerMessage.RoomInfo?>(null)
    val roomInfo: StateFlow<ServerMessage.RoomInfo?> = _roomInfo

    private val _mapData = MutableStateFlow<ServerMessage.MapData?>(null)
    val mapData: StateFlow<ServerMessage.MapData?> = _mapData

    private val _gameLog = MutableStateFlow<List<LogEntry>>(emptyList())
    val gameLog: StateFlow<List<LogEntry>> = _gameLog

    private val _player = MutableStateFlow<Player?>(null)
    val player: StateFlow<Player?> = _player

    private val _roomEntities = MutableStateFlow<List<Npc>>(emptyList())
    val roomEntities: StateFlow<List<Npc>> = _roomEntities

    private val _roomPlayers = MutableStateFlow<List<PlayerInfo>>(emptyList())
    val roomPlayers: StateFlow<List<PlayerInfo>> = _roomPlayers

    private val _attackMode = MutableStateFlow(false)
    val attackMode: StateFlow<Boolean> = _attackMode

    private val _selectedTargetId = MutableStateFlow<String?>(null)
    val selectedTargetId: StateFlow<String?> = _selectedTargetId

    // Inventory
    private val _inventory = MutableStateFlow<List<InventoryItem>>(emptyList())
    val inventory: StateFlow<List<InventoryItem>> = _inventory

    private val _equipment = MutableStateFlow<Map<String, String>>(emptyMap())
    val equipment: StateFlow<Map<String, String>> = _equipment

    private val _itemCatalog = MutableStateFlow<Map<String, Item>>(emptyMap())
    val itemCatalog: StateFlow<Map<String, Item>> = _itemCatalog

    private val _showInventory = MutableStateFlow(false)
    val showInventory: StateFlow<Boolean> = _showInventory

    private val _showEquipment = MutableStateFlow(false)
    val showEquipment: StateFlow<Boolean> = _showEquipment

    // Coins & ground items
    private val _playerCoins = MutableStateFlow(Coins())
    val playerCoins: StateFlow<Coins> = _playerCoins

    private val _roomGroundItems = MutableStateFlow<List<GroundItem>>(emptyList())
    val roomGroundItems: StateFlow<List<GroundItem>> = _roomGroundItems

    private val _roomGroundCoins = MutableStateFlow(Coins())
    val roomGroundCoins: StateFlow<Coins> = _roomGroundCoins

    // Active effects
    private val _activeEffects = MutableStateFlow<List<ActiveEffect>>(emptyList())
    val activeEffects: StateFlow<List<ActiveEffect>> = _activeEffects

    // Stealth
    private val _isHidden = MutableStateFlow(false)
    val isHidden: StateFlow<Boolean> = _isHidden

    // Meditation
    private val _isMeditating = MutableStateFlow(false)
    val isMeditating: StateFlow<Boolean> = _isMeditating

    // Rest
    private val _isResting = MutableStateFlow(false)
    val isResting: StateFlow<Boolean> = _isResting

    // Skill catalog
    private val _skillCatalog = MutableStateFlow<Map<String, SkillDef>>(emptyMap())
    val skillCatalog: StateFlow<Map<String, SkillDef>> = _skillCatalog

    // Character sheet
    private val _showCharacterSheet = MutableStateFlow(false)
    val showCharacterSheet: StateFlow<Boolean> = _showCharacterSheet

    // Trainer
    private val _trainerInfo = MutableStateFlow<ServerMessage.TrainerInfo?>(null)
    val trainerInfo: StateFlow<ServerMessage.TrainerInfo?> = _trainerInfo

    private val _showTrainer = MutableStateFlow(false)
    val showTrainer: StateFlow<Boolean> = _showTrainer

    // Spells
    private val _spellCatalog = MutableStateFlow<Map<String, SpellDef>>(emptyMap())
    val spellCatalog: StateFlow<Map<String, SpellDef>> = _spellCatalog

    private val _spellSlots = MutableStateFlow<List<String?>>(listOf(null, null, null, null))
    val spellSlots: StateFlow<List<String?>> = _spellSlots

    private val _readiedSpellId = MutableStateFlow<String?>(null)
    val readiedSpellId: StateFlow<String?> = _readiedSpellId

    private val _showSpellPicker = MutableStateFlow(false)
    val showSpellPicker: StateFlow<Boolean> = _showSpellPicker

    private val _editingSlotIndex = MutableStateFlow<Int?>(null)
    val editingSlotIndex: StateFlow<Int?> = _editingSlotIndex

    // Vendor
    private val _vendorInfo = MutableStateFlow<ServerMessage.VendorInfo?>(null)
    val vendorInfo: StateFlow<ServerMessage.VendorInfo?> = _vendorInfo

    private val _showVendor = MutableStateFlow(false)
    val showVendor: StateFlow<Boolean> = _showVendor

    // Crafting
    private val _crafterInfo = MutableStateFlow<ServerMessage.CraftingMenu?>(null)
    val crafterInfo: StateFlow<ServerMessage.CraftingMenu?> = _crafterInfo

    private val _showCrafting = MutableStateFlow(false)
    val showCrafting: StateFlow<Boolean> = _showCrafting

    // Visited rooms (fog-of-war)
    private val _visitedRooms = MutableStateFlow<Set<RoomId>>(emptySet())
    val visitedRooms: StateFlow<Set<RoomId>> = _visitedRooms

    // Map overlay
    private val _showMap = MutableStateFlow(false)
    val showMap: StateFlow<Boolean> = _showMap

    // Tracked direction (from TRACK skill)
    private val _trackedDirection = MutableStateFlow<Direction?>(null)
    val trackedDirection: StateFlow<Direction?> = _trackedDirection

    // Lock target picker
    private val _showLockTargetPicker = MutableStateFlow(false)
    val showLockTargetPicker: StateFlow<Boolean> = _showLockTargetPicker

    // Kick direction picker
    private val _showKickDirectionPicker = MutableStateFlow(false)
    val showKickDirectionPicker: StateFlow<Boolean> = _showKickDirectionPicker

    private val _kickPendingNpcId = MutableStateFlow<String?>(null)
    val kickPendingNpcId: StateFlow<String?> = _kickPendingNpcId

    // Death notification
    private val _deathMessage = MutableStateFlow<String?>(null)
    val deathMessage: StateFlow<String?> = _deathMessage

    fun dismissDeath() {
        _deathMessage.value = null
    }

    // Settings
    private val _showSettings = MutableStateFlow(false)
    val showSettings: StateFlow<Boolean> = _showSettings

    // Help
    private val _showHelp = MutableStateFlow(false)
    val showHelp: StateFlow<Boolean> = _showHelp

    // Tutorial modal (blocking). The currently-shown tutorial is the head of [tutorialQueue];
    // additional incoming blocking Tutorials append to the tail and are shown sequentially as
    // each is dismissed. Required so that multi-step intros (#272: per-world intro then welcome)
    // don't lose the first tutorial.
    private val tutorialQueue: ArrayDeque<ServerMessage.Tutorial> = ArrayDeque()
    private val _tutorialMessage = MutableStateFlow<ServerMessage.Tutorial?>(null)
    val tutorialMessage: StateFlow<ServerMessage.Tutorial?> = _tutorialMessage

    // Coach mark (non-blocking)
    private val _coachMark = MutableStateFlow<ServerMessage.Tutorial?>(null)
    val coachMark: StateFlow<ServerMessage.Tutorial?> = _coachMark

    // Coach mark target element positions
    val coachMarkTargets: MutableMap<String, androidx.compose.ui.layout.LayoutCoordinates> = mutableMapOf()

    fun registerCoachMarkTarget(key: String, coordinates: androidx.compose.ui.layout.LayoutCoordinates) {
        coachMarkTargets[key] = coordinates
    }

    fun unregisterCoachMarkTarget(key: String) {
        coachMarkTargets.remove(key)
    }

    private val _classCatalog = MutableStateFlow<Map<String, CharacterClassDef>>(emptyMap())
    val classCatalog: StateFlow<Map<String, CharacterClassDef>> = _classCatalog

    private fun sfx(soundId: String, category: String) {
        if (soundId.isNotBlank()) audioManager?.playSfx(serverBaseUrl, soundId, category)
    }

    /** Play NPC interaction SFX (greet/farewell) — stops previous to prevent overlap. */
    private fun npcSfx(soundId: String, category: String) {
        if (soundId.isNotBlank()) audioManager?.playNpcSfx(serverBaseUrl, soundId, category)
    }

    private fun bgm(trackId: String) {
        audioManager?.playBgm(serverBaseUrl, trackId)
    }

    private fun findSpellByName(name: String): SpellDef? =
        _spellCatalog.value.values.find { it.name == name }

    private fun findItemByName(name: String): Item? =
        _itemCatalog.value.values.find { it.name == name }

    fun setInitialPlayer(player: Player) {
        _player.value = player
    }

    fun setInitialRoomInfo(roomInfo: ServerMessage.RoomInfo) {
        _roomInfo.value = roomInfo
        _roomEntities.value = roomInfo.npcs
        _roomPlayers.value = roomInfo.players
        bgm(roomInfo.room.bgm)
    }

    fun setInitialMapData(mapData: ServerMessage.MapData) {
        _mapData.value = mapData
        if (mapData.visitedRooms.isNotEmpty()) {
            _visitedRooms.value = _visitedRooms.value + mapData.visitedRooms
        }
        _visitedRooms.value = _visitedRooms.value + mapData.playerRoomId
    }

    fun setInitialTutorial(tutorial: ServerMessage.Tutorial) {
        if (tutorial.blocking) {
            enqueueBlockingTutorial(tutorial)
        } else {
            _coachMark.value = tutorial
        }
    }

    private fun enqueueBlockingTutorial(tutorial: ServerMessage.Tutorial) {
        if (_tutorialMessage.value == null) {
            _tutorialMessage.value = tutorial
        } else {
            tutorialQueue.addLast(tutorial)
        }
    }

    fun setInitialCatalogs(
        classes: List<CharacterClassDef> = emptyList(),
        items: List<Item> = emptyList(),
        spells: List<SpellDef> = emptyList(),
        skills: List<SkillDef> = emptyList()
    ) {
        if (classes.isNotEmpty()) _classCatalog.value = classes.associateBy { it.id }
        if (items.isNotEmpty()) _itemCatalog.value = items.associateBy { it.id }
        if (spells.isNotEmpty()) _spellCatalog.value = spells.associateBy { it.id }
        if (skills.isNotEmpty()) _skillCatalog.value = skills.associateBy { it.id }
        autoAssignSpells()
    }

    fun startCollecting() {
        viewModelScope.launch {
            wsClient.messages.collect { message ->
                try {
                    handleMessage(message)
                } catch (e: Exception) {
                    PlatformLogger.e("GameViewModel", "handleMessage crashed for ${message::class.simpleName}", e)
                }
            }
        }
        look()
    }

    private fun handleMessage(message: ServerMessage) {
        when (message) {
            is ServerMessage.RoomInfo -> {
                _roomInfo.value = message
                _roomEntities.value = message.npcs
                _roomPlayers.value = message.players
                _visitedRooms.value = _visitedRooms.value + message.room.id
                logRoomInfo(message.room, message.players, message.npcs)
                bgm(message.room.bgm)
            }
            is ServerMessage.MoveOk -> {
                // Play departure sound from the room we're leaving
                val previousRoom = _roomInfo.value?.room
                if (previousRoom != null) sfx(previousRoom.departSound, "rooms")
                _roomInfo.value = ServerMessage.RoomInfo(message.room, message.players, message.npcs)
                _roomEntities.value = message.npcs
                _roomPlayers.value = message.players
                _visitedRooms.value = _visitedRooms.value + message.room.id
                // Clear ground items on move (will be refreshed by RoomItemsUpdate)
                _roomGroundItems.value = emptyList()
                _roomGroundCoins.value = Coins()
                _trackedDirection.value = null
                _readiedSpellId.value = null
                _selectedTargetId.value = null
                addLog("You move ${message.direction.name.lowercase()}.", MudColors.selfAction)
                logRoomInfo(message.room, message.players, message.npcs)
                bgm(message.room.bgm)
            }
            is ServerMessage.MoveError -> addLog("Cannot move: ${message.reason}", MudColors.error)
            is ServerMessage.MapData -> {
                _mapData.value = message
                if (message.visitedRooms.isNotEmpty()) {
                    _visitedRooms.value = _visitedRooms.value + message.visitedRooms
                }
                _visitedRooms.value = _visitedRooms.value + message.playerRoomId
            }
            is ServerMessage.PlayerEntered -> {
                addLog("${message.playerName} has arrived.", MudColors.playerEvent)
                val info = message.playerInfo
                if (info != null) {
                    _roomPlayers.value = _roomPlayers.value + info
                }
            }
            is ServerMessage.PlayerLeft -> {
                addLog("${message.playerName} left ${message.direction.name.lowercase()}.", MudColors.playerEvent)
                _roomPlayers.value = _roomPlayers.value.filter { it.name != message.playerName }
            }
            is ServerMessage.NpcEntered -> {
                val color = if (message.hostile) MudColors.hostile else MudColors.friendly
                val text = if (message.spawned) {
                    "${message.npcName} emerges from the shadows."
                } else {
                    "${message.npcName} has arrived."
                }
                addLog(text, color)
                if (message.npcId.isNotEmpty()) {
                    val current = _roomEntities.value.toMutableList()
                    current.add(0, Npc(
                        id = message.npcId,
                        name = message.npcName,
                        description = "",
                        currentRoomId = message.roomId,
                        behaviorType = "unknown",
                        hostile = message.hostile,
                        currentHp = message.currentHp,
                        maxHp = message.maxHp,
                        attackSound = message.attackSound,
                        missSound = message.missSound,
                        deathSound = message.deathSound,
                        interactSound = message.interactSound,
                        exitSound = message.exitSound
                    ))
                    _roomEntities.value = current
                }
            }
            is ServerMessage.NpcLeft -> {
                val npc = _roomEntities.value.find { it.id == message.npcId }
                val color = if (npc?.hostile == true) MudColors.hostile else MudColors.friendly
                addLog("${message.npcName} left ${message.direction.name.lowercase()}.", color)
                if (message.npcId.isNotEmpty()) {
                    _roomEntities.value = _roomEntities.value.filter { it.id != message.npcId }
                }
            }
            is ServerMessage.CombatHit -> {
                when {
                    message.isMiss && message.isBackstab -> {
                        addLog("${message.attackerName} strikes from the shadows but misses! (still hidden)", MudColors.stealth)
                        sfx("miss", "general")
                    }
                    message.isDodge && message.isBackstab -> {
                        addLog("${message.defenderName} dodges ${message.attackerName}'s backstab! (still hidden)", MudColors.stealth)
                        sfx("dodge", "general")
                    }
                    message.isMiss -> {
                        addLog("${message.attackerName} misses ${message.defenderName}!", MudColors.combatYou)
                        if (!message.isPlayerDefender) {
                            // Player missed NPC — play weapon miss sound
                            val weaponId = _equipment.value["weapon"]
                            val item = weaponId?.let { _itemCatalog.value[it] }
                            val missSound = item?.missSound ?: ""
                            if (missSound.isNotEmpty()) sfx(missSound, "items") else sfx("miss", "general")
                        } else {
                            // NPC missed player — play NPC miss sound
                            val npc = _roomEntities.value.find { it.name == message.attackerName }
                            val npcMissSound = npc?.missSound ?: ""
                            if (npcMissSound.isNotEmpty()) sfx(npcMissSound, "npcs") else sfx("miss", "general")
                        }
                    }
                    message.isDodge -> {
                        addLog("${message.defenderName} dodges ${message.attackerName}'s attack!", MudColors.combatYou)
                        sfx("dodge", "general")
                    }
                    message.isParry && message.damage > 0 -> {
                        addLog("${message.defenderName} parries ${message.attackerName}'s blow for reduced damage! (${message.damage} damage, ${message.defenderHp}/${message.defenderMaxHp} HP)", MudColors.combatYou)
                        sfx("parry", "general")
                    }
                    message.damage > 0 -> {
                        val verb = if (message.isBackstab) "backstabs" else "hits"
                        val color = if (message.isPlayerDefender) MudColors.combatEnemy
                            else if (message.isBackstab) MudColors.stealth else MudColors.combatYou
                        addLog("${message.attackerName} $verb ${message.defenderName} for ${message.damage} damage! (${message.defenderHp}/${message.defenderMaxHp} HP)", color)
                        if (message.isBackstab) {
                            sfx("backstab", "general")
                        } else if (!message.isPlayerDefender) {
                            val weaponId = _equipment.value["weapon"]
                            val item = weaponId?.let { _itemCatalog.value[it] }
                            val weaponSound = item?.attackSound ?: ""
                            if (weaponSound.isNotEmpty()) sfx(weaponSound, "items") else sfx("sword_swing", "items")
                        } else {
                            val npc = _roomEntities.value.find { it.name == message.attackerName }
                            val npcAttackSound = npc?.attackSound ?: ""
                            if (npcAttackSound.isNotEmpty()) sfx(npcAttackSound, "npcs") else sfx("miss", "general")
                        }
                    }
                }
                if (message.isPlayerDefender) {
                    _player.value = _player.value?.copy(currentHp = message.defenderHp)
                } else {
                    _roomEntities.value = _roomEntities.value.map { npc ->
                        if (npc.id == message.defenderId) {
                            npc.copy(currentHp = message.defenderHp)
                        } else npc
                    }
                }
            }
            is ServerMessage.NpcDied -> {
                val dyingNpc = _roomEntities.value.find { it.id == message.npcId }
                sfx(dyingNpc?.deathSound?.ifEmpty { null } ?: "enemy_death", "npcs")
                addLog("${message.npcName} has been slain by ${message.killerName}!", MudColors.kill)
                _roomEntities.value = _roomEntities.value.filter { it.id != message.npcId }
                if (_selectedTargetId.value == message.npcId) {
                    _selectedTargetId.value = null
                }
            }
            is ServerMessage.PlayerDied -> {
                addLog("You have been slain by ${message.killerName}! Respawning...", MudColors.death)
                _deathMessage.value = "Slain by ${message.killerName}"
                _player.value = _player.value?.copy(
                    currentHp = message.respawnHp,
                    currentMp = message.respawnMp,
                    currentRoomId = message.respawnRoomId
                )
                _attackMode.value = false
                _selectedTargetId.value = null
                _readiedSpellId.value = null
                _activeEffects.value = emptyList()
                _isHidden.value = false
                _isMeditating.value = false
                _isResting.value = false
                _roomGroundItems.value = emptyList()
                _roomGroundCoins.value = Coins()
            }
            is ServerMessage.AttackModeUpdate -> {
                _attackMode.value = message.enabled
                if (!message.enabled) {
                    _readiedSpellId.value = null
                    addLog("Attack mode disabled.", MudColors.system)
                } else {
                    addLog("Attack mode enabled!", MudColors.combatYou)
                }
            }
            is ServerMessage.PlayerSays -> {
                addEntry(LogEntry(listOf(
                    LogSpan("${message.playerName}", MudColors.playerName),
                    LogSpan(" says: \"${message.message}\"", MudColors.say)
                )))
            }
            is ServerMessage.ActiveEffectsUpdate -> {
                _activeEffects.value = message.effects
            }
            is ServerMessage.EffectTick -> {
                sfx(message.sound, "spells")
                addLog(message.message, MudColors.effect)
                _player.value = _player.value?.let { p ->
                    val newMp = if (message.newMp >= 0) message.newMp else p.currentMp
                    p.copy(currentHp = message.newHp, currentMp = newMp)
                }
            }
            is ServerMessage.SystemMessage -> addLog("[System] ${message.message}", MudColors.system)
            is ServerMessage.Tutorial -> {
                if (message.blocking) {
                    enqueueBlockingTutorial(message)
                } else {
                    _coachMark.value = message
                }
            }
            is ServerMessage.Error -> addLog("[Error] ${message.message}", MudColors.error)
            is ServerMessage.LoginOk -> _player.value = message.player
            is ServerMessage.Pong -> { /* ignore */ }
            is ServerMessage.RegisterOk -> { /* handled by AuthViewModel */ }
            is ServerMessage.AuthError -> { /* handled by AuthViewModel */ }
            is ServerMessage.NameCheckResult -> { /* handled by AuthViewModel */ }
            is ServerMessage.ServerHello -> { /* handled by AuthViewModel */ }
            is ServerMessage.ConnectionRejected -> { /* handled by AuthViewModel */ }
            is ServerMessage.ClassCatalogSync -> {
                _classCatalog.value = message.classes.associateBy { it.id }
            }
            is ServerMessage.ItemCatalogSync -> {
                _itemCatalog.value = message.items.associateBy { it.id }
            }
            is ServerMessage.InventoryUpdate -> {
                _inventory.value = message.inventory
                _equipment.value = message.equipment
                _playerCoins.value = message.coins
            }
            is ServerMessage.LootReceived -> {
                for (lootedItem in message.items) {
                    val qtyStr = if (lootedItem.quantity > 1) " x${lootedItem.quantity}" else ""
                    addLog("You loot ${lootedItem.itemName}$qtyStr from ${message.npcName}.", MudColors.loot)
                }
            }
            is ServerMessage.ItemUsed -> {
                val usedItem = findItemByName(message.itemName)
                if (usedItem?.useSound?.isNotEmpty() == true) sfx(usedItem.useSound, "items") else sfx("potion_drink", "items")
                addLog(message.message, MudColors.effect)
                _player.value = _player.value?.copy(
                    currentHp = message.newHp,
                    currentMp = message.newMp
                )
            }
            is ServerMessage.EquipUpdate -> {
                if (message.itemName != null) {
                    addLog("You equip ${message.itemName}.", MudColors.selfAction)
                } else {
                    addLog("You unequip from ${message.slot}.", MudColors.selfAction)
                }
            }
            is ServerMessage.RoomItemsUpdate -> {
                _roomGroundItems.value = message.items
                _roomGroundCoins.value = message.coins
            }
            is ServerMessage.LootDropped -> {
                sfx("loot_drop", "general")
                val catalog = _itemCatalog.value
                for (item in message.items) {
                    val name = catalog[item.itemId]?.name ?: item.itemName
                    val qtyStr = if (item.quantity > 1) " x${item.quantity}" else ""
                    addLog("${message.npcName} dropped $name$qtyStr.", MudColors.loot)
                }
                if (!message.coins.isEmpty()) {
                    val parts = mutableListOf<String>()
                    if (message.coins.platinum > 0) parts.add("${message.coins.platinum} platinum")
                    if (message.coins.gold > 0) parts.add("${message.coins.gold} gold")
                    if (message.coins.silver > 0) parts.add("${message.coins.silver} silver")
                    if (message.coins.copper > 0) parts.add("${message.coins.copper} copper")
                    addLog("${message.npcName} dropped ${parts.joinToString(", ")}.", MudColors.loot)
                }
            }
            is ServerMessage.PickupResult -> {
                sfx(if (message.isCoin) "coin_pickup" else "item_pickup", "general")
                if (message.isCoin) {
                    addLog("You pick up ${message.quantity} ${message.itemName}.", MudColors.loot)
                } else {
                    val qtyStr = if (message.quantity > 1) " x${message.quantity}" else ""
                    addLog("You pick up ${message.itemName}$qtyStr.", MudColors.loot)
                }
            }
            is ServerMessage.StealthUpdate -> {
                _isHidden.value = message.hidden
                if (message.message.isNotEmpty()) {
                    addLog(message.message, MudColors.stealth)
                }
                if (message.hidden) {
                    _attackMode.value = false
                }
            }
            is ServerMessage.MeditateUpdate -> {
                _isMeditating.value = message.meditating
                if (message.message.isNotEmpty()) {
                    addLog(message.message, MudColors.spell)
                }
            }
            is ServerMessage.RestUpdate -> {
                _isResting.value = message.resting
                if (message.message.isNotEmpty()) {
                    addLog(message.message, MudColors.spell)
                }
            }
            is ServerMessage.TrackResult -> {
                if (message.success && message.direction != null) {
                    _trackedDirection.value = message.direction
                }
                addLog(message.message, MudColors.system)
            }
            is ServerMessage.SkillCatalogSync -> {
                _skillCatalog.value = message.skills.associateBy { it.id }
            }
            is ServerMessage.RaceCatalogSync -> { /* handled by AuthViewModel */ }
            is ServerMessage.SpellCatalogSync -> {
                _spellCatalog.value = message.spells.associateBy { it.id }
                autoAssignSpells()
            }
            is ServerMessage.SpellCastResult -> {
                val color = if (message.success) MudColors.spell else MudColors.error
                addLog(message.message, color)
                if (message.success) {
                    val spell = findSpellByName(message.spellName)
                    sfx(spell?.castSound ?: "", "spells")
                    _player.value = _player.value?.let { p ->
                        val newHp = message.newHp ?: p.currentHp
                        p.copy(currentMp = message.newMp, currentHp = newHp)
                    }
                }
                // Don't clear readiedSpellId — spell persists for auto-cast loop
            }
            is ServerMessage.SpellEffect -> {
                val impactSpell = findSpellByName(message.spellName)
                sfx(impactSpell?.impactSound ?: "", "spells")
                if (!message.isPlayerTarget) {
                    _roomEntities.value = _roomEntities.value.map { npc ->
                        if (message.targetId.isNotEmpty() && npc.id == message.targetId ||
                            message.targetId.isEmpty() && npc.name == message.targetName) {
                            npc.copy(currentHp = message.targetNewHp)
                        } else npc
                    }
                }
            }
            is ServerMessage.SkillEffect -> {
                addLog(message.message, MudColors.combatYou)
                // Update NPC HP
                _roomEntities.value = _roomEntities.value.map { npc ->
                    if (npc.id == message.targetId) {
                        npc.copy(currentHp = message.targetHp)
                    } else npc
                }
            }
            is ServerMessage.VendorInfo -> {
                if (!_showVendor.value) npcSfx(message.interactSound, "npcs")
                _vendorInfo.value = message
                _showVendor.value = true
            }
            is ServerMessage.BuyResult -> {
                if (message.success) {
                    addLog(message.message, MudColors.loot)
                } else {
                    addLog(message.message, MudColors.error)
                }
                _playerCoins.value = message.updatedCoins
                _inventory.value = message.updatedInventory
                _equipment.value = message.equipment
                // Refresh vendor info if panel is open
                if (_showVendor.value) interactVendor()
            }
            is ServerMessage.SellResult -> {
                if (message.success) {
                    addLog(message.message, MudColors.loot)
                } else {
                    addLog(message.message, MudColors.error)
                }
                _playerCoins.value = message.updatedCoins
                _inventory.value = message.updatedInventory
                _equipment.value = message.equipment
                if (_showVendor.value) interactVendor()
            }
            is ServerMessage.XpGained -> {
                val logText = if (message.amount >= 0) {
                    "+${message.amount} XP (${message.currentXp}/${message.xpToNextLevel})"
                } else {
                    "Lost ${-message.amount} XP (${message.currentXp}/${message.xpToNextLevel})"
                }
                addLog(logText, MudColors.xp)
                _player.value = _player.value?.copy(
                    currentXp = message.currentXp,
                    xpToNextLevel = message.xpToNextLevel
                )
            }
            is ServerMessage.LevelUp -> {
                addLog("LEVEL UP! You are now level ${message.newLevel}! HP+${message.hpRoll} MP+${message.mpRoll} CP+${message.cpGained}", MudColors.levelUp)
                _player.value = _player.value?.copy(
                    level = message.newLevel,
                    maxHp = message.newMaxHp,
                    currentHp = message.newMaxHp,
                    maxMp = message.newMaxMp,
                    currentMp = message.newMaxMp,
                    unspentCp = message.totalUnspentCp,
                    xpToNextLevel = message.xpToNextLevel
                )
                // Refresh trainer info if trainer panel is open
                if (_showTrainer.value) interactTrainer()
            }
            is ServerMessage.TrainerInfo -> {
                if (!_showTrainer.value) sfx(message.interactSound, "npcs")
                _trainerInfo.value = message
                _showTrainer.value = true
                _player.value = _player.value?.copy(
                    stats = message.currentStats,
                    unspentCp = message.unspentCp
                )
            }
            is ServerMessage.InteractResult -> {
                val color = if (message.success) MudColors.selfAction else MudColors.error
                addLog(message.message, color)
                sfx(message.sound, "rooms")
            }
            is ServerMessage.StatTrained -> {
                addLog("Trained ${message.stat} to ${message.newValue} (${message.cpSpent} CP spent, ${message.remainingCp} remaining)", MudColors.system)
                _player.value = _player.value?.let { p ->
                    val newStats = when (message.stat.lowercase()) {
                        "strength" -> p.stats.copy(strength = message.newValue)
                        "agility" -> p.stats.copy(agility = message.newValue)
                        "intellect" -> p.stats.copy(intellect = message.newValue)
                        "willpower" -> p.stats.copy(willpower = message.newValue)
                        "health" -> p.stats.copy(health = message.newValue)
                        "charm" -> p.stats.copy(charm = message.newValue)
                        else -> p.stats
                    }
                    p.copy(
                        stats = newStats,
                        unspentCp = message.remainingCp,
                        currentHp = if (message.maxHp > 0) message.currentHp else p.currentHp,
                        maxHp = if (message.maxHp > 0) message.maxHp else p.maxHp,
                        currentMp = if (message.maxMp > 0) message.currentMp else p.currentMp,
                        maxMp = if (message.maxMp > 0) message.maxMp else p.maxMp
                    )
                }
                // Refresh trainer info after training
                interactTrainer()
            }
            is ServerMessage.ServerShutdown -> {
                addLog(message.message, MudColors.error)
            }
            is ServerMessage.CraftingMenu -> {
                if (!_showCrafting.value) npcSfx(message.interactSound, "npcs")
                _crafterInfo.value = message
                _showCrafting.value = true
            }
            is ServerMessage.CraftResult -> {
                val color = if (message.success) MudColors.loot else MudColors.error
                if (message.success) sfx("craft_success", "general")
                addLog(message.message, color)
                _playerCoins.value = message.updatedCoins
                _inventory.value = message.updatedInventory
                _equipment.value = message.equipment
                // Refresh crafting menu if panel is open
                if (_showCrafting.value) interactCrafter()
            }
            // Handled by AuthViewModel, not GameViewModel
            is ServerMessage.PlatformAuthOk -> {}
        }
    }

    private fun logRoomInfo(room: Room, players: List<PlayerInfo>, npcs: List<Npc>) {
        addLog("--- ${room.name} ---", MudColors.roomName)
        addLog(room.description, MudColors.roomDesc)
        if (players.isNotEmpty()) {
            val spans = mutableListOf(LogSpan("Players here: ", MudColors.default))
            players.forEachIndexed { i, info ->
                if (i > 0) spans.add(LogSpan(", ", MudColors.default))
                spans.add(LogSpan(info.name, MudColors.playerName))
            }
            addEntry(LogEntry(spans))
        }
        if (npcs.isNotEmpty()) {
            val spans = mutableListOf(LogSpan("You see: ", MudColors.default))
            npcs.forEachIndexed { i, npc ->
                if (i > 0) spans.add(LogSpan(", ", MudColors.default))
                spans.add(LogSpan(npc.name, if (npc.hostile) MudColors.hostile else MudColors.friendly))
            }
            addEntry(LogEntry(spans))
        }
        val exits = room.exits.keys.joinToString(", ") { it.name.lowercase() }
        addLog("Exits: $exits", MudColors.exits)
    }

    private fun addLog(text: String, color: androidx.compose.ui.graphics.Color) {
        addEntry(LogEntry(text, color))
    }

    private fun addEntry(entry: LogEntry) {
        val log = _gameLog.value + entry
        _gameLog.value = if (log.size > 200) log.takeLast(200) else log
    }

    fun move(direction: Direction) {
        viewModelScope.launch {
            wsClient.send(ClientMessage.Move(direction))
        }
    }

    fun look() {
        viewModelScope.launch {
            wsClient.send(ClientMessage.Look)
        }
    }

    fun say(message: String) {
        viewModelScope.launch {
            wsClient.send(ClientMessage.Say(message))
        }
    }

    fun toggleAttackMode(enabled: Boolean) {
        if (!enabled) {
            _readiedSpellId.value = null
            viewModelScope.launch {
                wsClient.send(ClientMessage.ReadySpell(null))
                wsClient.send(ClientMessage.AttackToggle(false))
            }
        } else {
            viewModelScope.launch {
                wsClient.send(ClientMessage.AttackToggle(true))
            }
        }
    }

    fun selectTarget(npcId: String?) {
        _selectedTargetId.value = npcId
        viewModelScope.launch {
            wsClient.send(ClientMessage.SelectTarget(npcId))
        }
    }

    fun attackTarget(npcId: String) {
        _readiedSpellId.value = null
        selectTarget(npcId)
        viewModelScope.launch { wsClient.send(ClientMessage.ReadySpell(null)) }
        toggleAttackMode(true)
    }

    fun toggleInventory() {
        _showInventory.value = !_showInventory.value
        if (_showInventory.value) {
            _showEquipment.value = false
            viewInventory()
        }
    }

    fun toggleEquipment() {
        _showEquipment.value = !_showEquipment.value
        if (_showEquipment.value) {
            _showInventory.value = false
            viewInventory()
        }
    }

    fun viewInventory() {
        viewModelScope.launch {
            wsClient.send(ClientMessage.ViewInventory)
        }
    }

    fun equipItem(itemId: String, slot: String) {
        viewModelScope.launch {
            wsClient.send(ClientMessage.EquipItem(itemId, slot))
        }
    }

    fun unequipItem(slot: String) {
        viewModelScope.launch {
            wsClient.send(ClientMessage.UnequipItem(slot))
        }
    }

    fun useItem(itemId: String) {
        viewModelScope.launch {
            wsClient.send(ClientMessage.UseItem(itemId))
        }
    }

    fun pickupItem(itemId: String, quantity: Int) {
        viewModelScope.launch {
            wsClient.send(ClientMessage.PickupItem(itemId, quantity))
        }
    }

    fun pickupCoins(coinType: String) {
        viewModelScope.launch {
            wsClient.send(ClientMessage.PickupCoins(coinType))
        }
    }

    fun toggleSneakMode(enabled: Boolean) {
        viewModelScope.launch {
            wsClient.send(ClientMessage.SneakToggle(enabled))
        }
    }

    fun useSkill(skillId: String, targetId: String? = null) {
        viewModelScope.launch {
            wsClient.send(ClientMessage.UseSkill(skillId, targetId))
        }
    }

    fun toggleCharacterSheet() {
        _showCharacterSheet.value = !_showCharacterSheet.value
    }

    fun toggleMap() {
        _showMap.value = !_showMap.value
        if (_showMap.value) {
            _showInventory.value = false
            _showEquipment.value = false
        }
    }

    fun toggleSettings() {
        _showSettings.value = !_showSettings.value
    }

    fun toggleHelp() {
        _showHelp.value = !_showHelp.value
    }

    fun dismissTutorial() {
        _tutorialMessage.value = if (tutorialQueue.isNotEmpty()) tutorialQueue.removeFirst() else null
    }

    fun dismissCoachMark() {
        _coachMark.value = null
    }

    fun interactTrainer() {
        viewModelScope.launch {
            wsClient.send(ClientMessage.InteractTrainer)
        }
    }

    fun trainLevelUp() {
        viewModelScope.launch {
            wsClient.send(ClientMessage.TrainLevelUp)
        }
    }

    fun trainStat(stat: String, points: Int = 1) {
        viewModelScope.launch {
            wsClient.send(ClientMessage.TrainStat(stat, points))
        }
    }

    fun allocateTrainedStats(stats: com.neomud.shared.model.Stats) {
        viewModelScope.launch {
            wsClient.send(ClientMessage.AllocateTrainedStats(stats))
        }
    }

    fun dismissTrainer() {
        _trainerInfo.value?.exitSound?.let { if (it.isNotBlank()) sfx(it, "npcs") }
        _showTrainer.value = false
        _trainerInfo.value = null
    }

    fun interactVendor() {
        viewModelScope.launch {
            wsClient.send(ClientMessage.InteractVendor)
        }
    }

    fun buyItem(itemId: String, quantity: Int = 1) {
        viewModelScope.launch {
            wsClient.send(ClientMessage.BuyItem(itemId, quantity))
        }
    }

    fun sellItem(itemId: String, quantity: Int = 1) {
        viewModelScope.launch {
            wsClient.send(ClientMessage.SellItem(itemId, quantity))
        }
    }

    fun dismissVendor() {
        _vendorInfo.value?.exitSound?.let { if (it.isNotBlank()) npcSfx(it, "npcs") }
        _showVendor.value = false
        _vendorInfo.value = null
    }

    fun interactCrafter() {
        viewModelScope.launch {
            wsClient.send(ClientMessage.InteractCrafter)
        }
    }

    fun craftItem(recipeId: String) {
        viewModelScope.launch {
            wsClient.send(ClientMessage.CraftItem(recipeId))
        }
    }

    fun dismissCrafter() {
        _crafterInfo.value?.exitSound?.let { if (it.isNotBlank()) npcSfx(it, "npcs") }
        _showCrafting.value = false
        _crafterInfo.value = null
    }

    fun interactFeature(featureId: String) {
        viewModelScope.launch {
            wsClient.send(ClientMessage.InteractFeature(featureId))
        }
    }

    // Lock target picker methods
    fun showLockTargetPicker() {
        val room = _roomInfo.value?.room
        val lockedExits = room?.lockedExits ?: emptyMap()
        val unpickable = room?.unpickableExits ?: emptySet()
        val pickableExits = lockedExits.filterKeys { it !in unpickable }
        val lockedInteractables = (room?.interactables ?: emptyList())
            .filter { it.difficulty > 0 && it.difficultyCheck.isNotEmpty() }
        val totalTargets = pickableExits.size + lockedInteractables.size

        if (totalTargets <= 1) {
            // Let server discover all locks and resend RoomInfo — client may not see them all yet
            useSkill("PICK_LOCK")
        } else {
            _showLockTargetPicker.value = true
        }
    }

    fun dismissLockTargetPicker() {
        _showLockTargetPicker.value = false
    }

    fun pickLockTarget(targetId: String) {
        _showLockTargetPicker.value = false
        useSkill("PICK_LOCK", targetId)
    }

    // Kick direction picker methods
    fun showKickDirectionPicker(npcId: String? = null) {
        val targetId = npcId ?: _selectedTargetId.value
            ?: _roomEntities.value.firstOrNull { it.hostile }?.id
        if (targetId == null) {
            // No target — let server handle the error
            useSkill("KICK")
            return
        }
        _kickPendingNpcId.value = targetId
        _showKickDirectionPicker.value = true
    }

    fun dismissKickDirectionPicker() {
        _showKickDirectionPicker.value = false
        _kickPendingNpcId.value = null
    }

    fun kickWithDirection(direction: Direction) {
        val npcId = _kickPendingNpcId.value ?: return
        _showKickDirectionPicker.value = false
        _kickPendingNpcId.value = null
        useSkill("KICK", "$npcId:${direction.name}")
    }

    // Spell methods
    fun castSpellOnTarget(spellId: String, npcId: String) {
        _readiedSpellId.value = spellId
        _selectedTargetId.value = npcId
        viewModelScope.launch {
            wsClient.send(ClientMessage.ReadySpell(spellId))
            wsClient.send(ClientMessage.SelectTarget(npcId))
            wsClient.send(ClientMessage.AttackToggle(true))
        }
    }

    fun castSpell(spellId: String, targetId: String? = null) {
        viewModelScope.launch {
            wsClient.send(ClientMessage.CastSpell(spellId, targetId))
        }
    }

    fun readySpell(slotIndex: Int) {
        val spellId = _spellSlots.value.getOrNull(slotIndex) ?: return
        val spell = _spellCatalog.value[spellId] ?: return

        if (spell.targetType == TargetType.SELF) {
            castSpell(spellId)
            return
        }

        // Toggle off if same spell
        if (_readiedSpellId.value == spellId) {
            _readiedSpellId.value = null
            viewModelScope.launch { wsClient.send(ClientMessage.ReadySpell(null)) }
            toggleAttackMode(false)
            return
        }

        // Auto-select first hostile, engage attack
        _readiedSpellId.value = spellId
        val target = _roomEntities.value.firstOrNull { it.hostile }
        if (target != null) _selectedTargetId.value = target.id
        viewModelScope.launch {
            wsClient.send(ClientMessage.ReadySpell(spellId))
            if (target != null) wsClient.send(ClientMessage.SelectTarget(target.id))
            wsClient.send(ClientMessage.AttackToggle(true))
        }
    }

    fun assignSpellToSlot(index: Int, spellId: String?) {
        val slots = _spellSlots.value.toMutableList()
        slots[index] = spellId
        _spellSlots.value = slots
        _showSpellPicker.value = false
        _editingSlotIndex.value = null
    }

    fun openSpellPicker(slotIndex: Int) {
        _editingSlotIndex.value = slotIndex
        _showSpellPicker.value = true
    }

    fun dismissSpellPicker() {
        _showSpellPicker.value = false
        _editingSlotIndex.value = null
    }

    private fun autoAssignSpells() {
        val player = _player.value ?: return
        val classDef = _classCatalog.value[player.characterClass] ?: return
        if (classDef.magicSchools.isEmpty()) return

        val catalog = _spellCatalog.value
        val classSchools = classDef.magicSchools.keys
        val classSpells = catalog.values
            .filter { it.school in classSchools && player.level >= it.levelRequired }
            .sortedBy { it.levelRequired }

        val slots = _spellSlots.value.toMutableList()
        var spellIdx = 0
        for (i in slots.indices) {
            if (slots[i] == null && spellIdx < classSpells.size) {
                slots[i] = classSpells[spellIdx].id
                spellIdx++
            }
        }
        _spellSlots.value = slots
    }
}
