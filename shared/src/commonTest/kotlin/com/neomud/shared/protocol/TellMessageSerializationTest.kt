package com.neomud.shared.protocol

import com.neomud.shared.model.OnlinePlayer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TellMessageSerializationTest {

    @Test
    fun testTellReceivedRoundTrip() {
        val original = ServerMessage.TellReceived("Alice", "hello there")
        val json = MessageSerializer.encodeServerMessage(original)
        val decoded = MessageSerializer.decodeServerMessage(json)
        assertEquals(original, decoded)
        assertTrue(json.contains("\"type\":\"tell_received\""))
    }

    @Test
    fun testTellSentRoundTrip() {
        val original = ServerMessage.TellSent("Bob", "hey back")
        val json = MessageSerializer.encodeServerMessage(original)
        val decoded = MessageSerializer.decodeServerMessage(json)
        assertEquals(original, decoded)
        assertTrue(json.contains("\"type\":\"tell_sent\""))
    }

    @Test
    fun testWhoListRoundTrip() {
        val original = ServerMessage.WhoList(listOf(
            OnlinePlayer("Alice", 10, "WARRIOR", "Millhaven"),
            OnlinePlayer("Bob", 5, "CLERIC", "Ashwood Burn")
        ))
        val json = MessageSerializer.encodeServerMessage(original)
        val decoded = MessageSerializer.decodeServerMessage(json)
        assertEquals(original, decoded)
        assertTrue(json.contains("\"type\":\"who_list\""))
    }

    @Test
    fun testWhoListEmptyRoundTrip() {
        val original = ServerMessage.WhoList(emptyList())
        val json = MessageSerializer.encodeServerMessage(original)
        val decoded = MessageSerializer.decodeServerMessage(json)
        assertEquals(original, decoded)
    }
}
