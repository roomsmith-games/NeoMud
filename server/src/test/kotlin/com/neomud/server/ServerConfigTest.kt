package com.neomud.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ServerConfigTest {

    @Test fun defaultTelnetEnabledIsTrueWhenEnvUnset() {
        // ServerConfig default reads NEOMUD_TELNET_ENABLED env var; if not set (not "false") → true
        // This test is valid as long as the env var isn't set to "false" in the test environment
        val env = System.getenv("NEOMUD_TELNET_ENABLED")?.lowercase()
        if (env != "false") {
            assertTrue(ServerConfig().telnetEnabled)
        }
    }

    @Test fun noTelnetFlagDisablesTelnet() {
        val config = parseArgs(arrayOf("--no-telnet"))
        assertFalse(config.telnetEnabled)
    }

    @Test fun noTelnetFlagDoesNotAffectOtherDefaults() {
        val config = parseArgs(arrayOf("--no-telnet"))
        assertTrue(config.port > 0)
        assertTrue(config.dbPath.isNotBlank())
    }

    @Test fun defaultParseArgsHasTelnetEnabled() {
        val env = System.getenv("NEOMUD_TELNET_ENABLED")?.lowercase()
        if (env != "false") {
            val config = parseArgs(arrayOf())
            assertTrue(config.telnetEnabled)
        }
    }

    @Test fun noTelnetCombinesWithOtherArgs() {
        val config = parseArgs(arrayOf("--no-telnet", "--port", "9191"))
        assertFalse(config.telnetEnabled)
        assertEquals(9191, config.port)
    }
}
