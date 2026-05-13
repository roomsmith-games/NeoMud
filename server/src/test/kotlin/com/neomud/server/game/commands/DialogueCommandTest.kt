package com.neomud.server.game.commands

import com.neomud.server.game.npc.NpcManager
import com.neomud.server.persistence.repository.InventoryRepository
import com.neomud.server.persistence.repository.PlayerFlagsRepository
import com.neomud.server.session.PlayerSession
import com.neomud.server.session.SessionManager
import com.neomud.server.world.ItemCatalog
import com.neomud.server.world.NpcData
import com.neomud.server.world.WorldGraph
import com.neomud.shared.model.Item
import com.neomud.shared.model.Player
import com.neomud.shared.model.Stats
import com.neomud.shared.protocol.MessageSerializer
import com.neomud.shared.protocol.ServerMessage
import io.ktor.websocket.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DialogueCommandTest {

    private val testRoomId = "town:square"
    private val testPlayerName = "DialogueTester"

    /** In-memory PlayerFlagsRepository — avoids needing a real DB for these tests. */
    private class FakePlayerFlagsRepository : PlayerFlagsRepository() {
        private val flags = mutableMapOf<Pair<String, String>, String>()
        override fun getFlag(playerName: String, flagKey: String): String? = flags[playerName to flagKey]
        override fun setFlag(playerName: String, flagKey: String, flagValue: String) {
            flags[playerName to flagKey] = flagValue
        }
        override fun clearFlag(playerName: String, flagKey: String) { flags.remove(playerName to flagKey) }
        override fun loadAllFlags(playerName: String): Map<String, String> =
            flags.filter { it.key.first == playerName }.mapKeys { it.key.second }
    }

    /** In-memory InventoryRepository — tracks add calls so we can assert grant behavior. */
    private class FakeInventoryRepository(private val acceptItems: Boolean = true) : InventoryRepository(
        com.neomud.server.world.ItemCatalog(emptyList())
    ) {
        val addedItems = mutableListOf<Pair<String, String>>()
        override fun addItem(playerName: String, itemId: String, quantity: Int): Boolean {
            return if (acceptItems) {
                addedItems.add(playerName to itemId)
                true
            } else {
                false
            }
        }
    }

    /** No-op stub — we just need the class to exist so DialogueCommand can call sendInventoryUpdate. */
    private class FakeInventoryCommand : InventoryCommand(
        FakeInventoryRepository(),
        com.neomud.server.world.ItemCatalog(emptyList()),
        com.neomud.server.persistence.repository.CoinRepository(),
        WorldGraph(),
        SessionManager()
    ) {
        var sendCount = 0
        override suspend fun sendInventoryUpdate(session: PlayerSession) {
            sendCount++
        }
    }

    private fun newSession(): PlayerSession {
        val outgoing = Channel<Frame>(Channel.UNLIMITED)
        val ws = object : WebSocketSession {
            override val coroutineContext: CoroutineContext get() = EmptyCoroutineContext
            override val incoming: Channel<Frame> get() = Channel()
            override val outgoing: Channel<Frame> get() = outgoing
            override val extensions: List<WebSocketExtension<*>> get() = emptyList()
            override var masking: Boolean = false
            override var maxFrameSize: Long = Long.MAX_VALUE
            override suspend fun flush() {}
            @Deprecated("Use cancel instead", replaceWith = ReplaceWith("cancel()"))
            override fun terminate() {}
        }
        val session = PlayerSession(ws)
        session.player = Player(
            name = testPlayerName,
            characterClass = "WARRIOR",
            race = "HUMAN",
            level = 1,
            currentHp = 50,
            maxHp = 50,
            currentMp = 0,
            maxMp = 0,
            currentRoomId = testRoomId,
            currentXp = 0,
            xpToNextLevel = 100,
            stats = Stats(strength = 15, agility = 10, intellect = 5, willpower = 5, health = 15, charm = 5)
        )
        session.playerName = testPlayerName
        session.currentRoomId = testRoomId
        return session
    }

    private fun drainMessages(session: PlayerSession): List<ServerMessage> {
        val ws = session.webSocketSession
        val outgoing = ws.outgoing as Channel<Frame>
        val messages = mutableListOf<ServerMessage>()
        while (true) {
            val frame = outgoing.tryReceive().getOrNull() ?: break
            if (frame is Frame.Text) {
                messages.add(MessageSerializer.decodeServerMessage(frame.readText()))
            }
        }
        return messages
    }

    private fun loadNpc(
        npcManager: NpcManager,
        dialogueScript: String,
        grantItemId: String = "",
        grantItemFlag: String = "",
        repeatDialogueScript: String = ""
    ) {
        npcManager.loadNpcs(
            listOf(
                NpcData(
                    id = "npc:test_lore",
                    name = "Old Wren",
                    description = "A weathered elder.",
                    startRoomId = testRoomId,
                    behaviorType = "quest",
                    dialogueScript = dialogueScript,
                    grantItemId = grantItemId,
                    grantItemFlag = grantItemFlag,
                    repeatDialogueScript = repeatDialogueScript
                ) to "town"
            )
        )
    }

    private fun buildCommand(
        npcManager: NpcManager,
        flags: PlayerFlagsRepository = FakePlayerFlagsRepository(),
        inv: InventoryRepository = FakeInventoryRepository(),
        invCmd: InventoryCommand = FakeInventoryCommand(),
        itemCatalog: ItemCatalog = ItemCatalog(emptyList())
    ): DialogueCommand = DialogueCommand(npcManager, SessionManager(), inv, flags, invCmd, itemCatalog)

    // --- Basic dialogue dispatch ---

    @Test
    fun sendsDialogueWhenScriptPresent() = runBlocking {
        val npcManager = NpcManager(WorldGraph(), emptyMap(), emptyMap())
        loadNpc(npcManager, dialogueScript = "Greetings, child.")
        val command = buildCommand(npcManager)
        val session = newSession()

        command.execute(session, "npc:test_lore")

        val dialogue = drainMessages(session).filterIsInstance<ServerMessage.NpcDialogue>().firstOrNull()
        assertNotNull(dialogue, "Should send NpcDialogue")
        assertEquals("npc:test_lore", dialogue.npcId)
        assertEquals("Old Wren", dialogue.npcName)
        assertEquals("Greetings, child.", dialogue.content)
    }

    @Test
    fun sendsSystemMessageWhenScriptBlank() = runBlocking {
        val npcManager = NpcManager(WorldGraph(), emptyMap(), emptyMap())
        loadNpc(npcManager, dialogueScript = "")
        val command = buildCommand(npcManager)
        val session = newSession()

        command.execute(session, "npc:test_lore")

        val sys = drainMessages(session).filterIsInstance<ServerMessage.SystemMessage>().firstOrNull()
        assertNotNull(sys)
        assertTrue(sys.message.contains("nothing to say"))
    }

    @Test
    fun sendsSystemMessageWhenNpcNotInRoom() = runBlocking {
        val npcManager = NpcManager(WorldGraph(), emptyMap(), emptyMap())
        // Load with a different roomId
        npcManager.loadNpcs(
            listOf(
                NpcData(
                    id = "npc:other",
                    name = "Stranger",
                    description = "",
                    startRoomId = "other:room",
                    behaviorType = "quest",
                    dialogueScript = "Hello"
                ) to "other"
            )
        )
        val command = buildCommand(npcManager)
        val session = newSession()  // sits in town:square

        command.execute(session, "npc:other")

        val sys = drainMessages(session).filterIsInstance<ServerMessage.SystemMessage>().firstOrNull()
        assertNotNull(sys)
        assertTrue(sys.message.contains("not here"))
    }

    // --- One-time item grant ---

    @Test
    fun firstInteractionGrantsItemAndSetsFlag() = runBlocking {
        val npcManager = NpcManager(WorldGraph(), emptyMap(), emptyMap())
        loadNpc(npcManager, "Take this.", grantItemId = "item:warden_token", grantItemFlag = "warden_token_received")
        val flags = FakePlayerFlagsRepository()
        val inv = FakeInventoryRepository()
        val invCmd = FakeInventoryCommand()
        val command = buildCommand(npcManager, flags, inv, invCmd)
        val session = newSession()

        command.execute(session, "npc:test_lore")

        assertEquals(1, inv.addedItems.size, "Item should have been added once")
        assertEquals(testPlayerName to "item:warden_token", inv.addedItems[0])
        assertEquals("1", flags.getFlag(testPlayerName, "warden_token_received"))
        assertEquals(1, invCmd.sendCount, "InventoryUpdate should be sent")
        val sysMessages = drainMessages(session).filterIsInstance<ServerMessage.SystemMessage>()
        assertTrue(sysMessages.any { it.message.contains("gives you") },
            "Grant notification should be sent")
    }

    @Test
    fun secondInteractionSendsDialogueButSkipsGrant() = runBlocking {
        val npcManager = NpcManager(WorldGraph(), emptyMap(), emptyMap())
        loadNpc(npcManager, "Take this.", grantItemId = "item:warden_token", grantItemFlag = "warden_token_received")
        val flags = FakePlayerFlagsRepository()
        val inv = FakeInventoryRepository()
        val invCmd = FakeInventoryCommand()
        val command = buildCommand(npcManager, flags, inv, invCmd)
        val session = newSession()

        command.execute(session, "npc:test_lore")
        command.execute(session, "npc:test_lore")

        // Item granted only on first call
        assertEquals(1, inv.addedItems.size, "Item should be granted only once across multiple interactions")

        // Both interactions sent dialogue
        val dialogues = drainMessages(session).filterIsInstance<ServerMessage.NpcDialogue>()
        assertEquals(2, dialogues.size, "Both interactions should send dialogue")
    }

    @Test
    fun grantSkippedWhenInventoryAddFails() = runBlocking {
        val npcManager = NpcManager(WorldGraph(), emptyMap(), emptyMap())
        loadNpc(npcManager, "Take this.", grantItemId = "item:warden_token", grantItemFlag = "warden_token_received")
        val flags = FakePlayerFlagsRepository()
        val inv = FakeInventoryRepository(acceptItems = false)  // simulate inventory full
        val command = buildCommand(npcManager, flags, inv)
        val session = newSession()

        command.execute(session, "npc:test_lore")

        // Flag NOT set so player can retry on next interaction
        assertEquals(null, flags.getFlag(testPlayerName, "warden_token_received"),
            "Flag must NOT be set when inventory add fails")
        // System message warned the player. Wording is intentionally neutral so it covers both
        // inventory-full and content-author-typo-on-grantItemId — addItem returns false for either.
        val sysMessages = drainMessages(session).filterIsInstance<ServerMessage.SystemMessage>()
        assertTrue(sysMessages.any { it.message.contains("slips away") || it.message.contains("no room") },
            "Player should see a 'gift could not be received' message")
    }

    @Test
    fun noGrantWhenGrantConfigEmpty() = runBlocking {
        val npcManager = NpcManager(WorldGraph(), emptyMap(), emptyMap())
        loadNpc(npcManager, "Just chatting.")  // no grantItemId
        val flags = FakePlayerFlagsRepository()
        val inv = FakeInventoryRepository()
        val invCmd = FakeInventoryCommand()
        val command = buildCommand(npcManager, flags, inv, invCmd)
        val session = newSession()

        command.execute(session, "npc:test_lore")

        assertEquals(0, inv.addedItems.size, "No item should be granted when grantItemId is blank")
        assertEquals(0, invCmd.sendCount, "InventoryUpdate should not be sent for dialogue-only NPCs")
    }

    // --- Repeat-visit dialogue ---

    @Test
    fun repeatVisitUsesRepeatDialogueScript() = runBlocking {
        val npcManager = NpcManager(WorldGraph(), emptyMap(), emptyMap())
        loadNpc(
            npcManager,
            dialogueScript = "Take this.",
            grantItemId = "item:warden_token",
            grantItemFlag = "warden_token_received",
            repeatDialogueScript = "You already have the token."
        )
        val flags = FakePlayerFlagsRepository()
        val inv = FakeInventoryRepository()
        val invCmd = FakeInventoryCommand()
        val command = buildCommand(npcManager, flags, inv, invCmd)
        val session = newSession()

        command.execute(session, "npc:test_lore")
        command.execute(session, "npc:test_lore")

        val dialogues = drainMessages(session).filterIsInstance<ServerMessage.NpcDialogue>()
        assertEquals(2, dialogues.size)
        assertEquals("Take this.", dialogues[0].content)
        assertEquals("You already have the token.", dialogues[1].content)
    }

    @Test
    fun repeatVisitFallsBackWhenRepeatBlank() = runBlocking {
        val npcManager = NpcManager(WorldGraph(), emptyMap(), emptyMap())
        loadNpc(
            npcManager,
            dialogueScript = "Take this.",
            grantItemId = "item:warden_token",
            grantItemFlag = "warden_token_received",
            repeatDialogueScript = ""
        )
        val flags = FakePlayerFlagsRepository()
        val inv = FakeInventoryRepository()
        val invCmd = FakeInventoryCommand()
        val command = buildCommand(npcManager, flags, inv, invCmd)
        val session = newSession()

        command.execute(session, "npc:test_lore")
        command.execute(session, "npc:test_lore")

        val dialogues = drainMessages(session).filterIsInstance<ServerMessage.NpcDialogue>()
        assertEquals(2, dialogues.size)
        assertEquals("Take this.", dialogues[0].content)
        assertEquals("Take this.", dialogues[1].content)
    }

    @Test
    fun grantSendsItemNameNotification() = runBlocking {
        val npcManager = NpcManager(WorldGraph(), emptyMap(), emptyMap())
        loadNpc(npcManager, "Take this.", grantItemId = "item:warden_token", grantItemFlag = "warden_token_received")
        val catalog = ItemCatalog(listOf(
            Item(id = "item:warden_token", name = "Warden's Token", description = "A glowing token.", type = "quest")
        ))
        val command = buildCommand(npcManager, itemCatalog = catalog)
        val session = newSession()

        command.execute(session, "npc:test_lore")

        val sysMessages = drainMessages(session).filterIsInstance<ServerMessage.SystemMessage>()
        assertTrue(sysMessages.any { it.message.contains("gives you Warden's Token") },
            "Grant notification should include the item's display name")
    }

    @Test
    fun grantNotificationUsesRawIdWhenItemNotInCatalog() = runBlocking {
        val npcManager = NpcManager(WorldGraph(), emptyMap(), emptyMap())
        loadNpc(npcManager, "Take this.", grantItemId = "item:unknown_thing", grantItemFlag = "unknown_received")
        val command = buildCommand(npcManager)
        val session = newSession()

        command.execute(session, "npc:test_lore")

        val sysMessages = drainMessages(session).filterIsInstance<ServerMessage.SystemMessage>()
        assertTrue(sysMessages.any { it.message.contains("gives you item:unknown_thing") },
            "Grant notification should fall back to raw item ID when not in catalog")
    }
}
