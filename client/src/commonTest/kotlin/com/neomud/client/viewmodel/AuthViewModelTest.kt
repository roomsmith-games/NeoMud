package com.neomud.client.viewmodel

import com.neomud.client.network.ConnectionState
import com.neomud.client.testutil.FakeGameConnection
import com.neomud.client.testutil.TestData
import com.neomud.shared.NeoMudVersion
import com.neomud.shared.protocol.ClientMessage
import com.neomud.shared.protocol.ServerMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private lateinit var fakeConnection: FakeGameConnection
    private lateinit var viewModel: AuthViewModel
    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeConnection = FakeGameConnection()
        viewModel = AuthViewModel(wsClient = fakeConnection)
    }

    @AfterTest
    fun teardown() {
        Dispatchers.resetMain()
    }

    // ─── serverBaseUrl ───────────────────────────────────

    @Test
    fun serverBaseUrl_uses_http_when_tls_disabled() {
        viewModel.connect("localhost", 8080, useTls = false)
        assertEquals("http://localhost:8080", viewModel.serverBaseUrl)
    }

    @Test
    fun serverBaseUrl_uses_https_when_tls_enabled() {
        viewModel.connect("play.neomud.com", 443, useTls = true)
        assertEquals("https://play.neomud.com:443", viewModel.serverBaseUrl)
    }

    // ─── connect ─────────────────────────────────────────

    @Test
    fun connect_passes_useTls_to_connection() {
        viewModel.connect("example.com", 9090, useTls = true)

        assertEquals(1, fakeConnection.connectCalls.size)
        val call = fakeConnection.connectCalls.first()
        assertEquals("example.com", call.host)
        assertEquals(9090, call.port)
        assertTrue(call.useTls)
    }

    @Test
    fun connect_passes_false_tls_by_default() {
        viewModel.connect("localhost", 8080)

        assertEquals(1, fakeConnection.connectCalls.size)
        val call = fakeConnection.connectCalls.first()
        assertEquals(false, call.useTls)
    }

    // ─── login ───────────────────────────────────────────

    @Test
    fun login_sends_login_message() = runTest {
        viewModel.login("testuser")
        advanceUntilIdle()

        assertEquals(1, fakeConnection.sentMessages.size)
        val msg = fakeConnection.sentMessages.first()
        assertIs<ClientMessage.Login>(msg)
        assertEquals("testuser", msg.characterName)
    }

    @Test
    fun login_sets_loading_state() = runTest {
        viewModel.login("testuser")
        assertEquals(AuthState.Loading, viewModel.authState.value)
    }

    @Test
    fun login_sets_error_when_send_fails() = runTest {
        fakeConnection.sendResult = false
        viewModel.login("testuser")
        advanceUntilIdle()

        assertIs<AuthState.Error>(viewModel.authState.value)
    }

    // ─── server message handling ─────────────────────────

    @Test
    fun loginOk_sets_loggedIn_state() = runTest {
        val player = TestData.player(name = "Hero")
        fakeConnection.receiveMessage(ServerMessage.LoginOk(player))
        advanceUntilIdle()

        val state = viewModel.authState.value
        assertIs<AuthState.LoggedIn>(state)
        assertEquals("Hero", state.player.name)
    }

    @Test
    fun authError_sets_error_state() = runTest {
        fakeConnection.receiveMessage(ServerMessage.AuthError("Invalid credentials"))
        advanceUntilIdle()

        val state = viewModel.authState.value
        assertIs<AuthState.Error>(state)
        assertEquals("Invalid credentials", state.message)
    }

    // ─── logout ──────────────────────────────────────────

    @Test
    fun logout_disconnects_and_resets_state() {
        viewModel.logout()

        assertEquals(1, fakeConnection.disconnectCount)
        assertEquals(AuthState.Idle, viewModel.authState.value)
    }

    // ─── clearError ──────────────────────────────────────

    @Test
    fun clearError_resets_to_idle() = runTest {
        fakeConnection.receiveMessage(ServerMessage.AuthError("some error"))
        advanceUntilIdle()

        assertIs<AuthState.Error>(viewModel.authState.value)
        viewModel.clearError()
        assertEquals(AuthState.Idle, viewModel.authState.value)
    }

    // ─── ServerHello handshake ───────────────────────────

    @Test
    fun serverHello_updates_serverInfo() = runTest {
        fakeConnection.receiveMessage(ServerMessage.ServerHello(
            engineVersion = "0.1.0.0",
            protocolVersion = 1,
            worldName = "Test World",
            worldVersion = "0.5.0"
        ))
        advanceUntilIdle()

        val info = viewModel.serverInfo.value
        assertEquals("0.1.0.0", info.engineVersion)
        assertEquals(1, info.protocolVersion)
        assertEquals("Test World", info.worldName)
        assertEquals("0.5.0", info.worldVersion)
    }

    @Test
    fun serverHello_triggers_clientHello_response() = runTest {
        fakeConnection.receiveMessage(ServerMessage.ServerHello(
            engineVersion = "0.1.0.0",
            protocolVersion = 1,
            worldName = "Test World",
            worldVersion = "0.5.0"
        ))
        advanceUntilIdle()

        val helloMsg = fakeConnection.sentMessages.filterIsInstance<ClientMessage.ClientHello>()
        assertEquals(1, helloMsg.size, "Should send exactly one ClientHello")
        assertEquals(NeoMudVersion.ENGINE_VERSION, helloMsg.first().clientVersion)
        assertEquals(NeoMudVersion.PROTOCOL_VERSION, helloMsg.first().protocolVersion)
    }
}
