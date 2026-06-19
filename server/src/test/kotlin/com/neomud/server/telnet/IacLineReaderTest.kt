package com.neomud.server.telnet

import io.ktor.utils.io.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IacLineReaderTest {

    private fun reader(
        bytes: ByteArray,
        onNaws: (Int) -> Unit = {},
        onNegotiation: suspend (Byte, Byte) -> Unit = { _, _ -> },
    ) = IacLineReader(ByteReadChannel(bytes), onNaws = onNaws, onNegotiation = onNegotiation)

    // ---- Basic line endings ----

    @Test fun crLfTerminator() = runTest {
        val r = reader("hello\r\n".toByteArray())
        assertEquals("hello", r.readLine())
    }

    @Test fun crNulTerminator() = runTest {
        val r = reader(byteArrayOf(*"hello".toByteArray(), '\r'.code.toByte(), 0))
        assertEquals("hello", r.readLine())
    }

    @Test fun lfOnlyTerminator() = runTest {
        val r = reader("hello\n".toByteArray())
        assertEquals("hello", r.readLine())
    }

    @Test fun emptyLineReturnsEmptyString() = runTest {
        val r = reader("\r\n".toByteArray())
        assertEquals("", r.readLine())
    }

    @Test fun multipleLinesReadInOrder() = runTest {
        val r = reader("first\r\nsecond\r\n".toByteArray())
        assertEquals("first", r.readLine())
        assertEquals("second", r.readLine())
    }

    @Test fun eofWithContentReturnsContent() = runTest {
        val r = reader("no newline".toByteArray())
        assertEquals("no newline", r.readLine())
    }

    @Test fun eofEmptyReturnsNull() = runTest {
        val r = reader(byteArrayOf())
        assertNull(r.readLine())
    }

    // ---- IAC escape ----

    @Test fun iacIacProducesLiteralFf() = runTest {
        val bytes = byteArrayOf(*"ab".toByteArray(), Telnet.IAC, Telnet.IAC, *"cd\r\n".toByteArray())
        val line = reader(bytes).readLine()
        assertEquals(5, line?.length)
        assertEquals(0xFF.toChar(), line?.get(2))
    }

    // ---- Negotiation stripping ----

    @Test fun iacWillStripped() = runTest {
        val bytes = byteArrayOf(Telnet.IAC, Telnet.WILL, Telnet.SGA, *"hello\r\n".toByteArray())
        assertEquals("hello", reader(bytes).readLine())
    }

    @Test fun iacWontStripped() = runTest {
        val bytes = byteArrayOf(Telnet.IAC, Telnet.WONT, Telnet.ECHO, *"hi\r\n".toByteArray())
        assertEquals("hi", reader(bytes).readLine())
    }

    @Test fun iacDoStripped() = runTest {
        val bytes = byteArrayOf(Telnet.IAC, Telnet.DO, Telnet.NAWS, *"test\r\n".toByteArray())
        assertEquals("test", reader(bytes).readLine())
    }

    @Test fun iacDontStripped() = runTest {
        val bytes = byteArrayOf(Telnet.IAC, Telnet.DONT, Telnet.ECHO, *"ok\r\n".toByteArray())
        assertEquals("ok", reader(bytes).readLine())
    }

    @Test fun negotiationCallbackFired() = runTest {
        val calls = mutableListOf<Pair<Byte, Byte>>()
        val bytes = byteArrayOf(Telnet.IAC, Telnet.WILL, Telnet.NAWS, *"x\r\n".toByteArray())
        reader(bytes, onNegotiation = { cmd, opt -> calls.add(cmd to opt) }).readLine()
        assertEquals(1, calls.size)
        assertEquals(Telnet.WILL to Telnet.NAWS, calls[0])
    }

    @Test fun multipleNegotiationsStripped() = runTest {
        val bytes = byteArrayOf(
            Telnet.IAC, Telnet.WILL, Telnet.SGA,
            Telnet.IAC, Telnet.DO, Telnet.NAWS,
            *"msg\r\n".toByteArray()
        )
        assertEquals("msg", reader(bytes).readLine())
    }

    // ---- NAWS subnegotiation ----

    @Test fun nawsSubnegotiationParsesWidth() = runTest {
        var capturedWidth = 0
        // NAWS: width=120 (0x00 0x78), height=24 (0x00 0x18)
        val nawsData = byteArrayOf(
            Telnet.IAC, Telnet.SB, Telnet.NAWS,
            0x00, 0x78, 0x00, 0x18,
            Telnet.IAC, Telnet.SE,
            *"line\r\n".toByteArray()
        )
        reader(nawsData, onNaws = { w -> capturedWidth = w }).readLine()
        assertEquals(120, capturedWidth)
    }

    @Test fun nawsTooNarrowIgnored() = runTest {
        var called = false
        // Width = 20 (below minimum 40)
        val nawsData = byteArrayOf(
            Telnet.IAC, Telnet.SB, Telnet.NAWS,
            0x00, 0x14, 0x00, 0x18,
            Telnet.IAC, Telnet.SE,
            *"line\r\n".toByteArray()
        )
        reader(nawsData, onNaws = { called = true }).readLine()
        assertTrue(!called)
    }

    @Test fun nawsTooWideIgnored() = runTest {
        var called = false
        // Width = 250 (above maximum 200)
        val nawsData = byteArrayOf(
            Telnet.IAC, Telnet.SB, Telnet.NAWS,
            0x00, 0xFA.toByte(), 0x00, 0x18,
            Telnet.IAC, Telnet.SE,
            *"line\r\n".toByteArray()
        )
        reader(nawsData, onNaws = { called = true }).readLine()
        assertTrue(!called)
    }

    @Test fun nawsBoundaryWidthAccepted() = runTest {
        var capturedWidth = 0
        // Width = 80 (default, valid)
        val nawsData = byteArrayOf(
            Telnet.IAC, Telnet.SB, Telnet.NAWS,
            0x00, 0x50, 0x00, 0x18,
            Telnet.IAC, Telnet.SE,
            *"x\r\n".toByteArray()
        )
        reader(nawsData, onNaws = { w -> capturedWidth = w }).readLine()
        assertEquals(80, capturedWidth)
    }

    @Test fun unknownSubnegotiationIgnored() = runTest {
        // SB option 99 (unknown) — should not crash or consume line text
        val bytes = byteArrayOf(
            Telnet.IAC, Telnet.SB, 0x63,
            0x01, 0x02,
            Telnet.IAC, Telnet.SE,
            *"text\r\n".toByteArray()
        )
        assertEquals("text", reader(bytes).readLine())
    }

    // ---- Backspace / DEL ----

    @Test fun backspaceRemovesPreviousChar() = runTest {
        // "helo" + backspace = "hel"
        val bytes = byteArrayOf(*"helo".toByteArray(), 8, *"\r\n".toByteArray())
        assertEquals("hel", reader(bytes).readLine())
    }

    @Test fun delRemovesPreviousChar() = runTest {
        val bytes = byteArrayOf(*"helo".toByteArray(), 127, *"\r\n".toByteArray())
        assertEquals("hel", reader(bytes).readLine())
    }

    @Test fun backspaceOnEmptyBufferIsNoop() = runTest {
        val bytes = byteArrayOf(8, *"hi\r\n".toByteArray())
        assertEquals("hi", reader(bytes).readLine())  // no crash, leading backspace discarded
    }

    // ---- Control chars ----

    @Test fun controlCharsIgnored() = runTest {
        // BEL (7) and ESC (27) should be ignored
        val bytes = byteArrayOf(*"hel".toByteArray(), 7, 27, *"lo\r\n".toByteArray())
        assertEquals("hello", reader(bytes).readLine())
    }

    // ---- initialByte pushback ----

    @Test fun initialByteIsReturnedFirst() = runTest {
        // Simulate pre-auth detection reading the first byte (0x00) before constructing IacLineReader
        val rest = "ello\r\n".toByteArray()
        val r = IacLineReader(ByteReadChannel(rest), initialByte = 'h'.code.toByte())
        assertEquals("hello", r.readLine())
    }

    @Test fun initialByteNullHasNoEffect() = runTest {
        val r = IacLineReader(ByteReadChannel("hello\r\n".toByteArray()), initialByte = null)
        assertEquals("hello", r.readLine())
    }

    // ---- IAC before and after text ----

    @Test fun iacSequenceBeforeText() = runTest {
        val bytes = byteArrayOf(
            Telnet.IAC, Telnet.WILL, Telnet.SGA,
            *"hello\r\n".toByteArray()
        )
        assertEquals("hello", reader(bytes).readLine())
    }

    @Test fun iacSequenceAfterText() = runTest {
        val bytes = byteArrayOf(
            *"hello".toByteArray(),
            Telnet.IAC, Telnet.DO, Telnet.NAWS,
            *"\r\n".toByteArray()
        )
        assertEquals("hello", reader(bytes).readLine())
    }

    @Test fun iacSequenceMidText() = runTest {
        val bytes = byteArrayOf(
            *"hel".toByteArray(),
            Telnet.IAC, Telnet.WILL, Telnet.ECHO,
            *"lo\r\n".toByteArray()
        )
        assertEquals("hello", reader(bytes).readLine())
    }
}
