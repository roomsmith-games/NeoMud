package com.neomud.server.game.npc

import com.neomud.server.defaultWorldSource
import com.neomud.server.game.npc.behavior.IdleBehavior
import com.neomud.server.world.BossPhaseData
import com.neomud.server.world.NpcData
import com.neomud.server.world.WorldGraph
import com.neomud.server.world.WorldLoader
import com.neomud.shared.model.Room
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NpcManagerPhaseTest {

    private fun buildWorld(): WorldGraph {
        val wg = WorldGraph()
        wg.addRoom(Room("keep:throne", "Throne Room", "A throne room.", emptyMap(), "keep", 0, 0))
        wg.setDefaultSpawn("keep:throne")
        return wg
    }

    private fun bossNpcData(phases: List<BossPhaseData> = emptyList()) = NpcData(
        id = "npc:stormcrown",
        name = "Stormcrown",
        description = "A fearsome boss.",
        startRoomId = "keep:throne",
        behaviorType = "idle",
        hostile = true,
        maxHp = 1000,
        damage = 50,
        accuracy = 40,
        defense = 20,
        evasion = 10,
        level = 27,
        xpReward = 2000,
        phases = phases
    )

    @Test
    fun `phases copied from NpcData to NpcState`() {
        val phases = listOf(
            BossPhaseData(name = "Lightning Form", hpThresholdPercent = 0.5, damage = 85)
        )
        val wg = buildWorld()
        val manager = NpcManager(wg)
        manager.loadNpcs(listOf(bossNpcData(phases) to "keep"))

        val state = manager.getNpcState("npc:stormcrown")!!
        assertEquals(1, state.phases.size)
        assertEquals("Lightning Form", state.phases[0].name)
        assertEquals(0.5, state.phases[0].hpThresholdPercent)
        assertEquals(0, state.currentPhase)
    }

    @Test
    fun `NPC without phases has empty list`() {
        val wg = buildWorld()
        val manager = NpcManager(wg)
        manager.loadNpcs(listOf(bossNpcData() to "keep"))

        val state = manager.getNpcState("npc:stormcrown")!!
        assertTrue(state.phases.isEmpty())
        assertEquals(0, state.currentPhase)
    }

    @Test
    fun `spriteOverride passed through getNpcsInRoom`() {
        val wg = buildWorld()
        val manager = NpcManager(wg)
        manager.loadNpcs(listOf(bossNpcData() to "keep"))

        val state = manager.getNpcState("npc:stormcrown")!!
        state.spriteOverride = "npc:stormcrown_phase2"

        val npcs = manager.getNpcsInRoom("keep:throne")
        assertEquals(1, npcs.size)
        assertEquals("npc:stormcrown_phase2", npcs[0].spriteOverride)
    }

    @Test
    fun `spriteOverride defaults to empty`() {
        val wg = buildWorld()
        val manager = NpcManager(wg)
        manager.loadNpcs(listOf(bossNpcData() to "keep"))

        val npcs = manager.getNpcsInRoom("keep:throne")
        assertEquals(1, npcs.size)
        assertEquals("", npcs[0].spriteOverride)
    }

    @Test
    fun `sealed threshold bosses have phases in default world`() {
        val result = WorldLoader.load(defaultWorldSource())
        val manager = NpcManager(result.worldGraph)
        manager.loadNpcs(result.npcDataList)

        val sealBoss = manager.getNpcState("npc:sealed_one_seal")
        assertNotNull(sealBoss, "Seal variant boss should be loaded")
        assertEquals(2, sealBoss.phases.size, "Seal boss should have 2 phases")
        assertEquals(0.66, sealBoss.phases[0].hpThresholdPercent)
        assertEquals(0.33, sealBoss.phases[1].hpThresholdPercent)
        assertEquals(72, sealBoss.phases[0].damage)
        assertEquals(84, sealBoss.phases[1].damage)

        val sunderBoss = manager.getNpcState("npc:sealed_one_sunder")
        assertNotNull(sunderBoss, "Sunder variant boss should be loaded")
        assertEquals(2, sunderBoss.phases.size, "Sunder boss should have 2 phases")
        assertEquals(100, sunderBoss.phases[0].damage)
        assertEquals(110, sunderBoss.phases[1].damage)

        val guardian = manager.getNpcState("npc:threshold_guardian")
        assertNotNull(guardian, "Threshold Guardian should be loaded")
        assertEquals(1, guardian.phases.size, "Guardian should have 1 phase")
        assertEquals(0.5, guardian.phases[0].hpThresholdPercent)
    }

    @Test
    fun `mutable stats can be changed for phase transition`() {
        val wg = buildWorld()
        val manager = NpcManager(wg)
        manager.loadNpcs(listOf(bossNpcData() to "keep"))

        val state = manager.getNpcState("npc:stormcrown")!!
        assertEquals(50, state.damage)
        assertEquals(40, state.accuracy)
        assertEquals(20, state.defense)
        assertEquals(10, state.evasion)

        state.damage = 85
        state.accuracy = 65
        state.defense = 40
        state.evasion = 20

        assertEquals(85, state.damage)
        assertEquals(65, state.accuracy)
        assertEquals(40, state.defense)
        assertEquals(20, state.evasion)
    }
}
