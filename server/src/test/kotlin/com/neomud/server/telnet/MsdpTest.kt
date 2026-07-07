package com.neomud.server.telnet

import com.neomud.shared.model.*
import com.neomud.shared.protocol.ServerMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MsdpTest {

    /** Decodes an `IAC SB MSDP (MSDP_VAR name MSDP_VAL value)… IAC SE` frame into a map. */
    private fun decode(frame: ByteArray): Map<String, String> {
        assertEquals(Telnet.IAC, frame[0])
        assertEquals(Telnet.SB, frame[1])
        assertEquals(Telnet.MSDP, frame[2])
        assertEquals(Telnet.IAC, frame[frame.size - 2])
        assertEquals(Telnet.SE, frame[frame.size - 1])
        val body = frame.copyOfRange(3, frame.size - 2)
        val result = LinkedHashMap<String, String>()
        var i = 0
        while (i < body.size) {
            require(body[i] == Telnet.MSDP_VAR)
            i++
            val name = StringBuilder()
            while (i < body.size && body[i] != Telnet.MSDP_VAL) { name.append(body[i].toInt().toChar()); i++ }
            require(body[i] == Telnet.MSDP_VAL)
            i++
            val value = StringBuilder()
            while (i < body.size && body[i] != Telnet.MSDP_VAR) { value.append(body[i].toInt().toChar()); i++ }
            result[name.toString()] = value.toString()
        }
        return result
    }

    private fun testPlayer() = Player(
        name = "Aldric", characterClass = "Warrior", stats = Stats(),
        currentHp = 60, maxHp = 80, currentMp = 20, maxMp = 30, level = 5, currentRoomId = "z:r"
    )

    @Test fun loginEmitsVitalsAndLevel() {
        val s = TelnetSessionState()
        val msg = ServerMessage.LoginOk(testPlayer())
        s.update(msg)
        val vars = decode(Msdp.framesFor(msg, s).single())
        assertEquals("60", vars["HEALTH"])
        assertEquals("80", vars["HEALTH_MAX"])
        assertEquals("20", vars["MANA"])
        assertEquals("30", vars["MANA_MAX"])
        assertEquals("5", vars["LEVEL"])
    }

    @Test fun roomInfoEmitsRoomNameAndExits() {
        val s = TelnetSessionState()
        val r = Room(id = "z:sq", name = "Town Square", description = "",
            exits = mapOf(Direction.NORTH to "z:n", Direction.SOUTH to "z:s"),
            zoneId = "z", x = 0, y = 0)
        val msg = ServerMessage.RoomInfo(r, emptyList(), emptyList())
        s.update(msg)
        val vars = decode(Msdp.framesFor(msg, s).single())
        assertEquals("Town Square", vars["ROOM_NAME"])
        val exits = vars["ROOM_EXITS"]!!.split(",").toSet()
        assertEquals(setOf("north", "south"), exits)
    }

    @Test fun silentMessageEmitsNothing() {
        val s = TelnetSessionState()
        assertTrue(Msdp.framesFor(ServerMessage.Pong, s).isEmpty())
    }
}
