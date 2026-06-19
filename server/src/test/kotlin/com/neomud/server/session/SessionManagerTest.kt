package com.neomud.server.session

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SessionManagerTest {

    /** Match the stub shape used throughout the server/game tests. */
    private fun fakeSession(): PlayerSession {
        return PlayerSession(object : TransportSession {
            override suspend fun sendMessage(message: com.neomud.shared.protocol.ServerMessage) {}
            override suspend fun close(reason: String) {}
        })
    }

    @Test
    fun `activeSessionCount reflects adds and removes`() {
        val sm = SessionManager()
        assertEquals(0, sm.activeSessionCount())

        assertTrue(sm.addSession("Alice", fakeSession(), username = "alice"))
        assertEquals(1, sm.activeSessionCount())

        assertTrue(sm.addSession("Bob", fakeSession(), username = "bob"))
        assertEquals(2, sm.activeSessionCount())

        sm.removeSession("Alice")
        assertEquals(1, sm.activeSessionCount())

        sm.removeSession("Bob")
        assertEquals(0, sm.activeSessionCount())
    }

    @Test
    fun `touchActivity advances lastActivityAt`() {
        val sm = SessionManager()
        val before = sm.getLastActivityAt()
        Thread.sleep(5)
        sm.touchActivity()
        val after = sm.getLastActivityAt()
        assertTrue(after.isAfter(before), "lastActivityAt should move forward; before=$before after=$after")
    }

    @Test
    fun `addSession updates lastActivityAt on success`() {
        val sm = SessionManager()
        val before = sm.getLastActivityAt()
        Thread.sleep(5)
        assertTrue(sm.addSession("Alice", fakeSession(), username = "alice"))
        assertTrue(sm.getLastActivityAt().isAfter(before))
    }

    @Test
    fun `duplicate-username addSession returns false and still counts (activity unchanged semantics not required)`() {
        val sm = SessionManager()
        assertTrue(sm.addSession("Alice", fakeSession(), username = "alice"))
        // Second add with the same username but a different character should fail atomically.
        assertFalse(sm.addSession("AliceAlt", fakeSession(), username = "alice"))
        assertEquals(1, sm.activeSessionCount())
    }

    @Test
    fun `removeSession updates lastActivityAt`() {
        val sm = SessionManager()
        sm.addSession("Alice", fakeSession(), username = "alice")
        val before = sm.getLastActivityAt()
        Thread.sleep(5)
        sm.removeSession("Alice")
        assertTrue(sm.getLastActivityAt().isAfter(before))
    }
}
