package com.neomud.client.ui.screens

import androidx.compose.ui.test.*
import com.neomud.client.network.ConnectionState
import com.neomud.client.testutil.ComposeTestBase
import com.neomud.client.testutil.TestThemeWrapper
import com.neomud.client.testutil.installTestCoil
import com.neomud.client.testutil.resetTestCoil
import com.neomud.client.viewmodel.AuthState
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class LoginScreenTest : ComposeTestBase() {

    @BeforeTest
    fun setup() { installTestCoil() }

    @AfterTest
    fun teardown() { resetTestCoil() }

    @Test
    fun shows_host_and_port_when_disconnected() = runComposeUiTest {
        setContent {
            TestThemeWrapper {
                LoginScreen(
                    connectionState = ConnectionState.DISCONNECTED,
                    authState = AuthState.Idle,
                    connectionError = null,
                    onConnect = { _, _ -> },
                    onLogin = { _ -> },
                    onNavigateToRegister = {},
                    onClearError = {}
                )
            }
        }

        // Server Host and Port fields should be visible (dev config has showServerConfig = true by default)
        onNodeWithText("Connect to Server").assertIsDisplayed()
        onNodeWithText("Connect").assertIsDisplayed()
    }

    @Test
    fun connect_button_fires_callback_with_host_and_port() = runComposeUiTest {
        var connectedHost = ""
        var connectedPort = 0

        setContent {
            TestThemeWrapper {
                LoginScreen(
                    connectionState = ConnectionState.DISCONNECTED,
                    authState = AuthState.Idle,
                    connectionError = null,
                    onConnect = { host, port ->
                        connectedHost = host
                        connectedPort = port
                    },
                    onLogin = { _ -> },
                    onNavigateToRegister = {},
                    onClearError = {}
                )
            }
        }

        onNodeWithText("Connect").performClick()
        // Default values from serverConfig should be used
        assertTrue(connectedHost.isNotEmpty())
        assertTrue(connectedPort > 0)
    }

    @Test
    fun shows_login_fields_when_connected() = runComposeUiTest {
        setContent {
            TestThemeWrapper {
                LoginScreen(
                    connectionState = ConnectionState.CONNECTED,
                    authState = AuthState.Idle,
                    connectionError = null,
                    onConnect = { _, _ -> },
                    onLogin = { _ -> },
                    onNavigateToRegister = {},
                    onClearError = {}
                )
            }
        }

        // Should show auth fields when connected (Create Account link is unique to auth phase)
        onNodeWithText("Create Character").assertIsDisplayed()
        // "Login" appears as both header and button — verify at least one exists
        onAllNodesWithText("Login").assertCountEquals(2)
    }

    @Test
    fun shows_connecting_spinner_when_connecting() = runComposeUiTest {
        setContent {
            TestThemeWrapper {
                LoginScreen(
                    connectionState = ConnectionState.CONNECTING,
                    authState = AuthState.Idle,
                    connectionError = null,
                    onConnect = { _, _ -> },
                    onLogin = { _ -> },
                    onNavigateToRegister = {},
                    onClearError = {}
                )
            }
        }

        onNodeWithText("Connecting...").assertIsDisplayed()
    }

    @Test
    fun shows_error_message_when_connection_error() = runComposeUiTest {
        setContent {
            TestThemeWrapper {
                LoginScreen(
                    connectionState = ConnectionState.DISCONNECTED,
                    authState = AuthState.Idle,
                    connectionError = "Connection refused",
                    onConnect = { _, _ -> },
                    onLogin = { _ -> },
                    onNavigateToRegister = {},
                    onClearError = {}
                )
            }
        }

        onNodeWithText("Connection failed: Connection refused").assertIsDisplayed()
    }

    @Test
    fun shows_auth_error_message() = runComposeUiTest {
        setContent {
            TestThemeWrapper {
                LoginScreen(
                    connectionState = ConnectionState.CONNECTED,
                    authState = AuthState.Error("Invalid credentials"),
                    connectionError = null,
                    onConnect = { _, _ -> },
                    onLogin = { _ -> },
                    onNavigateToRegister = {},
                    onClearError = {}
                )
            }
        }

        onNodeWithText("Invalid credentials").assertExists()
    }
}
