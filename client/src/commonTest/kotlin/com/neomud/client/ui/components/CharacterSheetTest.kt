package com.neomud.client.ui.components

import androidx.compose.ui.test.*
import com.neomud.client.testutil.ComposeTestBase
import com.neomud.client.testutil.TestData
import com.neomud.client.testutil.TestThemeWrapper
import com.neomud.shared.model.Coins
import com.neomud.shared.model.EffectType
import com.neomud.shared.model.Stats
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class CharacterSheetTest : ComposeTestBase() {

    private val classCatalog = mapOf("warrior" to TestData.characterClassDef())
    private val catalog = TestData.itemCatalog()
    private val skillCatalog = mapOf(
        "BASH" to TestData.skillDef(id = "BASH", name = "Bash"),
        "KICK" to TestData.skillDef(id = "KICK", name = "Kick")
    )

    @Test
    fun shows_character_name_and_level() = runComposeUiTest {
        val player = TestData.player(name = "Aragorn", level = 10, race = "human")
        setContent {
            TestThemeWrapper {
                CharacterSheet(
                    player = player,
                    classCatalog = classCatalog,
                    equipment = emptyMap(),
                    itemCatalog = catalog,
                    activeEffects = emptyList(),
                    playerCoins = Coins(),
                    onClose = {}
                )
            }
        }

        onNodeWithText("Aragorn", substring = true).assertExists()
        onNodeWithText("Character Sheet", substring = true).assertExists()
    }

    @Test
    fun shows_stats_grid() = runComposeUiTest {
        val player = TestData.player(stats = Stats(strength = 45, agility = 38, intellect = 25, willpower = 30, health = 40, charm = 20))
        setContent {
            TestThemeWrapper {
                CharacterSheet(
                    player = player,
                    classCatalog = classCatalog,
                    equipment = emptyMap(),
                    itemCatalog = catalog,
                    activeEffects = emptyList(),
                    playerCoins = Coins(),
                    onClose = {}
                )
            }
        }

        onNodeWithText("STR").assertExists()
        onNodeWithText("45").assertExists()
        onNodeWithText("AGI").assertExists()
        onNodeWithText("38").assertExists()
    }

    @Test
    fun shows_HP_and_MP_vital_bars() = runComposeUiTest {
        val player = TestData.player(currentHp = 75, maxHp = 100, currentMp = 20, maxMp = 50)
        setContent {
            TestThemeWrapper {
                CharacterSheet(
                    player = player,
                    classCatalog = classCatalog,
                    equipment = emptyMap(),
                    itemCatalog = catalog,
                    activeEffects = emptyList(),
                    playerCoins = Coins(),
                    onClose = {}
                )
            }
        }

        onNodeWithText("HP: 75/100").assertExists()
        onNodeWithText("MP: 20/50").assertExists()
    }

    @Test
    fun shows_equipment_slots_with_item_names() = runComposeUiTest {
        val equipment = mapOf("weapon" to "iron_sword")
        setContent {
            TestThemeWrapper {
                CharacterSheet(
                    player = TestData.player(),
                    classCatalog = classCatalog,
                    equipment = equipment,
                    itemCatalog = catalog,
                    activeEffects = emptyList(),
                    playerCoins = Coins(),
                    onClose = {}
                )
            }
        }

        onNodeWithText("Iron Sword").assertExists()
        onNodeWithText("-- empty --").assertExists()
    }

    @Test
    fun shows_skill_names_from_catalog() = runComposeUiTest {
        setContent {
            TestThemeWrapper {
                CharacterSheet(
                    player = TestData.player(),
                    classCatalog = classCatalog,
                    equipment = emptyMap(),
                    itemCatalog = catalog,
                    activeEffects = emptyList(),
                    playerCoins = Coins(),
                    skillCatalog = skillCatalog,
                    onClose = {}
                )
            }
        }

        onNodeWithText("Bash").assertExists()
        onNodeWithText("Kick").assertExists()
    }

    @Test
    fun shows_active_effects_with_tick_counts() = runComposeUiTest {
        val effects = listOf(
            TestData.activeEffect(name = "Poison", type = EffectType.POISON, remainingTicks = 5)
        )
        setContent {
            TestThemeWrapper {
                CharacterSheet(
                    player = TestData.player(),
                    classCatalog = classCatalog,
                    equipment = emptyMap(),
                    itemCatalog = catalog,
                    activeEffects = effects,
                    playerCoins = Coins(),
                    onClose = {}
                )
            }
        }

        onNodeWithText("Poison").assertExists()
        onNodeWithText("5 ticks").assertExists()
    }

    @Test
    fun shows_coins_section() = runComposeUiTest {
        setContent {
            TestThemeWrapper {
                CharacterSheet(
                    player = TestData.player(),
                    classCatalog = classCatalog,
                    equipment = emptyMap(),
                    itemCatalog = catalog,
                    activeEffects = emptyList(),
                    playerCoins = TestData.coins(gold = 5, silver = 20),
                    onClose = {}
                )
            }
        }

        onNodeWithText("5 GP").assertExists()
        onNodeWithText("20 SP").assertExists()
    }

    @Test
    fun shows_no_coins_message_when_empty() = runComposeUiTest {
        setContent {
            TestThemeWrapper {
                CharacterSheet(
                    player = TestData.player(),
                    classCatalog = classCatalog,
                    equipment = emptyMap(),
                    itemCatalog = catalog,
                    activeEffects = emptyList(),
                    playerCoins = Coins(),
                    onClose = {}
                )
            }
        }

        onNodeWithText("No coins").assertExists()
    }

    @Test
    fun close_button_fires_onClose() = runComposeUiTest {
        var closed = false
        setContent {
            TestThemeWrapper {
                CharacterSheet(
                    player = TestData.player(),
                    classCatalog = classCatalog,
                    equipment = emptyMap(),
                    itemCatalog = catalog,
                    activeEffects = emptyList(),
                    playerCoins = Coins(),
                    onClose = { closed = true }
                )
            }
        }

        onNodeWithTag("close_button").performClick()
        assertTrue(closed)
    }
}
