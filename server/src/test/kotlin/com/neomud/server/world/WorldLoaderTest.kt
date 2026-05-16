package com.neomud.server.world

import com.neomud.server.defaultWorldSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorldLoaderTest {

    private fun load() = WorldLoader.load(defaultWorldSource())

    @Test
    fun testLoadWorldFromResources() {
        val result = load()
        val world = result.worldGraph

        assertTrue(world.roomCount >= 25, "Should load at least 25 rooms (7 town + 7 forest + 6 marsh + 5 gorge)")
    }

    @Test
    fun testTownRoomsLoaded() {
        val result = load()
        val world = result.worldGraph

        assertNotNull(world.getRoom("town:square"), "town:square should exist")
        assertNotNull(world.getRoom("town:tavern"), "town:tavern should exist")
        assertNotNull(world.getRoom("town:market"), "town:market should exist")
        assertNotNull(world.getRoom("town:gate"), "town:gate should exist")
        assertNotNull(world.getRoom("town:temple"), "town:temple should exist")
        assertNotNull(world.getRoom("town:cellar"), "town:cellar should exist")
        assertNotNull(world.getRoom("town:magic_shop"), "town:magic_shop should exist")
    }

    @Test
    fun testForestRoomsLoaded() {
        val result = load()
        val world = result.worldGraph

        assertNotNull(world.getRoom("forest:edge"), "forest:edge should exist")
        assertNotNull(world.getRoom("forest:path"), "forest:path should exist")
        assertNotNull(world.getRoom("forest:clearing"), "forest:clearing should exist")
        assertNotNull(world.getRoom("forest:deep"), "forest:deep should exist")
        assertNotNull(world.getRoom("forest:stream"), "forest:stream should exist")
        assertNotNull(world.getRoom("forest:ruins"), "forest:ruins should exist")
    }

    @Test
    fun testCrossZoneExits() {
        val result = load()
        val world = result.worldGraph

        val gate = world.getRoom("town:gate")
        assertNotNull(gate)
        val northExit = gate.exits[com.neomud.shared.model.Direction.NORTH]
        assertEquals("forest:edge", northExit, "town:gate should exit north to forest:edge")

        val forestEdge = world.getRoom("forest:edge")
        assertNotNull(forestEdge)
        val southExit = forestEdge.exits[com.neomud.shared.model.Direction.SOUTH]
        assertEquals("town:gate", southExit, "forest:edge should exit south to town:gate")
    }

    @Test
    fun testNpcsLoaded() {
        val result = load()
        assertTrue(result.npcDataList.isNotEmpty(), "Should load NPCs")
        assertTrue(result.npcDataList.size >= 17, "Should load at least 17 NPCs (5 town + 4 forest + 4 marsh + 4 gorge)")

        val guard = result.npcDataList.find { it.first.id == "npc:town_guard" }
        assertNotNull(guard, "Town guard NPC should be loaded")
        assertEquals("patrol", guard.first.behaviorType)
        assertTrue(guard.first.patrolRoute.isNotEmpty(), "Guard should have patrol route")
        assertEquals("town", guard.second, "Guard should belong to town zone")

        val barkeep = result.npcDataList.find { it.first.id == "npc:barkeep" }
        assertNotNull(barkeep, "Barkeep NPC should be loaded")
        assertEquals("vendor", barkeep.first.behaviorType)
        assertTrue(barkeep.first.vendorItems.isNotEmpty(), "Barkeep should have vendor items")
        assertTrue("item:health_potion" in barkeep.first.vendorItems, "Barkeep should sell health potions")
        assertTrue("item:ale" in barkeep.first.vendorItems, "Barkeep should sell ale")

        val enchantress = result.npcDataList.find { it.first.id == "npc:enchantress" }
        assertNotNull(enchantress, "Enchantress NPC should be loaded")
        assertEquals("vendor", enchantress.first.behaviorType)
        assertEquals("town", enchantress.second, "Enchantress should belong to town zone")
        assertTrue(enchantress.first.vendorItems.isNotEmpty(), "Enchantress should have vendor items")
        assertTrue("item:mystic_staff" in enchantress.first.vendorItems, "Enchantress should sell mystic staff")
    }

    @Test
    fun testMonsterNpcsLoaded() {
        val result = load()

        val wolf = result.npcDataList.find { it.first.id == "npc:shadow_wolf" }
        assertNotNull(wolf, "Shadow Wolf should be loaded")
        assertEquals("wander", wolf.first.behaviorType)
        assertTrue(wolf.first.hostile, "Shadow Wolf should be hostile")
        assertEquals(30, wolf.first.maxHp)
        assertEquals(3, wolf.first.damage)
        assertEquals(2, wolf.first.level)
        assertEquals("forest", wolf.second, "Shadow Wolf should belong to forest zone")

        val spider = result.npcDataList.find { it.first.id == "npc:forest_spider" }
        assertNotNull(spider, "Giant Forest Spider should be loaded")
        assertEquals("wander", spider.first.behaviorType)
        assertTrue(spider.first.hostile, "Spider should be hostile")
        assertEquals(20, spider.first.maxHp)
        assertEquals(5, spider.first.damage)
        assertEquals(3, spider.first.level)
        assertEquals("forest", spider.second, "Spider should belong to forest zone")
    }

    @Test
    fun testMagicShopRoomLoaded() {
        val result = load()
        val world = result.worldGraph

        val shop = world.getRoom("town:magic_shop")
        assertNotNull(shop, "town:magic_shop should exist")
        assertEquals("The Enchanted Emporium", shop.name)
        assertEquals("town:market", shop.exits[com.neomud.shared.model.Direction.WEST])

        val market = world.getRoom("town:market")
        assertNotNull(market)
        assertEquals("town:magic_shop", market.exits[com.neomud.shared.model.Direction.EAST],
            "town:market should exit east to town:magic_shop")
    }

    @Test
    fun testLockedExitsLoaded() {
        val result = load()
        val world = result.worldGraph

        val deep = world.getRoom("forest:deep")
        assertNotNull(deep)
        assertEquals(0, deep.lockedExits.size, "forest:deep should have 0 locked exits (all are hidden-only)")

        val tavern = world.getRoom("town:tavern")
        assertNotNull(tavern)
        assertEquals(1, tavern.lockedExits.size, "town:tavern should have 1 locked exit")
        assertEquals(10, tavern.lockedExits[com.neomud.shared.model.Direction.DOWN])
    }

    @Test
    fun testHiddenExitsLoaded() {
        val result = load()
        val world = result.worldGraph

        val deep = world.getRoom("forest:deep")
        assertNotNull(deep)

        // All three exits should be in the exits map
        assertEquals("forest:stream", deep.exits[com.neomud.shared.model.Direction.EAST])
        assertEquals("forest:ruins", deep.exits[com.neomud.shared.model.Direction.NORTH])
        assertEquals("forest:cave", deep.exits[com.neomud.shared.model.Direction.WEST])

        // Hidden exits are hidden-only (no locks)
        assertTrue(deep.lockedExits.isEmpty(), "forest:deep should have no locked exits")

        // Hidden exit defs should be stored for EAST and NORTH (WEST is now visible)
        val hiddenDefs = world.getHiddenExitDefs("forest:deep")
        assertEquals(2, hiddenDefs.size, "forest:deep should have 2 hidden exit defs")

        val eastDef = hiddenDefs[com.neomud.shared.model.Direction.EAST]
        assertNotNull(eastDef)
        assertEquals(14, eastDef.perceptionDC)
        assertEquals(0, eastDef.lockDifficulty)
        assertEquals(40, eastDef.hiddenResetTicks)
        assertEquals(0, eastDef.lockResetTicks)

        val northDef = hiddenDefs[com.neomud.shared.model.Direction.NORTH]
        assertNotNull(northDef)
        assertEquals(12, northDef.perceptionDC)
        assertEquals(0, northDef.lockDifficulty)
        assertEquals(50, northDef.hiddenResetTicks)
        assertEquals(0, northDef.lockResetTicks)
    }

    @Test
    fun testLockResetTicksLoaded() {
        val result = load()
        val world = result.worldGraph

        // forest:deep hidden exits have no locks — verify lockResetTicks are 0
        val deep = world.getRoom("forest:deep")
        assertNotNull(deep)
        assertTrue(deep.lockedExits.isEmpty(), "forest:deep should have no locked exits")
        val eastDef = world.getHiddenExitDefs("forest:deep")[com.neomud.shared.model.Direction.EAST]
        assertNotNull(eastDef)
        assertEquals(0, eastDef.lockResetTicks)
    }

    @Test
    fun testForestCaveRoomLoaded() {
        val result = load()
        val world = result.worldGraph

        val cave = world.getRoom("forest:cave")
        assertNotNull(cave, "forest:cave should exist (hidden exit target)")
        assertEquals("Hidden Cave", cave.name)
        assertEquals("forest:deep", cave.exits[com.neomud.shared.model.Direction.EAST])
    }

    @Test
    fun testMarshRoomsLoaded() {
        val result = load()
        val world = result.worldGraph

        assertNotNull(world.getRoom("marsh:edge"), "marsh:edge should exist")
        assertNotNull(world.getRoom("marsh:trail"), "marsh:trail should exist")
        assertNotNull(world.getRoom("marsh:shallows"), "marsh:shallows should exist")
        assertNotNull(world.getRoom("marsh:hollow"), "marsh:hollow should exist")
        assertNotNull(world.getRoom("marsh:island"), "marsh:island should exist")
        assertNotNull(world.getRoom("marsh:heart"), "marsh:heart should exist")
    }

    @Test
    fun testGorgeRoomsLoaded() {
        val result = load()
        val world = result.worldGraph

        assertNotNull(world.getRoom("gorge:mouth"), "gorge:mouth should exist")
        assertNotNull(world.getRoom("gorge:ledge"), "gorge:ledge should exist")
        assertNotNull(world.getRoom("gorge:fissure"), "gorge:fissure should exist")
        assertNotNull(world.getRoom("gorge:alcove"), "gorge:alcove should exist")
        assertNotNull(world.getRoom("gorge:depths"), "gorge:depths should exist")
    }

    @Test
    fun testForestToMarshCrossZoneExits() {
        val result = load()
        val world = result.worldGraph

        // Marsh is only accessible via gorge:mouth (single-entry zone)
        val cave = world.getRoom("forest:cave")
        assertNotNull(cave)
        assertNull(cave.exits[com.neomud.shared.model.Direction.WEST],
            "forest:cave should not exit west to marsh (marsh is single-entry via gorge)")

        val mouth = world.getRoom("gorge:mouth")
        assertNotNull(mouth)
        assertEquals("marsh:heart", mouth.exits[com.neomud.shared.model.Direction.DOWN],
            "gorge:mouth should exit down to marsh:heart")
    }

    @Test
    fun testForestToGorgeCrossZoneExits() {
        val result = load()
        val world = result.worldGraph

        val stream = world.getRoom("forest:stream")
        assertNotNull(stream)
        assertEquals("gorge:fissure", stream.exits[com.neomud.shared.model.Direction.NORTHEAST],
            "forest:stream should exit northeast to gorge:fissure")

        val fissure = world.getRoom("gorge:fissure")
        assertNotNull(fissure)
        assertEquals("forest:stream", fissure.exits[com.neomud.shared.model.Direction.SOUTHWEST],
            "gorge:fissure should exit southwest to forest:stream")
    }

    @Test
    fun testMarshToGorgeCrossZoneExits() {
        val result = load()
        val world = result.worldGraph

        val heart = world.getRoom("marsh:heart")
        assertNotNull(heart)
        assertEquals("gorge:mouth", heart.exits[com.neomud.shared.model.Direction.UP],
            "marsh:heart should exit up to gorge:mouth")

        val gorgeMouth = world.getRoom("gorge:mouth")
        assertNotNull(gorgeMouth)
        assertEquals("marsh:heart", gorgeMouth.exits[com.neomud.shared.model.Direction.DOWN],
            "gorge:mouth should exit down to marsh:heart")
    }

    @Test
    fun testMarshNpcsLoaded() {
        val result = load()

        val lurker = result.npcDataList.find { it.first.id == "npc:bog_lurker" }
        assertNotNull(lurker, "Bog Lurker should be loaded")
        assertEquals("wander", lurker.first.behaviorType)
        assertTrue(lurker.first.hostile)
        assertEquals(55, lurker.first.maxHp)
        assertEquals(14, lurker.first.damage)
        assertEquals(4, lurker.first.level)
        assertEquals("marsh", lurker.second, "Bog Lurker should belong to marsh zone")

        val hag = result.npcDataList.find { it.first.id == "npc:mire_hag" }
        assertNotNull(hag, "Mire Hag should be loaded")
        assertEquals("idle", hag.first.behaviorType)
        assertEquals(100, hag.first.maxHp)
        assertEquals(5, hag.first.level)
        assertEquals("marsh", hag.second)
    }

    @Test
    fun testGorgeNpcsLoaded() {
        val result = load()

        val stalker = result.npcDataList.find { it.first.id == "npc:gorge_stalker" }
        assertNotNull(stalker, "Gorge Stalker should be loaded")
        assertEquals("patrol", stalker.first.behaviorType)
        assertTrue(stalker.first.patrolRoute.isNotEmpty(), "Gorge Stalker should have patrol route")
        assertEquals(80, stalker.first.maxHp)
        assertEquals(6, stalker.first.level)
        assertEquals("gorge", stalker.second)

        val warden = result.npcDataList.find { it.first.id == "npc:gorge_warden" }
        assertNotNull(warden, "Gorge Warden should be loaded")
        assertEquals("idle", warden.first.behaviorType)
        assertEquals(140, warden.first.maxHp)
        assertEquals(7, warden.first.level)
        assertEquals("gorge", warden.second)
    }

    @Test
    fun testNpcExitSoundLoaded() {
        val result = load()

        val barkeep = result.npcDataList.find { it.first.id == "npc:barkeep" }
        assertNotNull(barkeep, "Barkeep NPC should be loaded")
        assertEquals("barkeep_intro", barkeep.first.interactSound, "Barkeep should have barkeep_intro interactSound")
        assertEquals("barkeep_exit", barkeep.first.exitSound, "Barkeep should have barkeep_exit exitSound")

        val guildmaster = result.npcDataList.find { it.first.id == "npc:guildmaster" }
        assertNotNull(guildmaster, "Guildmaster NPC should be loaded")
        assertEquals("guildmaster_intro", guildmaster.first.interactSound, "Guildmaster should have guildmaster_intro interactSound")
        assertEquals("guildmaster_exit", guildmaster.first.exitSound, "Guildmaster should have guildmaster_exit exitSound")

        val blacksmith = result.npcDataList.find { it.first.id == "npc:blacksmith" }
        assertNotNull(blacksmith, "Blacksmith NPC should be loaded")
        assertEquals("blacksmith_intro", blacksmith.first.interactSound, "Blacksmith should have blacksmith_intro interactSound")
        assertEquals("blacksmith_exit", blacksmith.first.exitSound, "Blacksmith should have blacksmith_exit exitSound")

        val enchantress = result.npcDataList.find { it.first.id == "npc:enchantress" }
        assertNotNull(enchantress, "Enchantress NPC should be loaded")
        assertEquals("enchantress_intro", enchantress.first.interactSound, "Enchantress should have enchantress_intro interactSound")
        assertEquals("enchantress_exit", enchantress.first.exitSound, "Enchantress should have enchantress_exit exitSound")
    }

    @Test
    fun testCellarExitsLoaded() {
        val result = load()
        val world = result.worldGraph

        val cellar = world.getRoom("town:cellar")
        assertNotNull(cellar, "town:cellar should exist")
        assertEquals("town:tavern", cellar.exits[com.neomud.shared.model.Direction.UP],
            "town:cellar should have UP exit to town:tavern")
        assertTrue(com.neomud.shared.model.Direction.UP in cellar.lockedExits,
            "town:cellar UP exit should be locked")
        assertEquals(99, cellar.lockedExits[com.neomud.shared.model.Direction.UP],
            "town:cellar UP lock difficulty should be 99")
    }

    @Test
    fun testTownGuardHasCombatStats() {
        val result = load()
        val guard = result.npcDataList.find { it.first.id == "npc:town_guard" }
        assertNotNull(guard, "Town guard should be loaded")
        assertEquals(5, guard.first.level, "Guard should be level 5")
        assertEquals(120, guard.first.maxHp, "Guard should have 120 HP")
        assertEquals(10, guard.first.damage, "Guard should have 10 damage")
        assertEquals(false, guard.first.hostile, "Guard should not be hostile")
    }

    @Test
    fun testDefaultSpawnRoom() {
        val result = load()
        assertEquals("town:temple", result.worldGraph.defaultSpawnRoom)
    }

    @Test
    fun testClassCatalogLoaded() {
        val result = load()
        assertEquals(15, result.classCatalog.classCount)
    }

    @Test
    fun testItemCatalogLoaded() {
        val result = load()
        assertTrue(result.itemCatalog.itemCount >= 38, "Should load at least 38 items")
    }

    @Test
    fun testLootTableCatalogLoaded() {
        val result = load()
        assertTrue(result.lootTableCatalog.tableCount >= 12, "Should load at least 12 loot tables (4 forest + 4 marsh + 4 gorge)")
    }

    @Test
    fun testAllCombatZonesHaveWorkingSpawnConfigs() {
        val result = load()
        val zonesWithHostileNpcs = result.npcDataList
            .filter { it.first.hostile }
            .map { it.second }
            .toSet()

        for (zoneId in zonesWithHostileNpcs) {
            val config = result.zoneSpawnConfigs[zoneId]
            assertNotNull(config, "Zone '$zoneId' has hostile NPCs but no spawn config")
            assertTrue(config.rateTicks > 0,
                "Zone '$zoneId' has rateTicks=0 — NPCs will never respawn (wrong field name?)")
            assertTrue(config.maxEntities > 0,
                "Zone '$zoneId' has maxEntities=0 — NPCs will never respawn (wrong field name?)")
        }
    }

    @Test
    fun testStarterZoneSpawnConfigAllowsResting() {
        val result = load()
        val forestConfig = result.zoneSpawnConfigs["forest"]
        assertNotNull(forestConfig, "Forest zone should have a spawn config")

        assertTrue(forestConfig.maxPerRoom <= 1,
            "Forest maxPerRoom should be ≤1 so L1 players only face one hostile at a time, got ${forestConfig.maxPerRoom}")
        assertTrue(forestConfig.rateTicks >= 20,
            "Forest rateTicks should be ≥20 (~30s) to give L1 players time to rest between fights, got ${forestConfig.rateTicks}")
    }

    @Test
    fun testManifestHasVersioningFields() {
        val result = load()
        val manifest = result.manifest
        assertNotNull(manifest, "Default world should have a manifest")
        assertEquals("0.1.0.0", manifest.engineVersion)
        assertEquals("0.1.0.0", manifest.engineVersionMin)
        assertEquals("00000000-0000-0000-0000-000000000001", manifest.worldId)
        assertTrue(manifest.createdWithMaker)
    }
}
