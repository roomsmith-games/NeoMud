package com.neomud.server

import com.neomud.server.persistence.repository.PlayerFlagsRepository
import com.neomud.server.persistence.tables.PlayerFlagsTable
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlayerFlagsRepositoryTest {

    private fun withTestDb(block: () -> Unit) {
        val tmpFile = File.createTempFile("neomud_flags_test_", ".db")
        tmpFile.deleteOnExit()
        tmpFile.delete()
        Database.connect("jdbc:sqlite:${tmpFile.absolutePath}", driver = "org.sqlite.JDBC")
        transaction { SchemaUtils.create(PlayerFlagsTable) }
        block()
    }

    @Test
    fun `getFlag returns null when flag is missing`() = withTestDb {
        val repo = PlayerFlagsRepository()
        assertNull(repo.getFlag("nobody", "endgame_choice"))
    }

    @Test
    fun `setFlag then getFlag round-trips`() = withTestDb {
        val repo = PlayerFlagsRepository()
        repo.setFlag("alice", "endgame_choice", "seal")
        assertEquals("seal", repo.getFlag("alice", "endgame_choice"))
    }

    @Test
    fun `setFlag overwrites existing value for same key`() = withTestDb {
        val repo = PlayerFlagsRepository()
        repo.setFlag("alice", "endgame_choice", "seal")
        repo.setFlag("alice", "endgame_choice", "sunder")
        assertEquals("sunder", repo.getFlag("alice", "endgame_choice"))
    }

    @Test
    fun `flags are scoped per player`() = withTestDb {
        val repo = PlayerFlagsRepository()
        repo.setFlag("alice", "endgame_choice", "seal")
        repo.setFlag("bob", "endgame_choice", "sunder")
        assertEquals("seal", repo.getFlag("alice", "endgame_choice"))
        assertEquals("sunder", repo.getFlag("bob", "endgame_choice"))
    }

    @Test
    fun `clearFlag removes only the named flag`() = withTestDb {
        val repo = PlayerFlagsRepository()
        repo.setFlag("alice", "endgame_choice", "seal")
        repo.setFlag("alice", "warden_token_seen", "true")
        repo.clearFlag("alice", "endgame_choice")
        assertNull(repo.getFlag("alice", "endgame_choice"))
        assertEquals("true", repo.getFlag("alice", "warden_token_seen"))
    }

    @Test
    fun `clearFlag is a no-op for missing flag`() = withTestDb {
        val repo = PlayerFlagsRepository()
        repo.clearFlag("nobody", "anything") // should not throw
    }

    @Test
    fun `loadAllFlags returns all flags for the player`() = withTestDb {
        val repo = PlayerFlagsRepository()
        repo.setFlag("alice", "endgame_choice", "seal")
        repo.setFlag("alice", "warden_token_seen", "true")
        repo.setFlag("alice", "current_arc", "II")
        val flags = repo.loadAllFlags("alice")
        assertEquals(3, flags.size)
        assertEquals("seal", flags["endgame_choice"])
        assertEquals("true", flags["warden_token_seen"])
        assertEquals("II", flags["current_arc"])
    }

    @Test
    fun `loadAllFlags returns empty map for unknown player`() = withTestDb {
        val repo = PlayerFlagsRepository()
        val flags = repo.loadAllFlags("nobody")
        assertTrue(flags.isEmpty())
    }
}
