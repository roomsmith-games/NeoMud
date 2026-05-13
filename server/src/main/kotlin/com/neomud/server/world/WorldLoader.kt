package com.neomud.server.world

import com.neomud.shared.NeoMudVersion
import com.neomud.shared.model.EquipmentSlots
import com.neomud.shared.model.Direction
import com.neomud.shared.model.Room
import com.neomud.shared.model.RoomEffect
import com.neomud.shared.model.RoomInteractable
import com.neomud.server.game.GameConfig
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("WorldLoader")

object WorldLoader {
    private val json = Json { ignoreUnknownKeys = true }

    data class LoadResult(
        val worldGraph: WorldGraph,
        val npcDataList: List<Pair<NpcData, String>>,
        val classCatalog: ClassCatalog,
        val itemCatalog: ItemCatalog,
        val lootTableCatalog: LootTableCatalog,
        val skillCatalog: SkillCatalog,
        val raceCatalog: RaceCatalog,
        val spellCatalog: SpellCatalog,
        val pcSpriteCatalog: PcSpriteCatalog,
        val recipeCatalog: RecipeCatalog,
        val zoneSpawnConfigs: Map<String, SpawnConfig>,
        val roomMaxHostileNpcs: Map<String, Int>,
        val manifest: WorldManifest? = null
    )

