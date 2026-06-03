package com.neomud.server.world

import com.neomud.server.defaultWorldSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SpellCatalogTest {

    private fun load() = SpellCatalog.load(defaultWorldSource())

    @Test
    fun testLoadSpellCatalog() {
        val catalog = load()
        assertTrue(catalog.spellCount >= 23, "Should load at least 23 spells, got ${catalog.spellCount}")
    }

    @Test
    fun testGetSpellById() {
        val catalog = load()
        val spell = catalog.getSpell("MAGIC_MISSILE")
        assertNotNull(spell)
        assertEquals("Magic Missile", spell.name)
        assertEquals("mage", spell.school)
        assertEquals(4, spell.manaCost)
    }

    @Test
    fun testGetNonexistentSpell() {
        val catalog = load()
        assertNull(catalog.getSpell("NONEXISTENT"))
    }

    @Test
    fun testGetAllSpells() {
        val catalog = load()
        val all = catalog.getAllSpells()
        assertEquals(catalog.spellCount, all.size)
        assertTrue(all.any { it.id == "FIREBALL" })
        assertTrue(all.any { it.id == "MINOR_HEAL" })
    }

    @Test
    fun testGetSpellsForSchool() {
        val catalog = load()
        val mageSpells = catalog.getSpellsForSchool("mage")
        assertEquals(4, mageSpells.size)
        assertTrue(mageSpells.all { it.school == "mage" })

        val priestSpells = catalog.getSpellsForSchool("priest")
        assertEquals(9, priestSpells.size)

        val druidSpells = catalog.getSpellsForSchool("druid")
        assertEquals(6, druidSpells.size)

        val kaiSpells = catalog.getSpellsForSchool("kai")
        assertEquals(5, kaiSpells.size)

        val bardSpells = catalog.getSpellsForSchool("bard")
        assertEquals(8, bardSpells.size)
    }

    @Test
    fun testSpellProperties() {
        val catalog = load()
        val fireball = catalog.getSpell("FIREBALL")
        assertNotNull(fireball)
        assertEquals(18, fireball.manaCost)
        assertEquals(5, fireball.levelRequired)
        assertEquals(45, fireball.basePower)
        assertEquals("intellect", fireball.primaryStat)
    }
}
