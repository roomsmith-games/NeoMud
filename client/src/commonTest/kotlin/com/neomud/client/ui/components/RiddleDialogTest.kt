package com.neomud.client.ui.components

import androidx.compose.ui.test.*
import com.neomud.client.testutil.ComposeTestBase
import com.neomud.client.testutil.TestThemeWrapper
import com.neomud.shared.protocol.ServerMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class RiddleDialogTest : ComposeTestBase() {

    private fun prompt(
        featureId: String = "door_riddle",
        label: String = "inscribed archway",
        question: String = "What has keys but no locks?",
        hint: String? = "Think musically."
    ) = ServerMessage.RiddlePrompt(
        featureId = featureId,
        label = label,
        question = question,
        hint = hint
    )

    @Test
    fun dialog_rendersQuestionAndHint() = runComposeUiTest {
        setContent {
            TestThemeWrapper {
                RiddleDialog(
                    prompt = prompt(),
                    onSubmit = {},
                    onDismiss = {}
                )
            }
        }

        onNodeWithText("inscribed archway").assertIsDisplayed()
        onNodeWithText("What has keys but no locks?").assertIsDisplayed()
        onNodeWithText("Think musically.").assertIsDisplayed()
    }

    @Test
    fun dialog_emitsSubmitCallback_onAnswerSubmit() = runComposeUiTest {
        var submitted: String? = null
        setContent {
            TestThemeWrapper {
                RiddleDialog(
                    prompt = prompt(),
                    onSubmit = { submitted = it },
                    onDismiss = {}
                )
            }
        }

        onNodeWithTag("riddleInput").performTextInput("piano")
        onNodeWithTag("riddleSubmit").performClick()
        assertEquals("piano", submitted)
    }

    @Test
    fun dialog_emitsCancelCallback_onCancelTap() = runComposeUiTest {
        var dismissed = false
        setContent {
            TestThemeWrapper {
                RiddleDialog(
                    prompt = prompt(),
                    onSubmit = {},
                    onDismiss = { dismissed = true }
                )
            }
        }

        onNodeWithTag("riddleCancel").performClick()
        assertTrue(dismissed)
    }

    @Test
    fun dialog_submitDisabledWhenEmpty() = runComposeUiTest {
        var submitted: String? = null
        setContent {
            TestThemeWrapper {
                RiddleDialog(
                    prompt = prompt(),
                    onSubmit = { submitted = it },
                    onDismiss = {}
                )
            }
        }

        onNodeWithTag("riddleSubmit").performClick()
        assertEquals(null, submitted, "Submit should not fire with empty input")
    }
}
