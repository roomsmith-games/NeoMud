package com.neomud.server.telnet

import io.ktor.utils.io.*

/**
 * Reads lines from a ByteReadChannel, stripping and processing IAC telnet sequences.
 *
 * Handles:
 * - IAC IAC → literal 0xFF character in output
 * - IAC WILL/WONT/DO/DONT <opt> → calls onNegotiation, stripped from output
 * - IAC SB NAWS <w-hi> <w-lo> … IAC SE → calls onNaws(width), stripped from output
 * - IAC SB <other> … IAC SE → silently stripped
 * - \r\n or \r\0 → end of line (CR-NUL is valid RFC 854 line terminator)
 * - \n alone → end of line
 * - Backspace (8) / DEL (127) → removes previous character
 * - Control chars < 32 (except \r, \n, backspace) → ignored
 */
class IacLineReader(
    val channel: ByteReadChannel,
    initialByte: Byte? = null,
    private val onNaws: (width: Int) -> Unit = {},
    private val onNegotiation: suspend (cmd: Byte, option: Byte) -> Unit = { _, _ -> },
) {
    private var peeked: Byte? = initialByte

    suspend fun readLine(): String? {
        val buf = StringBuilder()
        while (true) {
            val b = readByte() ?: return if (buf.isEmpty()) null else buf.toString()

            when {
                b == Telnet.IAC -> {
                    val cmd = readByte() ?: return buf.toString()
                    when (cmd) {
                        Telnet.IAC -> buf.append(0xFF.toChar())
                        Telnet.WILL, Telnet.WONT, Telnet.DO, Telnet.DONT -> {
                            val opt = readByte() ?: return buf.toString()
                            onNegotiation(cmd, opt)
                        }
                        Telnet.SB -> handleSubnegotiation()
                        else -> {}
                    }
                }
                b == '\r'.code.toByte() -> {
                    // Consume trailing \n or \0
                    val next = peekByte()
                    if (next == '\n'.code.toByte() || next == 0.toByte()) readByte()
                    return buf.toString()
                }
                b == '\n'.code.toByte() -> return buf.toString()
                b == 8.toByte() || b == 127.toByte() -> {
                    if (buf.isNotEmpty()) buf.deleteCharAt(buf.length - 1)
                }
                b >= 32 -> buf.append(b.toInt().toChar())
                else -> {}
            }
        }
    }

    private suspend fun handleSubnegotiation() {
        val data = mutableListOf<Byte>()
        while (true) {
            val b = readByte() ?: return
            if (b == Telnet.IAC) {
                val next = readByte() ?: return
                if (next == Telnet.SE) break
                if (next == Telnet.IAC) data.add(b)
            } else {
                data.add(b)
            }
        }
        if (data.isEmpty()) return
        val option = data[0]
        val payload = data.drop(1)
        if (option == Telnet.NAWS && payload.size >= 4) {
            val width = ((payload[0].toInt() and 0xFF) shl 8) or (payload[1].toInt() and 0xFF)
            if (width in 40..200) onNaws(width)
        }
    }

    private suspend fun readByte(): Byte? {
        peeked?.let { peeked = null; return it }
        return try {
            channel.readByte()
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun peekByte(): Byte? {
        if (peeked != null) return peeked
        return try {
            val b = channel.readByte()
            peeked = b
            b
        } catch (_: Exception) {
            null
        }
    }
}
