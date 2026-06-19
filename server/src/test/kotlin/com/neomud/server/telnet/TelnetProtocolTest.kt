package com.neomud.server.telnet

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TelnetProtocolTest {

    @Test fun negotiationFrameLength() {
        assertEquals(3, Telnet.negotiationFrame(Telnet.DO, Telnet.SGA).size)
    }

    @Test fun negotiationFrameBytes() {
        val frame = Telnet.negotiationFrame(Telnet.DO, Telnet.SGA)
        assertEquals(Telnet.IAC, frame[0])
        assertEquals(Telnet.DO, frame[1])
        assertEquals(Telnet.SGA, frame[2])
    }

    @Test fun negotiationFrameWillEcho() {
        val frame = Telnet.negotiationFrame(Telnet.WILL, Telnet.ECHO)
        assertEquals(Telnet.IAC, frame[0])
        assertEquals(Telnet.WILL, frame[1])
        assertEquals(Telnet.ECHO, frame[2])
    }

    @Test fun negotiationFrameWontEcho() {
        val frame = Telnet.negotiationFrame(Telnet.WONT, Telnet.ECHO)
        assertEquals(Telnet.WONT, frame[1])
        assertEquals(Telnet.ECHO, frame[2])
    }

    @Test fun subNegotiationFrameWrapsCorrectly() {
        val data = byteArrayOf(0x01, 0x02)
        val frame = Telnet.subNegotiationFrame(Telnet.NAWS, data)
        assertEquals(Telnet.IAC, frame[0])
        assertEquals(Telnet.SB, frame[1])
        assertEquals(Telnet.NAWS, frame[2])
        assertEquals(0x01, frame[3])
        assertEquals(0x02, frame[4])
        assertEquals(Telnet.IAC, frame[5])
        assertEquals(Telnet.SE, frame[6])
    }

    @Test fun subNegotiationFrameMinLength() {
        val frame = Telnet.subNegotiationFrame(Telnet.NAWS, byteArrayOf())
        // IAC SB option IAC SE = 5 bytes minimum
        assertEquals(5, frame.size)
        assertEquals(Telnet.IAC, frame[0])
        assertEquals(Telnet.SB, frame[1])
        assertEquals(Telnet.NAWS, frame[2])
        assertEquals(Telnet.IAC, frame[3])
        assertEquals(Telnet.SE, frame[4])
    }

    @Test fun subNegotiationFrameTotalLength() {
        val data = byteArrayOf(0x00, 0x50, 0x00, 0x18) // NAWS 80×24
        val frame = Telnet.subNegotiationFrame(Telnet.NAWS, data)
        assertEquals(4 + 5, frame.size) // data.size + 5 overhead
    }
}
