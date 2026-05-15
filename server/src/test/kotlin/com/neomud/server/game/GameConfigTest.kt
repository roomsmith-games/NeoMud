package com.neomud.server.game

import com.neomud.server.world.ItemCatalog
import com.neomud.shared.model.Item
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GameConfigTest {

    @Test
    fun testGraceTicksPositive() {
        assertTrue(GameConfig.Combat.GRACE_TICKS > 0, "Grace period should be at least 1 tick")
    }

    @Test
    fun testCombatConstants() {
        assertEquals(5, GameConfig.Combat.MIN_HIT_CHANCE)
        assertEquals(95, GameConfig.Combat.MAX_HIT_CHANCE)
        assertEquals(50, GameConfig.Combat.BASE_HIT_CHANCE)
        assertEquals(3, GameConfig.Combat.UNARMED_DAMAGE_RANGE)
        assertEquals(1.5, GameConfig.Combat.CRIT_DAMAGE_MULTIPLIER)
        assertEquals(3, GameConfig.Combat.BACKSTAB_DAMAGE_MULTIPLIER)
    }

    @Test
    fun testProgressionConstants() {
        assertEquals(30, GameConfig.Progression.MAX_LEVEL)
        assertEquals(100, GameConfig.Progression.XP_BASE_MULTIPLIER)
        assertEquals(100L, GameConfig.Progression.XP_MINIMUM)
        assertTrue(GameConfig.Progression.DEATH_XP_LOSS_PERCENT in 0.0..1.0)
    }

    @Test
    fun testSkillConstants() {
        assertTrue(GameConfig.Skills.BASH_DAMAGE_RANGE > 0)
        assertTrue(GameConfig.Skills.BASH_STUN_CHANCE in 1..100)
        assertTrue(GameConfig.Skills.BASH_COOLDOWN_TICKS > 0)
        assertTrue(GameConfig.Skills.KICK_DAMAGE_RANGE > 0)
        assertTrue(GameConfig.Skills.KICK_COOLDOWN_TICKS > 0)
    }

    @Test
    fun testHealStatDivisorLargerThanDamage() {
        // Heal spells should scale more slowly than damage spells (larger divisor = less stat contribution)
        assertTrue(GameConfig.Skills.HEAL_STAT_DIVISOR > GameConfig.Skills.SPELL_POWER_STAT_DIVISOR,
            "HEAL_STAT_DIVISOR should be larger than SPELL_POWER_STAT_DIVISOR for tighter heal scaling")
    }

    @Test
    fun testNpcConstants() {
        assertTrue(GameConfig.Npc.WANDER_MOVE_TICKS > 0)
        assertTrue(GameConfig.Npc.PATROL_MOVE_TICKS > 0)
        assertTrue(GameConfig.Npc.PURSUIT_MAX_TICKS > 0)
        assertTrue(GameConfig.Npc.PURSUIT_MOVE_TICKS > 0)
    }

    @Test
    fun testTrailConstants() {
        assertTrue(GameConfig.Trails.MAX_ENTRIES_PER_ROOM > 0)
        assertTrue(GameConfig.Trails.LIFETIME_MS > 0)
        assertTrue(GameConfig.Trails.STALENESS_PENALTY_MAX > 0)
    }

    @Test
    fun testXpModifiersOrdered() {
        // Higher level difference should give more XP
        assertTrue(GameConfig.Progression.XP_MOD_5_ABOVE > GameConfig.Progression.XP_MOD_3_ABOVE)
        assertTrue(GameConfig.Progression.XP_MOD_3_ABOVE > GameConfig.Progression.XP_MOD_1_ABOVE)
        assertTrue(GameConfig.Progression.XP_MOD_1_ABOVE > GameConfig.Progression.XP_MOD_SAME)
        assertTrue(GameConfig.Progression.XP_MOD_SAME > GameConfig.Progression.XP_MOD_2_BELOW)
        assertTrue(GameConfig.Progression.XP_MOD_2_BELOW > GameConfig.Progression.XP_MOD_4_BELOW)
        assertTrue(GameConfig.Progression.XP_MOD_4_BELOW > GameConfig.Progression.XP_MOD_5_PLUS_BELOW)
    }

    @Test
    fun testStarterEquipmentWeaponMapping() {
        // Sword classes
        assertEquals("item:iron_sword", GameConfig.StarterEquipment.weaponForClass("WARRIOR"))
        assertEquals("item:iron_sword", GameConfig.StarterEquipment.weaponForClass("PALADIN"))
        assertEquals("item:iron_sword", GameConfig.StarterEquipment.weaponForClass("WITCHHUNTER"))
        assertEquals("item:iron_sword", GameConfig.StarterEquipment.weaponForClass("CLERIC"))
        assertEquals("item:iron_sword", GameConfig.StarterEquipment.weaponForClass("WARLOCK"))
        // Dagger classes
        assertEquals("item:rustic_dagger", GameConfig.StarterEquipment.weaponForClass("THIEF"))
        assertEquals("item:rustic_dagger", GameConfig.StarterEquipment.weaponForClass("NINJA"))
        assertEquals("item:rustic_dagger", GameConfig.StarterEquipment.weaponForClass("MISSIONARY"))
        assertEquals("item:rustic_dagger", GameConfig.StarterEquipment.weaponForClass("BARD"))
        assertEquals("item:rustic_dagger", GameConfig.StarterEquipment.weaponForClass("GYPSY"))
        // Staff classes
        assertEquals("item:wooden_staff", GameConfig.StarterEquipment.weaponForClass("MAGE"))
        assertEquals("item:wooden_staff", GameConfig.StarterEquipment.weaponForClass("DRUID"))
        assertEquals("item:wooden_staff", GameConfig.StarterEquipment.weaponForClass("PRIEST"))
        assertEquals("item:wooden_staff", GameConfig.StarterEquipment.weaponForClass("MYSTIC"))
        // Bow classes
        assertEquals("item:short_bow", GameConfig.StarterEquipment.weaponForClass("RANGER"))
    }

    @Test
    fun testStarterEquipmentCaseInsensitive() {
        assertEquals("item:iron_sword", GameConfig.StarterEquipment.weaponForClass("warrior"))
        assertEquals("item:rustic_dagger", GameConfig.StarterEquipment.weaponForClass("Thief"))
        assertEquals("item:wooden_staff", GameConfig.StarterEquipment.weaponForClass("mage"))
    }

    @Test
    fun testStarterEquipmentFallback() {
        // Unknown class gets dagger as fallback
        assertEquals("item:rustic_dagger", GameConfig.StarterEquipment.weaponForClass("UNKNOWN"))
    }

    @Test
    fun testStarterEquipmentConstants() {
        assertEquals(25, GameConfig.StarterEquipment.STARTING_COPPER)
        assertEquals("item:leather_chest", GameConfig.StarterEquipment.ARMOR_ITEM_ID)
        assertEquals("chest", GameConfig.StarterEquipment.ARMOR_SLOT)
    }

    @Test
    fun testResolveWeapon_defaultWorldItems() {
        val catalog = ItemCatalog(listOf(
            Item("item:iron_sword", "Iron Sword", "", "weapon", slot = "weapon", damageBonus = 8, value = 50, levelRequirement = 1),
            Item("item:rustic_dagger", "Rustic Dagger", "", "weapon", slot = "weapon", damageBonus = 5, value = 25, levelRequirement = 1)
        ))
        assertEquals("item:iron_sword", GameConfig.StarterEquipment.resolveWeapon("WARRIOR", catalog))
        assertEquals("item:rustic_dagger", GameConfig.StarterEquipment.resolveWeapon("THIEF", catalog))
    }

    @Test
    fun testResolveWeapon_fallbackToCheapestByCategory() {
        val catalog = ItemCatalog(listOf(
            Item("item:iron_pipe", "Iron Pipe", "", "weapon", slot = "weapon", damageBonus = 7, value = 40, levelRequirement = 1),
            Item("item:brass_dagger", "Brass Dagger", "", "weapon", slot = "weapon", damageBonus = 5, value = 20, levelRequirement = 1),
            Item("item:scrap_staff", "Scrap Staff", "", "weapon", slot = "weapon", damageBonus = 3, value = 15, levelRequirement = 1)
        ))
        assertEquals("item:brass_dagger", GameConfig.StarterEquipment.resolveWeapon("THIEF", catalog))
        assertEquals("item:scrap_staff", GameConfig.StarterEquipment.resolveWeapon("MAGE", catalog))
    }

    @Test
    fun testResolveWeapon_fallbackToCheapestAny() {
        val catalog = ItemCatalog(listOf(
            Item("item:iron_pipe", "Iron Pipe", "", "weapon", slot = "weapon", damageBonus = 7, value = 40, levelRequirement = 1)
        ))
        assertEquals("item:iron_pipe", GameConfig.StarterEquipment.resolveWeapon("WARRIOR", catalog),
            "When no sword-category weapon exists, fall back to cheapest weapon")
    }

    @Test
    fun testResolveWeapon_emptyReturnsNull() {
        val catalog = ItemCatalog(emptyList())
        assertNull(GameConfig.StarterEquipment.resolveWeapon("WARRIOR", catalog))
    }

    @Test
    fun testResolveArmor_defaultWorldItems() {
        val catalog = ItemCatalog(listOf(
            Item("item:leather_chest", "Leather Chest", "", "armor", slot = "chest", armorValue = 3, value = 30, levelRequirement = 1)
        ))
        assertEquals("item:leather_chest", GameConfig.StarterEquipment.resolveArmor(catalog))
    }

    @Test
    fun testResolveArmor_fallbackToCheapestChest() {
        val catalog = ItemCatalog(listOf(
            Item("item:scrap_vest", "Scrap Vest", "", "armor", slot = "chest", armorValue = 2, value = 20, levelRequirement = 1),
            Item("item:tempered_cuirass", "Tempered Cuirass", "", "armor", slot = "chest", armorValue = 8, value = 200, levelRequirement = 5)
        ))
        assertEquals("item:scrap_vest", GameConfig.StarterEquipment.resolveArmor(catalog))
    }

    @Test
    fun testResolveArmor_emptyReturnsNull() {
        val catalog = ItemCatalog(emptyList())
        assertNull(GameConfig.StarterEquipment.resolveArmor(catalog))
    }

    @Test
    fun testCpTierProgression() {
        assertTrue(GameConfig.Progression.CP_PER_LEVEL_LOW < GameConfig.Progression.CP_PER_LEVEL_MID)
        assertTrue(GameConfig.Progression.CP_PER_LEVEL_MID < GameConfig.Progression.CP_PER_LEVEL_HIGH)
        assertTrue(GameConfig.Progression.CP_TIER_2_LEVEL < GameConfig.Progression.CP_TIER_3_LEVEL)
    }
}
