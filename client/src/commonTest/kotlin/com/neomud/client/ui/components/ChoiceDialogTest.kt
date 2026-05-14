package com.neomud.client.ui.components

import androidx.compose.ui.test.*
import com.neomud.client.testutil.ComposeTestBase
import com.neomud.client.testutil.TestThemeWrapper
import com.neomud.shared.protocol.ServerMessage
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ChoiceDialogTest : ComposeTestBase() {

    private val prompt = ServerMessage.ChoicePrompt(
        featureId = "fracture_choice",
        label = "The First Seal",
        question = "Choose your path.",
        options = listOf(
            ServerMessage.ChoiceOption("seal", "Restore the Seal"),
            ServerMessage.ChoiceOption("sunder", "Shatter the Seal")
        )
    )

    @Test
    fun renders_question_and_label() = runComposeUiTest {
        setContent {
            TestThemeWrapper {
                ChoiceDialog(prompt = prompt, onChoice = {}, onDismiss = {})
            }
        }

        onNodeWithText("The First Seal").assertIsDisplayed()
        onNodeWithText("Choose your path.").assertIsDisplayed()
    }

    @Test
    fun renders_all_options() = runComposeUiTest {
        setContent {
            TestThemeWrapper {
                ChoiceDialog(prompt = prompt, onChoice = {}, onDismiss = {})
            }
        }

        onNodeWithText("Restore the Seal").assertIsDisplayed()
        onNodeWithText("Shatter the Seal").assertIsDisplayed()
        onNodeWithTag("choiceOption_seal").assertIsDisplayed()
        onNodeWithTag("choiceOption_sunder").assertIsDisplayed()
    }

    @Test
    fun clicking_option_fires_onChoice() = runComposeUiTest {
        var chosen: String? = null
        setContent {
            TestThemeWrapper {
                ChoiceDialog(prompt = prompt, onChoice = { chosen = it }, onDismiss = {})
            }
        }

        onNodeWithTag("choiceOption_seal").performClick()
        assertEquals("seal", chosen)
    }

    @Test
    fun clicking_cancel_fires_onDismiss() = runComposeUiTest {
        var dismissed = false
        setContent {
            TestThemeWrapper {
                ChoiceDialog(prompt = prompt, onChoice = {}, onDismiss = { dismissed = true })
            }
        }

        onNodeWithTag("choiceCancel").performClick()
        assertEquals(true, dismissed)
    }
}
