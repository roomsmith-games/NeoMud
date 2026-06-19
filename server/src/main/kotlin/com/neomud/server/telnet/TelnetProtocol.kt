package com.neomud.server.telnet

object Telnet {
    const val IAC: Byte = 0xFF.toByte()
    const val WILL: Byte = 0xFB.toByte()
    const val WONT: Byte = 0xFC.toByte()
    const val DO: Byte = 0xFD.toByte()
    const val DONT: Byte = 0xFE.toByte()
    const val SB: Byte = 0xFA.toByte()   // subnegotiation begin
    const val SE: Byte = 0xF0.toByte()   // subnegotiation end

    const val ECHO: Byte = 0x01
    const val SGA: Byte = 0x03           // suppress go-ahead
    const val NAWS: Byte = 0x1F          // negotiate about window size
    const val GMCP: Byte = 0xC9.toByte()
    const val MSDP: Byte = 0x45

    fun negotiationFrame(command: Byte, option: Byte): ByteArray =
        byteArrayOf(IAC, command, option)

    fun subNegotiationFrame(option: Byte, data: ByteArray): ByteArray =
        byteArrayOf(IAC, SB, option) + data + byteArrayOf(IAC, SE)
}
