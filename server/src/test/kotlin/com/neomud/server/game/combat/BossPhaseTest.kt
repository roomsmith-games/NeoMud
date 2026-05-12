package com.neomud.server.game.combat

import com.neomud.server.game.npc.NpcState
import com.neomud.server.game.npc.behavior.IdleBehavior
import com.neomud.server.world.BossPhaseData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BossPhaseTest {

    private fun createBossNpc(
        maxHp: Int = 1000,
        currentHp: Int = maxHp,
        damage: Int = 50,
        accuracy: Int = 40,
        defense: Int = 20,
        evasion: Int = 10,
        phases: List<BossPhaseData> = emptyList()
    ) = NpcState(
        id = "npc:stormcrown",
        name = "Stormcrown",
        description = "A fearsome boss",
        currentRoomId = "keep:throne",
        behavior = IdleBehavior(),
        hostile = true,
        maxHp = maxHp,
        currentHp = currentHp,
        damage = damage,
        accuracy = accuracy,
        defense = defense,
        evasion = evasion,
        phases = phases
    )

    private fun singlePhase(threshold: Double = 0.5) = listOf(
        BossPhaseData(
            name = "Lightning Form",
            hpThresholdPercent = threshold,
            spriteId = "npc:stormcrown_phase2",
            damage = 85,
            accuracy = 65,
            defense = 40,
            evasion = 20,
            transitionMessage = "Lightning crackles!",
            transitionSound = "boss_phase_shift"
        )
    )

    private fun checkPhaseTransition(npc: NpcState, events: MutableList<CombatEvent>, roomId: String) {
        if (npc.phases.isEmpty()) return
        if (npc.currentPhase >= npc.phases.size) return
        val phase = npc.phases[npc.currentPhase]
        val thresholdHp = (npc.maxHp * phase.hpThresholdPercent).toInt()
        if (npc.currentHp > thresholdHp) return

        npc.currentHp = maxOf(thresholdHp, 1)
        phase.damage?.let { npc.damage = it }
        phase.accuracy?.let { npc.accuracy = it }
        phase.defense?.let { npc.defense = it }
        phase.evasion?.let { npc.evasion = it }
        if (phase.spriteId.isNotEmpty()) npc.spriteOverride = phase.spriteId
        npc.currentPhase++

        events.add(CombatEvent.NpcPhaseShift(
            npcId = npc.id,
            npcName = npc.name,
            phaseName = phase.name,
            spriteId = phase.spriteId,
            message = phase.transitionMessage,
            currentHp = npc.currentHp,
            maxHp = npc.maxHp,
            sound = phase.transitionSound,
            roomId = roomId
        ))
    }

    @Test
    fun `phase fires at HP threshold`() {
        val npc = createBossNpc(maxHp = 1000, currentHp = 1000, phases = singlePhase(0.5))
        val events = mutableListOf<CombatEvent>()

        npc.currentHp = 500
        checkPhaseTransition(npc, events, "keep:throne")

        assertEquals(1, events.size)
        val shift = events[0] as CombatEvent.NpcPhaseShift
        assertEquals("Lightning Form", shift.phaseName)
        assertEquals("npc:stormcrown_phase2", shift.spriteId)
        assertEquals("Lightning crackles!", shift.message)
    }

    @Test
    fun `HP clamped to threshold`() {
        val npc = createBossNpc(maxHp = 1000, currentHp = 1000, phases = singlePhase(0.5))
        val events = mutableListOf<CombatEvent>()

        npc.currentHp = 100
        checkPhaseTransition(npc, events, "keep:throne")

        assertEquals(500, npc.currentHp)
    }

    @Test
    fun `stats updated on phase transition`() {
        val npc = createBossNpc(
            maxHp = 1000, currentHp = 1000,
            damage = 50, accuracy = 40, defense = 20, evasion = 10,
            phases = singlePhase(0.5)
        )
        val events = mutableListOf<CombatEvent>()

        npc.currentHp = 400
        checkPhaseTransition(npc, events, "keep:throne")

        assertEquals(85, npc.damage)
        assertEquals(65, npc.accuracy)
        assertEquals(40, npc.defense)
        assertEquals(20, npc.evasion)
    }

    @Test
    fun `sprite override set on phase transition`() {
        val npc = createBossNpc(maxHp = 1000, phases = singlePhase(0.5))
        val events = mutableListOf<CombatEvent>()

        npc.currentHp = 400
        checkPhaseTransition(npc, events, "keep:throne")

        assertEquals("npc:stormcrown_phase2", npc.spriteOverride)
    }

    @Test
    fun `multi-phase transitions work in sequence`() {
        val phases = listOf(
            BossPhaseData(name = "Phase 2", hpThresholdPercent = 0.6, damage = 60),
            BossPhaseData(name = "Phase 3", hpThresholdPercent = 0.3, damage = 90)
        )
        val npc = createBossNpc(maxHp = 1000, damage = 40, phases = phases)
        val events = mutableListOf<CombatEvent>()

        npc.currentHp = 500
        checkPhaseTransition(npc, events, "keep:throne")
        assertEquals(1, events.size)
        assertEquals("Phase 2", (events[0] as CombatEvent.NpcPhaseShift).phaseName)
        assertEquals(600, npc.currentHp)
        assertEquals(60, npc.damage)
        assertEquals(1, npc.currentPhase)

        npc.currentHp = 200
        checkPhaseTransition(npc, events, "keep:throne")
        assertEquals(2, events.size)
        assertEquals("Phase 3", (events[1] as CombatEvent.NpcPhaseShift).phaseName)
        assertEquals(300, npc.currentHp)
        assertEquals(90, npc.damage)
        assertEquals(2, npc.currentPhase)
    }

    @Test
    fun `burst damage does not skip phase`() {
        val npc = createBossNpc(maxHp = 1000, phases = singlePhase(0.5))
        val events = mutableListOf<CombatEvent>()

        npc.currentHp = -100
        checkPhaseTransition(npc, events, "keep:throne")

        assertEquals(500, npc.currentHp)
        assertTrue(npc.currentHp > 0)
    }

    @Test
    fun `no-phase NPCs unaffected`() {
        val npc = createBossNpc(maxHp = 100, currentHp = 10, phases = emptyList())
        val events = mutableListOf<CombatEvent>()

        checkPhaseTransition(npc, events, "keep:throne")

        assertTrue(events.isEmpty())
        assertEquals(10, npc.currentHp)
    }

    @Test
    fun `kill after last phase works normally`() {
        val npc = createBossNpc(maxHp = 1000, phases = singlePhase(0.5))
        val events = mutableListOf<CombatEvent>()

        npc.currentHp = 400
        checkPhaseTransition(npc, events, "keep:throne")
        assertEquals(500, npc.currentHp)
        assertEquals(1, npc.currentPhase)

        npc.currentHp = -50
        checkPhaseTransition(npc, events, "keep:throne")
        assertEquals(1, events.size)
        assertTrue(npc.currentHp <= 0)
    }

    @Test
    fun `phase transition does not fire above threshold`() {
        val npc = createBossNpc(maxHp = 1000, currentHp = 600, phases = singlePhase(0.5))
        val events = mutableListOf<CombatEvent>()

        checkPhaseTransition(npc, events, "keep:throne")

        assertTrue(events.isEmpty())
        assertEquals(600, npc.currentHp)
        assertEquals(0, npc.currentPhase)
    }
}
