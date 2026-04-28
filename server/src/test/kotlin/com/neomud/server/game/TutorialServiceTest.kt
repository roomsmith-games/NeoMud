package com.neomud.server.game

import com.neomud.server.persistence.repository.DiscoveryRepository
import com.neomud.server.session.PlayerSession
import com.neomud.server.world.ClassCatalog
import com.neomud.server.world.WorldManifest
import com.neomud.shared.model.*
import com.neomud.shared.protocol.MessageSerializer
import com.neomud.shared.protocol.ServerMessage
import io.ktor.websocket.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.*

class TutorialServiceTest {

    private fun createTestSession(outgoing: Channel<Frame>): PlayerSession {
        return PlayerSession(object : WebSocketSession {
            override val coroutineContext: CoroutineContext get() = EmptyCoroutineContext
            override val incoming: Channel<Frame> get() = Channel()
            override val outgoing: Channel<Frame> get() = outgoing
            override val extensions: List<WebSocketExtension<*>> get() = emptyList()
            override var masking: Boolean = false
            override var maxFrameSize: Long = Long.MAX_VALUE
            override suspend fun flush() {}
            @Deprecated("Use cancel instead", replaceWith = ReplaceWith("cancel()"))
            override fun terminate() {}
        })
    }

    private fun drainMessages(channel: Channel<Frame>): List<ServerMessage> {
        val messages = mutableListOf<ServerMessage>()
        while (true) {
            val frame = channel.tryReceive().getOrNull() ?: break
            if (frame is Frame.Text) {
                messages.add(MessageSerializer.decodeServerMessage(frame.readText()))
            }
        }
        return messages
    }

    private fun createService(): TutorialService {
        val discoveryRepo = object : DiscoveryRepository() {
            override fun markTutorialSeen(playerName: String, tutorialKey: String) {
                // no-op in tests
            }
        }
        val classCatalog = ClassCatalog(listOf(
            CharacterClassDef(
                id = "MAGE", name = "Mage",
                description = "A wielder of arcane magic",
                minimumStats = Stats(),
                hpPerLevelMin = 3, hpPerLevelMax = 6,
                mpPerLevelMin = 5, mpPerLevelMax = 10,
                magicSchools = mapOf("EVOCATION" to 3),
                skills = emptyList()
            ),
            CharacterClassDef(
                id = "THIEF", name = "Thief",
                description = "A master of stealth",
                minimumStats = Stats(),
                hpPerLevelMin = 4, hpPerLevelMax = 8,
                mpPerLevelMin = 0, mpPerLevelMax = 0,
                magicSchools = emptyMap(),
                skills = listOf("SNEAK", "PICK_LOCK")
            ),
            CharacterClassDef(
                id = "WARRIOR", name = "Warrior",
                description = "A mighty fighter",
                minimumStats = Stats(),
                hpPerLevelMin = 5, hpPerLevelMax = 10,
                mpPerLevelMin = 0, mpPerLevelMax = 0,
                magicSchools = emptyMap(),
                skills = listOf("BASH")
            )
        ))
        return TutorialService(discoveryRepo, classCatalog)
    }

    private fun createServiceWithManifest(introScript: String, name: String = "Test World"): TutorialService {
        val discoveryRepo = object : DiscoveryRepository() {
            override fun markTutorialSeen(playerName: String, tutorialKey: String) {}
        }
        val classCatalog = ClassCatalog(emptyList())
        val manifest = WorldManifest(
            formatVersion = 1,
            name = name,
            author = "Test",
            version = "0.1.0",
            introScript = introScript
        )
        return TutorialService(discoveryRepo, classCatalog, manifest)
    }

    // --- World intro (#272) ---

    @Test
    fun worldIntroFiresOnceWhenScriptPresent() = runBlocking {
        val service = createServiceWithManifest("A thousand years ago, the world cracked.", name = "Wardens")
        val outgoing = Channel<Frame>(Channel.UNLIMITED)
        val session = createTestSession(outgoing)
        session.playerName = "TestPlayer"

        val first = service.trySendWorldIntro(session)
        assertTrue(first, "First send should fire")
        assertTrue("tut_world_intro" in session.seenTutorials)

        val tutorials = drainMessages(outgoing).filterIsInstance<ServerMessage.Tutorial>()
        assertEquals(1, tutorials.size)
        assertEquals("tut_world_intro", tutorials[0].key)
        assertEquals("Wardens", tutorials[0].title)
        assertTrue(tutorials[0].blocking)
        assertEquals("A thousand years ago, the world cracked.", tutorials[0].content)
    }

    @Test
    fun worldIntroSkipsAlreadySeen() = runBlocking {
        val service = createServiceWithManifest("Some lore.")
        val outgoing = Channel<Frame>(Channel.UNLIMITED)
        val session = createTestSession(outgoing)
        session.playerName = "TestPlayer"
        session.seenTutorials.add("tut_world_intro")

        val result = service.trySendWorldIntro(session)
        assertFalse(result, "Should not re-fire")
        assertTrue(drainMessages(outgoing).isEmpty())
    }

