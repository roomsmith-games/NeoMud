package com.neomud.server.game.progression

import com.neomud.shared.model.Stats
import kotlin.test.Test
import kotlin.test.assertEquals

class PerceptionTest {
    @Test
    fun `compute averages intellect and agility`() {
        val stats = Stats(intellect = 60, agility = 40)
        assertEquals(50, Perception.compute(stats))
    }

    @Test
    fun `compute ignores other stats`() {
        val a = Stats(intellect = 70, agility = 30, strength = 100, health = 100, willpower = 100, charm = 100)
        val b = Stats(intellect = 70, agility = 30, strength = 0, health = 0, willpower = 0, charm = 0)
        assertEquals(Perception.compute(a), Perception.compute(b))
    }

    @Test
    fun `compute rounds down for odd sums`() {
        val stats = Stats(intellect = 51, agility = 50)
        assertEquals(50, Perception.compute(stats))
    }

    @Test
    fun `compute returns zero for zero stats`() {
        assertEquals(0, Perception.compute(Stats(intellect = 0, agility = 0)))
    }

    @Test
    fun `roll combines compute, level half, and die`() {
        val stats = Stats(intellect = 40, agility = 40) // perception = 40
        val level = 10                                   // level / 2 = 5
        val result = Perception.roll(stats, level) { 13 }
        assertEquals(58, result) // 40 + 5 + 13
    }

    @Test
    fun `roll treats level zero as no level bonus`() {
        val stats = Stats(intellect = 20, agility = 20) // perception = 20
        val result = Perception.roll(stats, 0) { 1 }
        assertEquals(21, result) // 20 + 0 + 1
    }
}
