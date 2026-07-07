package com.neomud.client.viewmodel

import com.neomud.client.testutil.FakeGameConnection
import com.neomud.client.testutil.TestData
import com.neomud.shared.model.PartyMember
import com.neomud.shared.protocol.ServerMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelTest {

    private lateinit var fakeConnection: FakeGameConnection
    private lateinit var viewModel: GameViewModel
    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeConnection = FakeGameConnection()
        viewModel = GameViewModel(wsClient = fakeConnection)
        viewModel.startCollecting()
    }

    @AfterTest
    fun teardown() {
        Dispatchers.resetMain()
    }

    private suspend fun TestScope.loginAs(name: String, currentHp: Int = 80, maxHp: Int = 100) {
        advanceUntilIdle() // let startCollecting()'s coroutine subscribe before we emit
        fakeConnection.receiveMessage(ServerMessage.LoginOk(player = TestData.player(name = name, currentHp = currentHp, maxHp = maxHp)))
    }

    // ─── CombatHit HP isolation ───────────────────────────────────────────────

    @Test
    fun combatHit_updates_own_hp_when_local_player_is_defender() = runTest {
        loginAs("Alice", currentHp = 80, maxHp = 100)
        advanceUntilIdle()

        fakeConnection.receiveMessage(ServerMessage.CombatHit(
            attackerName = "Goblin",
            defenderName = "Alice",
            damage = 30,
            defenderHp = 50,
            defenderMaxHp = 100,
            isPlayerDefender = true,
            isMiss = false, isDodge = false, isParry = false, isBackstab = false,
            defenderId = "player:Alice"
        ))
        advanceUntilIdle()

        assertEquals(50, viewModel.player.value?.currentHp)
    }

    @Test
    fun combatHit_does_not_overwrite_own_hp_when_another_player_is_defender() = runTest {
        loginAs("Alice", currentHp = 80, maxHp = 100)
        advanceUntilIdle()

        // Sean takes damage — Alice's HP must not change
        fakeConnection.receiveMessage(ServerMessage.CombatHit(
            attackerName = "Goblin",
            defenderName = "Sean",
            damage = 79,
            defenderHp = 1,
            defenderMaxHp = 100,
            isPlayerDefender = true,
            isMiss = false, isDodge = false, isParry = false, isBackstab = false,
            defenderId = "player:Sean"
        ))
        advanceUntilIdle()

        assertEquals(80, viewModel.player.value?.currentHp, "Alice's HP must not be overwritten by Sean's CombatHit")
    }

    @Test
    fun combatHit_updates_party_member_hp_when_party_member_is_defender() = runTest {
        loginAs("Alice", currentHp = 80, maxHp = 100)
        advanceUntilIdle()

        // Put Sean in the party
        fakeConnection.receiveMessage(ServerMessage.PartyInfo(
            partyId = "party:1",
            leaderId = "Alice",
            members = listOf(
                PartyMember(name = "Alice", characterClass = "warrior", level = 5, currentHp = 80, maxHp = 100),
                PartyMember(name = "Sean", characterClass = "ranger", level = 5, currentHp = 90, maxHp = 100)
            )
        ))
        advanceUntilIdle()

        // Sean takes damage
        fakeConnection.receiveMessage(ServerMessage.CombatHit(
            attackerName = "Goblin",
            defenderName = "Sean",
            damage = 79,
            defenderHp = 1,
            defenderMaxHp = 100,
            isPlayerDefender = true,
            isMiss = false, isDodge = false, isParry = false, isBackstab = false,
            defenderId = "player:Sean"
        ))
        advanceUntilIdle()

        val sean = viewModel.partyMembers.value.find { it.name == "Sean" }
        assertEquals(1, sean?.currentHp, "Sean's party HUD HP should reflect the hit immediately")
        assertEquals(80, viewModel.player.value?.currentHp, "Alice's own HP must be unchanged")
    }

    @Test
    fun heal_then_party_combatHit_does_not_reset_healed_hp() = runTest {
        // Regression: #485 — temple heals player, then a CombatHit from another player
        // being attacked reset the healed player's HP bar back to 1
        loginAs("Alice", currentHp = 1, maxHp = 100)
        advanceUntilIdle()

        // Temple heals Alice to full
        fakeConnection.receiveMessage(ServerMessage.EffectTick(
            effectName = "HEAL",
            message = "You are fully healed.",
            newHp = 100,
            sound = "",
            newMp = -1
        ))
        advanceUntilIdle()
        assertEquals(100, viewModel.player.value?.currentHp)

        // Sean gets hit in the same room — Alice's HP must stay at 100
        fakeConnection.receiveMessage(ServerMessage.CombatHit(
            attackerName = "Goblin",
            defenderName = "Sean",
            damage = 99,
            defenderHp = 1,
            defenderMaxHp = 100,
            isPlayerDefender = true,
            isMiss = false, isDodge = false, isParry = false, isBackstab = false,
            defenderId = "player:Sean"
        ))
        advanceUntilIdle()

        assertEquals(100, viewModel.player.value?.currentHp, "Healed HP must not be reset by another player's CombatHit")
    }
}