    @Test
    fun worldIntroNoOpWhenScriptEmpty() = runBlocking {
        val service = createServiceWithManifest("")
        val outgoing = Channel<Frame>(Channel.UNLIMITED)
        val session = createTestSession(outgoing)
        session.playerName = "TestPlayer"

        val result = service.trySendWorldIntro(session)
        assertFalse(result, "Empty introScript should be a no-op")
        assertFalse("tut_world_intro" in session.seenTutorials, "No-op should not mark seen")
        assertTrue(drainMessages(outgoing).isEmpty())
    }

    @Test
    fun worldIntroNoOpWhenManifestAbsent() = runBlocking {
        val service = createService() // no manifest
        val outgoing = Channel<Frame>(Channel.UNLIMITED)
        val session = createTestSession(outgoing)
        session.playerName = "TestPlayer"

        val result = service.trySendWorldIntro(session)
        assertFalse(result, "Missing manifest should be a no-op")
        assertTrue(drainMessages(outgoing).isEmpty())
    }

    @Test
    fun worldIntroFallsBackToWelcomeTitleWhenWorldNameBlank() = runBlocking {
        val service = createServiceWithManifest("Lore here.", name = "")
        val outgoing = Channel<Frame>(Channel.UNLIMITED)
        val session = createTestSession(outgoing)
        session.playerName = "TestPlayer"

        service.trySendWorldIntro(session)
        val tutorial = drainMessages(outgoing).filterIsInstance<ServerMessage.Tutorial>().first()
        assertEquals("Welcome", tutorial.title)
    }

    @Test
    fun testTrySendMarksAsSeen() = runBlocking {
        val service = createService()
        val outgoing = Channel<Frame>(Channel.UNLIMITED)
        val session = createTestSession(outgoing)
        session.playerName = "TestPlayer"

        val result = service.trySend(session, "welcome")
        assertTrue(result, "trySend should return true for unseen tutorial")
        assertTrue("welcome" in session.seenTutorials, "Tutorial should be marked as seen")

        val messages = drainMessages(outgoing)
        val tutorials = messages.filterIsInstance<ServerMessage.Tutorial>()
        assertEquals(1, tutorials.size)
        assertEquals("welcome", tutorials[0].key)
    }

    @Test
    fun testTrySendSkipsAlreadySeen() = runBlocking {
        val service = createService()
        val outgoing = Channel<Frame>(Channel.UNLIMITED)
        val session = createTestSession(outgoing)
        session.playerName = "TestPlayer"
        session.seenTutorials.add("welcome")

        val result = service.trySend(session, "welcome")
        assertFalse(result, "trySend should return false for already-seen tutorial")

        val messages = drainMessages(outgoing)
        assertTrue(messages.isEmpty(), "No messages should be sent for already-seen tutorial")
    }

    @Test
    fun testMultipleTutorialsFireImmediately() = runBlocking {
        val service = createService()
        val outgoing = Channel<Frame>(Channel.UNLIMITED)
        val session = createTestSession(outgoing)
        session.playerName = "TestPlayer"

        // All should fire immediately — no throttle
        service.trySend(session, "welcome")
        service.trySend(session, "tut_hostile_npc")
        service.trySend(session, "tut_low_hp")

        val messages = drainMessages(outgoing)
        val tutorials = messages.filterIsInstance<ServerMessage.Tutorial>()
        assertEquals(3, tutorials.size, "All three tutorials should fire immediately")
        assertEquals("welcome", tutorials[0].key)
        assertEquals("tut_hostile_npc", tutorials[1].key)
        assertEquals("tut_low_hp", tutorials[2].key)

        assertTrue("welcome" in session.seenTutorials)
        assertTrue("tut_hostile_npc" in session.seenTutorials)
        assertTrue("tut_low_hp" in session.seenTutorials)
    }

    @Test
    fun testSecondCallForSameKeyIsIgnored() = runBlocking {
        val service = createService()
        val outgoing = Channel<Frame>(Channel.UNLIMITED)
        val session = createTestSession(outgoing)
        session.playerName = "TestPlayer"

        assertTrue(service.trySend(session, "tut_hostile_npc"))
        assertFalse(service.trySend(session, "tut_hostile_npc"), "Second call for same key should be ignored")

        val messages = drainMessages(outgoing)
        assertEquals(1, messages.size, "Should only send once")
    }

    @Test
    fun testClassHasMagic() {
        val service = createService()
        assertTrue(service.classHasMagic("MAGE"), "Mage should have magic")
        assertFalse(service.classHasMagic("WARRIOR"), "Warrior should not have magic")
        assertFalse(service.classHasMagic("THIEF"), "Thief should not have magic")
    }