    fun load(source: WorldDataSource): LoadResult {
        // Load and validate manifest
        val manifest = source.readText("manifest.json")?.let {
            val m = json.decodeFromString<WorldManifest>(it)
            if (m.formatVersion > GameConfig.WorldFormat.CURRENT_FORMAT_VERSION) {
                error("Unsupported world format version ${m.formatVersion} (max supported: ${GameConfig.WorldFormat.CURRENT_FORMAT_VERSION})")
            }
            if (m.engineVersionMin.isNotEmpty() &&
                NeoMudVersion.compareVersions(NeoMudVersion.ENGINE_VERSION, m.engineVersionMin) < 0) {
                error("World '${m.name}' requires engine >= ${m.engineVersionMin}, but server is $NeoMudVersion.ENGINE_VERSION. Please update the server.")
            }
            logger.info("World bundle: ${m.name} v${m.version} by ${m.author} [${m.worldId}]")
            m
        }

        val classCatalog = ClassCatalog.load(source)
        val itemCatalog = ItemCatalog.load(source)
        val lootTableCatalog = LootTableCatalog.load(source)
        val skillCatalog = SkillCatalog.load(source)
        val raceCatalog = RaceCatalog.load(source)
        val spellCatalog = SpellCatalog.load(source)
        val pcSpriteCatalog = PcSpriteCatalog.load(source)
        val recipeCatalog = RecipeCatalog.load(source)
        val worldGraph = WorldGraph()
        val allNpcData = mutableListOf<Pair<NpcData, String>>()
        val zoneSpawnConfigs = mutableMapOf<String, SpawnConfig>()
        val roomMaxHostileNpcs = mutableMapOf<String, Int>()
        val zoneFiles = source.list("world/", ".zone.json")
        var dataDefinedSpawn: String? = null

        for (file in zoneFiles) {
            val content = source.readText(file)
            if (content == null) {
                logger.warn("Zone file not found: $file")
                continue
            }

            val zone = json.decodeFromString<ZoneData>(content)

            logger.info("Loading zone: ${zone.name} (${zone.rooms.size} rooms, ${zone.npcs.size} NPCs)")

            for (roomData in zone.rooms) {
                // Migrate legacy healPerTick to effects list
                val effects = roomData.effects.ifEmpty {
                    if (roomData.healPerTick > 0) {
                        listOf(RoomEffect(type = "HEAL", value = roomData.healPerTick))
                    } else emptyList()
                }

                // Merge hidden exits into regular exits and lockedExits
                val mergedExits = roomData.exits.toMutableMap()
                val mergedLockedExits = roomData.lockedExits.toMutableMap()
                for ((dir, hiddenData) in roomData.hiddenExits) {
                    // Hidden exit target must already be in exits map
                    if (dir !in mergedExits) {
                        logger.warn("Room '${roomData.id}' hiddenExits direction $dir has no target in exits map — skipping")
                        continue
                    }
                    if (hiddenData.lockDifficulty > 0) {
                        mergedLockedExits[dir] = hiddenData.lockDifficulty
                    }
                }

                // Combine lock reset durations from both regular and hidden exits
                val allLockResetDurations = roomData.lockResetTicks.toMutableMap()
                for ((dir, hiddenData) in roomData.hiddenExits) {
                    if (hiddenData.lockResetTicks > 0 && dir !in allLockResetDurations) {
                        allLockResetDurations[dir] = hiddenData.lockResetTicks
                    }
                }

                val room = Room(
                    id = roomData.id,
                    name = roomData.name,
                    description = roomData.description,
                    exits = mergedExits,
                    zoneId = zone.id,
                    x = roomData.x,
                    y = roomData.y,
                    backgroundImage = roomData.backgroundImage,
                    effects = effects,
                    bgm = roomData.bgm.ifEmpty { zone.bgm },
                    departSound = roomData.departSound,
                    lockedExits = mergedLockedExits,
                    interactables = roomData.interactables,
                    unpickableExits = roomData.unpickableExits
                )
                worldGraph.addRoom(room)

                // Store original locked exits for re-locking
                if (mergedLockedExits.isNotEmpty()) {
                    worldGraph.setOriginalLockedExits(roomData.id, mergedLockedExits.toMap())
                }
                // Store lock reset durations
                worldGraph.setLockResetDurations(roomData.id, allLockResetDurations.toMap())
                // Store hidden exit definitions
                worldGraph.setHiddenExitDefs(roomData.id, roomData.hiddenExits)
                // Store interactable definitions
                worldGraph.storeInteractableDefs(roomData.id, roomData.interactables)
                // Store per-room hostile NPC cap if specified
                roomData.maxHostileNpcs?.let { roomMaxHostileNpcs[roomData.id] = it }
            }

            allNpcData.addAll(zone.npcs.map { it to zone.id })
            zoneSpawnConfigs[zone.id] = zone.spawnConfig

            if (dataDefinedSpawn == null && zone.spawnRoom != null) {
                dataDefinedSpawn = zone.spawnRoom
            }
        }

        if (dataDefinedSpawn == null) {
            error("No zone defines a spawnRoom. At least one zone must specify a spawnRoom.")
        }
        worldGraph.setDefaultSpawn(dataDefinedSpawn)
        logger.info("World loaded: ${worldGraph.roomCount} rooms, ${allNpcData.size} NPCs")

        // Item data validation
        val validSlots = EquipmentSlots.DEFAULT_SLOTS.toSet()
        for (item in itemCatalog.getAllItems()) {
            if (item.type == "weapon") {
                if (item.slot.isBlank()) logger.warn("Weapon '${item.id}' missing slot (should be \"weapon\")")
                if (item.damageBonus == 0 && item.damageRange == 0) logger.warn("Weapon '${item.id}' has zero damageBonus and zero damageRange")
            }
            val accessorySlots = setOf("neck", "ring")
            if (item.type == "armor" && item.armorValue == 0 && item.slot !in accessorySlots) {
                logger.warn("Armor '${item.id}' has zero armorValue")
            }
            if (item.type == "consumable" && item.useEffect.isBlank()) {
                logger.warn("Consumable '${item.id}' missing useEffect")
            }
            if (item.stackable && item.maxStack <= 1) {
                logger.warn("Item '${item.id}' is stackable but maxStack=${item.maxStack}")
            }
            if (item.slot.isNotBlank() && item.slot !in validSlots) {
                logger.warn("Item '${item.id}' has unknown slot '${item.slot}'")
            }
        }

        // NPC data validation
        for ((npcData, _) in allNpcData) {
            if (npcData.hostile) {
                if (npcData.maxHp == 0) logger.warn("Hostile NPC '${npcData.id}' has maxHp=0")
                if (npcData.damage == 0) logger.warn("Hostile NPC '${npcData.id}' has damage=0")
                if (npcData.xpReward == 0L) logger.warn("Hostile NPC '${npcData.id}' has xpReward=0")
                if (lootTableCatalog.getLootTable(npcData.id).isEmpty()) {
                    logger.warn("Hostile NPC '${npcData.id}' has no loot table entry")
                }
            }
            if (npcData.vendorItems.isNotEmpty()) {
                for (itemId in npcData.vendorItems) {
                    if (itemCatalog.getItem(itemId) == null) {
                        logger.warn("Vendor NPC '${npcData.id}' references unknown item '$itemId'")
                    }
                }
            }
            if (npcData.behaviorType == "vendor" && npcData.vendorItems.isEmpty()) {
                logger.warn("Vendor NPC '${npcData.id}' has empty vendorItems")
            }
            if (npcData.behaviorType == "crafter" && npcData.crafterRecipes.isEmpty()) {
                logger.warn("Crafter NPC '${npcData.id}' has empty crafterRecipes")
            }
            if (npcData.behaviorType == "patrol" && npcData.patrolRoute.isEmpty()) {
                logger.warn("Patrol NPC '${npcData.id}' has empty patrolRoute")
            }
            if (worldGraph.getRoom(npcData.startRoomId) == null) {
                logger.warn("NPC '${npcData.id}' startRoomId '${npcData.startRoomId}' not found in loaded rooms")
            }
            for (roomId in npcData.spawnPoints) {
                if (worldGraph.getRoom(roomId) == null) {
                    logger.warn("NPC '${npcData.id}' spawnPoint '$roomId' not found in loaded rooms")
                }
            }
        }

        // Room data validation
        for (room in worldGraph.getAllRooms()) {
            if (room.exits.isEmpty()) logger.warn("Room '${room.id}' has zero exits (isolated)")
            if (room.backgroundImage.isBlank()) logger.warn("Room '${room.id}' missing backgroundImage")
            for ((direction, targetId) in room.exits) {
                if (worldGraph.getRoom(targetId) == null) {
                    logger.warn("Room '${room.id}' exit $direction points to unknown room '$targetId'")
                }
            }
        }

        // Interactable validation
        for (room in worldGraph.getAllRooms()) {
            val ids = mutableSetOf<String>()
            for (feat in room.interactables) {
                if (!ids.add(feat.id)) {
                    logger.warn("Room '${room.id}' has duplicate interactable id '${feat.id}'")
                }
                when (feat.actionType) {
                    "EXIT_OPEN" -> {
                        val dirStr = feat.actionData["direction"]
                        if (dirStr == null) {
                            logger.warn("Room '${room.id}' interactable '${feat.id}' EXIT_OPEN missing 'direction'")
                        } else {
                            try {
                                val dir = Direction.valueOf(dirStr)
                                if (dir !in room.exits) {
                                    logger.warn("Room '${room.id}' interactable '${feat.id}' EXIT_OPEN direction '$dirStr' not in room exits")
                                }
                            } catch (_: IllegalArgumentException) {
                                logger.warn("Room '${room.id}' interactable '${feat.id}' EXIT_OPEN invalid direction '$dirStr'")
                            }
                        }
                    }
                    "TELEPORT" -> {
                        val targetRoomId = feat.actionData["targetRoomId"]
                        if (targetRoomId != null && worldGraph.getRoom(targetRoomId) == null) {
                            logger.warn("Room '${room.id}' interactable '${feat.id}' TELEPORT targetRoomId '$targetRoomId' not found")
                        }
                    }
                    "TREASURE_DROP", "MONSTER_SPAWN", "ROOM_EFFECT",
                    "RIDDLE_PROMPT" -> { /* validated at runtime */ }
                    "DAMAGE_TRAP" -> {
                        val saveStat = feat.actionData["saveStat"]
                        if (!saveStat.isNullOrBlank()) {
                            val validStats = setOf("STRENGTH", "AGILITY", "TOUGHNESS", "INTELLECT", "WILLPOWER", "HEALTH", "CHARM")
                            if (saveStat.uppercase() !in validStats) {
                                logger.warn("Room '${room.id}' interactable '${feat.id}' DAMAGE_TRAP unknown saveStat '$saveStat'")
                            }
                        }
                    }
                    "PLACE_ITEM" -> {
                        val accepted = feat.actionData["acceptedItems"]
                        if (accepted != null) {
                            for (itemId in accepted.split(",").map { it.trim() }.filter { it.isNotEmpty() }) {
                                if (itemCatalog.getItem(itemId) == null) {
                                    logger.warn("Room '${room.id}' interactable '${feat.id}' PLACE_ITEM acceptedItem '$itemId' not found in ItemCatalog")
                                }
                            }
                        }
                        validateSuccessDirection(room, feat, "PLACE_ITEM")
                    }
                    "PUZZLE_STEP" -> {
                        validateSuccessDirection(room, feat, "PUZZLE_STEP")
                    }
                    "CONDITIONAL_TRIGGER" -> {
                        val conditionType = feat.actionData["conditionType"]
                        if (conditionType == "ITEM") {
                            val requiredItemId = feat.actionData["requiredItemId"]
                            if (requiredItemId != null && itemCatalog.getItem(requiredItemId) == null) {
                                logger.warn("Room '${room.id}' interactable '${feat.id}' CONDITIONAL_TRIGGER requiredItemId '$requiredItemId' not found in ItemCatalog")
                            }
                        }
                        validateSuccessDirection(room, feat, "CONDITIONAL_TRIGGER")
                    }
                    else -> logger.warn("Room '${room.id}' interactable '${feat.id}' unknown actionType '${feat.actionType}'")
                }
            }
        }

        // Loot table cross-reference validation
        for ((npcId, entry) in lootTableCatalog.getAllEntries()) {
            for (lootEntry in entry.items) {
                if (itemCatalog.getItem(lootEntry.itemId) == null) {
                    logger.warn("Loot table '$npcId' references unknown item '${lootEntry.itemId}'")
                }
            }
        }

        // Recipe validation
        for (recipe in recipeCatalog.getAllRecipes()) {
            if (itemCatalog.getItem(recipe.outputItemId) == null) {
                logger.warn("Recipe '${recipe.id}' output item '${recipe.outputItemId}' not found in item catalog")
            }
            for (mat in recipe.materials) {
                if (itemCatalog.getItem(mat.itemId) == null) {
                    logger.warn("Recipe '${recipe.id}' material '${mat.itemId}' not found in item catalog")
                }
            }
        }
        // NPC crafter recipe validation
        for ((npcData, _) in allNpcData) {
            for (recipeId in npcData.crafterRecipes) {
                if (recipeCatalog.getRecipe(recipeId) == null) {
                    logger.warn("Crafter NPC '${npcData.id}' references unknown recipe '$recipeId'")
                }
            }
        }

        // Sound field validation
        for (item in itemCatalog.getAllItems()) {
            if (item.type == "weapon") {
                if (item.attackSound.isBlank()) logger.warn("Weapon '${item.id}' missing attackSound")
                if (item.missSound.isBlank()) logger.warn("Weapon '${item.id}' missing missSound")
            }
            if (item.type == "consumable" && item.useSound.isBlank()) {
                logger.warn("Consumable '${item.id}' missing useSound")
            }
        }
        for ((npcData, _) in allNpcData) {
            if (npcData.hostile) {
                if (npcData.attackSound.isBlank()) logger.warn("Hostile NPC '${npcData.id}' missing attackSound")
                if (npcData.missSound.isBlank()) logger.warn("Hostile NPC '${npcData.id}' missing missSound")
                if (npcData.deathSound.isBlank()) logger.warn("Hostile NPC '${npcData.id}' missing deathSound")
            }
            if (npcData.interactSound.isBlank() && npcData.behaviorType in listOf("vendor", "trainer")) {
                logger.warn("NPC '${npcData.id}' (${npcData.behaviorType}) missing interactSound")
            }
            if (npcData.exitSound.isBlank() && npcData.behaviorType in listOf("vendor", "trainer")) {
                logger.warn("NPC '${npcData.id}' (${npcData.behaviorType}) missing exitSound")
            }
        }
        for (spell in spellCatalog.getAllSpells()) {
            if (spell.castSound.isBlank()) logger.warn("Spell '${spell.id}' missing castSound")
            if ((spell.spellType == com.neomud.shared.model.SpellType.DAMAGE || spell.spellType == com.neomud.shared.model.SpellType.DOT)) {
                if (spell.impactSound.isBlank()) logger.warn("Damage spell '${spell.id}' missing impactSound")
                if (spell.missSound.isBlank()) logger.warn("Damage spell '${spell.id}' missing missSound")
            }
        }
        for (room in worldGraph.getAllRooms()) {
            if (room.departSound.isBlank()) logger.info("Room '${room.id}' missing departSound")
        }

        // Asset file existence validation
        fun assetExists(path: String): Boolean = source.openStream(path)?.use { true } ?: false
        fun spritePathFor(entityId: String): String {
            val prefix = entityId.substringBefore(':')
            val folder = "${prefix}s" // npc -> npcs, item -> items
            return "assets/images/$folder/${entityId.replace(':', '_')}.webp"
        }
        fun sfxPathFor(soundId: String, category: String) = "assets/audio/$category/$soundId.mp3"
        fun bgmPathFor(trackId: String) = "assets/audio/bgm/$trackId.mp3"

        // Item sprites
        for (item in itemCatalog.getAllItems()) {
            val path = spritePathFor(item.id)
            if (!assetExists(path)) logger.warn("Item '${item.id}' missing sprite asset: $path")
        }
        // NPC sprites
        for ((npcData, _) in allNpcData) {
            val path = spritePathFor(npcData.id)
            if (!assetExists(path)) logger.warn("NPC '${npcData.id}' missing sprite asset: $path")
        }
        // Room background images
        for (room in worldGraph.getAllRooms()) {
            if (room.backgroundImage.isNotBlank()) {
                val path = room.backgroundImage.trimStart('/')
                if (!assetExists(path)) logger.warn("Room '${room.id}' missing background asset: $path")
            }
        }
        // SFX files
        val checkedSfx = mutableSetOf<String>()
        fun checkSfx(soundId: String, category: String, owner: String) {
            val key = "$category/$soundId"
            if (soundId.isNotBlank() && checkedSfx.add(key) && !assetExists(sfxPathFor(soundId, category))) {
                logger.warn("Missing SFX asset: ${sfxPathFor(soundId, category)} (referenced by $owner)")
            }
        }
        for (item in itemCatalog.getAllItems()) {
            for (sound in listOf(item.attackSound, item.missSound, item.useSound)) {
                checkSfx(sound, "items", "item '${item.id}'")
            }
        }
        for ((npcData, _) in allNpcData) {
            for (sound in listOf(npcData.attackSound, npcData.missSound, npcData.deathSound, npcData.interactSound, npcData.exitSound)) {
                checkSfx(sound, "npcs", "NPC '${npcData.id}'")
            }
        }
        for (spell in spellCatalog.getAllSpells()) {
            for (sound in listOf(spell.castSound, spell.impactSound, spell.missSound)) {
                checkSfx(sound, "spells", "spell '${spell.id}'")
            }
        }
        for (room in worldGraph.getAllRooms()) {
            checkSfx(room.departSound, "rooms", "room '${room.id}'")
        }
        // BGM files
        val checkedBgm = mutableSetOf<String>()
        for (room in worldGraph.getAllRooms()) {
            val bgm = room.bgm
            if (bgm.isNotBlank() && checkedBgm.add(bgm) && !assetExists(bgmPathFor(bgm))) {
                logger.warn("Missing BGM asset: ${bgmPathFor(bgm)} (referenced by room '${room.id}')")
            }
        }

        return LoadResult(worldGraph, allNpcData, classCatalog, itemCatalog, lootTableCatalog, skillCatalog, raceCatalog, spellCatalog, pcSpriteCatalog, recipeCatalog, zoneSpawnConfigs, roomMaxHostileNpcs, manifest)
    }

    private fun validateSuccessDirection(room: Room, feat: RoomInteractable, actionType: String) {
        val dirStr = feat.actionData["successDirection"] ?: return
        try {
            val dir = Direction.valueOf(dirStr)
            if (dir !in room.exits) {
                logger.warn("Room '${room.id}' interactable '${feat.id}' $actionType successDirection '$dirStr' not in room exits")
            }
        } catch (_: IllegalArgumentException) {
            logger.warn("Room '${room.id}' interactable '${feat.id}' $actionType invalid successDirection '$dirStr'")
        }
    }
}