    @Test
    fun testClassHasStealth() {
        val service = createService()
        assertTrue(service.classHasStealth("THIEF"), "Thief should have stealth")
        assertFalse(service.classHasStealth("WARRIOR"), "Warrior should not have stealth")
        assertFalse(service.classHasStealth("MAGE"), "Mage should not have stealth")
    }

    @Test
    fun testDeathContentLevelAware() {
        val service = createService()
        val l1Content = service.deathContent(1)
        assertTrue(l1Content.contains("no XP penalty"), "Level 1 should mention no penalty")

        val l5Content = service.deathContent(5)
        assertTrue(l5Content.contains("XP penalty"), "Level 5+ should mention XP penalty")
        assertFalse(l5Content.contains("no XP"), "Level 5+ should not say 'no XP penalty'")
    }

    @Test
    fun testContentOverride() = runBlocking {
        val service = createService()
        val outgoing = Channel<Frame>(Channel.UNLIMITED)
        val session = createTestSession(outgoing)
        session.playerName = "TestPlayer"

        service.trySend(session, "welcome", contentOverride = "Custom welcome!")

        val messages = drainMessages(outgoing)
        val tutorials = messages.filterIsInstance<ServerMessage.Tutorial>()
        assertEquals(1, tutorials.size)
        assertEquals("Custom welcome!", tutorials[0].content)
        assertEquals("Welcome to NeoMud!", tutorials[0].title)
    }

    @Test
    fun testNonBlockingTutorialFields() = runBlocking {
        val service = createService()
        val outgoing = Channel<Frame>(Channel.UNLIMITED)
        val session = createTestSession(outgoing)
        session.playerName = "TestPlayer"

        service.trySend(session, "tut_hostile_npc")

        val messages = drainMessages(outgoing)
        val tutorials = messages.filterIsInstance<ServerMessage.Tutorial>()
        assertEquals(1, tutorials.size)
        assertEquals(false, tutorials[0].blocking)
        assertEquals("npc_sprites", tutorials[0].targetElement)
    }

    @Test
    fun testTutorialConfigConstants() {
        assertEquals(8_000L, GameConfig.Tutorial.TOAST_DISPLAY_MS)
    }

    @Test
    fun testUnknownKeyReturnsFalse() = runBlocking {
        val service = createService()
        val outgoing = Channel<Frame>(Channel.UNLIMITED)
        val session = createTestSession(outgoing)
        session.playerName = "TestPlayer"

        val result = service.trySend(session, "nonexistent_key")
        assertFalse(result)
    }

    @Test
    fun testPlayerSessionTutorialFields() {
        val outgoing = Channel<Frame>(Channel.UNLIMITED)
        val session = createTestSession(outgoing)

        assertFalse(session.firstKillDone)
        assertFalse(session.inCombat)
    }

    @Test
    fun testInventoryTutorialIsNonBlocking() = runBlocking {
        val service = createService()
        val outgoing = Channel<Frame>(Channel.UNLIMITED)
        val session = createTestSession(outgoing)
        session.playerName = "TestPlayer"

        service.trySend(session, "tut_inventory")

        val messages = drainMessages(outgoing)
        val tutorials = messages.filterIsInstance<ServerMessage.Tutorial>()
        assertEquals(1, tutorials.size)
        assertEquals("tut_inventory", tutorials[0].key)
        assertFalse(tutorials[0].blocking, "Inventory tutorial should be non-blocking")
    }

    @Test
    fun testLockedExitTutorialIsNonBlocking() = runBlocking {
        val service = createService()
        val outgoing = Channel<Frame>(Channel.UNLIMITED)
        val session = createTestSession(outgoing)
        session.playerName = "TestPlayer"

        service.trySend(session, "tut_locked_exit")

        val messages = drainMessages(outgoing)
        val tutorials = messages.filterIsInstance<ServerMessage.Tutorial>()
        assertEquals(1, tutorials.size)
        assertEquals("tut_locked_exit", tutorials[0].key)
        assertFalse(tutorials[0].blocking, "Locked exit tutorial should be non-blocking")
    }

    @Test
    fun testHiddenExitTutorialIsNonBlocking() = runBlocking {
        val service = createService()
        val outgoing = Channel<Frame>(Channel.UNLIMITED)
        val session = createTestSession(outgoing)
        session.playerName = "TestPlayer"

        service.trySend(session, "tut_hidden_exit")

        val messages = drainMessages(outgoing)
        val tutorials = messages.filterIsInstance<ServerMessage.Tutorial>()
        assertEquals(1, tutorials.size)
        assertEquals("tut_hidden_exit", tutorials[0].key)
        assertFalse(tutorials[0].blocking, "Hidden exit tutorial should be non-blocking")
    }
}
