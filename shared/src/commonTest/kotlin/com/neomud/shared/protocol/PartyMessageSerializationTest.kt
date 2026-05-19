package com.neomud.shared.protocol

import com.neomud.shared.model.FollowState
import com.neomud.shared.model.PartyMember
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PartyMessageSerializationTest {

    // ─── ClientMessage round trips ──────────────────────────

    @Test
    fun testPartyInviteRoundTrip() {
        val original = ClientMessage.PartyInvite("Thorin")
        val json = MessageSerializer.encodeClientMessage(original)
        val decoded = MessageSerializer.decodeClientMessage(json)
        assertEquals(original, decoded)
        assertTrue(json.contains("\"type\":\"party_invite\""))
    }

    @Test
    fun testPartyAcceptRoundTrip() {
        val original = ClientMessage.PartyAccept("Gandalf")
        val json = MessageSerializer.encodeClientMessage(original)
        val decoded = MessageSerializer.decodeClientMessage(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testPartyDeclineRoundTrip() {
        val original = ClientMessage.PartyDecline("Gandalf")
        val json = MessageSerializer.encodeClientMessage(original)
        val decoded = MessageSerializer.decodeClientMessage(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testPartyLeaveRoundTrip() {
        val original = ClientMessage.PartyLeave
        val json = MessageSerializer.encodeClientMessage(original)
        val decoded = MessageSerializer.decodeClientMessage(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testPartyKickRoundTrip() {
        val original = ClientMessage.PartyKick("Bilbo")
        val json = MessageSerializer.encodeClientMessage(original)
        val decoded = MessageSerializer.decodeClientMessage(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testPartySayRoundTrip() {
        val original = ClientMessage.PartySay("Follow me to the dungeon!")
        val json = MessageSerializer.encodeClientMessage(original)
        val decoded = MessageSerializer.decodeClientMessage(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testFollowRoundTrip() {
        val original = ClientMessage.Follow("Thorin")
        val json = MessageSerializer.encodeClientMessage(original)
        val decoded = MessageSerializer.decodeClientMessage(json)
        assertEquals(original, decoded)
        assertTrue(json.contains("\"type\":\"follow\""))
    }

    @Test
    fun testFollowStopRoundTrip() {
        val original = ClientMessage.FollowStop
        val json = MessageSerializer.encodeClientMessage(original)
        val decoded = MessageSerializer.decodeClientMessage(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testRallyRoundTrip() {
        val original = ClientMessage.Rally
        val json = MessageSerializer.encodeClientMessage(original)
        val decoded = MessageSerializer.decodeClientMessage(json)
        assertEquals(original, decoded)
    }

    // ─── ServerMessage round trips ──────────────────────────

    @Test
    fun testPartyInviteReceivedRoundTrip() {
        val original = ServerMessage.PartyInviteReceived("Gandalf", 2)
        val json = MessageSerializer.encodeServerMessage(original)
        val decoded = MessageSerializer.decodeServerMessage(json)
        assertEquals(original, decoded)
        assertTrue(json.contains("\"type\":\"party_invite_received\""))
    }

    @Test
    fun testPartyFormedRoundTrip() {
        val members = listOf(
            PartyMember("Gandalf", "MAGE", "HUMAN", 15, 80, 120, 90, 100, "millhaven:town_square", isLeader = true),
            PartyMember("Thorin", "WARRIOR", "DWARF", 12, 150, 150, 10, 20, "millhaven:town_square")
        )
        val original = ServerMessage.PartyFormed("party_abc123", members, "Gandalf")
        val json = MessageSerializer.encodeServerMessage(original)
        val decoded = MessageSerializer.decodeServerMessage(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testPartyMemberJoinedRoundTrip() {
        val member = PartyMember("Bilbo", "THIEF", "HALFLING", 8, 60, 80, 5, 10, "millhaven:market")
        val original = ServerMessage.PartyMemberJoined(member)
        val json = MessageSerializer.encodeServerMessage(original)
        val decoded = MessageSerializer.decodeServerMessage(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testPartyMemberLeftRoundTrip() {
        val original = ServerMessage.PartyMemberLeft("Bilbo", "left")
        val json = MessageSerializer.encodeServerMessage(original)
        val decoded = MessageSerializer.decodeServerMessage(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testPartyMemberLeftKickedRoundTrip() {
        val original = ServerMessage.PartyMemberLeft("Bilbo", "kicked")
        val json = MessageSerializer.encodeServerMessage(original)
        val decoded = MessageSerializer.decodeServerMessage(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testPartyMemberLeftDefaultReasonRoundTrip() {
        val original = ServerMessage.PartyMemberLeft("Bilbo")
        val json = MessageSerializer.encodeServerMessage(original)
        val decoded = MessageSerializer.decodeServerMessage(json) as ServerMessage.PartyMemberLeft
        assertEquals("left", decoded.reason)
    }

    @Test
    fun testPartyDisbandedRoundTrip() {
        val original = ServerMessage.PartyDisbanded("leader left")
        val json = MessageSerializer.encodeServerMessage(original)
        val decoded = MessageSerializer.decodeServerMessage(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testPartyDisbandedDefaultReasonRoundTrip() {
        val original = ServerMessage.PartyDisbanded()
        val json = MessageSerializer.encodeServerMessage(original)
        val decoded = MessageSerializer.decodeServerMessage(json) as ServerMessage.PartyDisbanded
        assertEquals("disbanded", decoded.reason)
    }

    @Test
    fun testPartyMemberUpdateRoundTrip() {
        val original = ServerMessage.PartyMemberUpdate(
            "Thorin", 120, 150, 15, 20, "foothills:meadow"
        )
        val json = MessageSerializer.encodeServerMessage(original)
        val decoded = MessageSerializer.decodeServerMessage(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testPartyMemberUpdateDefaultsRoundTrip() {
        val original = ServerMessage.PartyMemberUpdate("Thorin", 120, 150)
        val json = MessageSerializer.encodeServerMessage(original)
        val decoded = MessageSerializer.decodeServerMessage(json) as ServerMessage.PartyMemberUpdate
        assertEquals(0, decoded.currentMp)
        assertEquals(0, decoded.maxMp)
        assertEquals("", decoded.roomId)
    }

    @Test
    fun testPartyChatMessageRoundTrip() {
        val original = ServerMessage.PartyChatMessage("Gandalf", "You shall not pass!")
        val json = MessageSerializer.encodeServerMessage(original)
        val decoded = MessageSerializer.decodeServerMessage(json)
        assertEquals(original, decoded)
        assertTrue(json.contains("\"type\":\"party_chat\""))
    }

    @Test
    fun testPartyInfoRoundTrip() {
        val members = listOf(
            PartyMember("Gandalf", "MAGE", "HUMAN", 15, 80, 120, 90, 100, "millhaven:town_square", isLeader = true),
            PartyMember("Thorin", "WARRIOR", "DWARF", 12, 150, 150, 10, 20, "millhaven:town_square"),
            PartyMember("Bilbo", "THIEF", "HALFLING", 8, 60, 80, 5, 10, "millhaven:market")
        )
        val original = ServerMessage.PartyInfo("party_abc123", members, "Gandalf")
        val json = MessageSerializer.encodeServerMessage(original)
        val decoded = MessageSerializer.decodeServerMessage(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testFollowUpdateRoundTrip() {
        val original = ServerMessage.FollowUpdate("Bilbo", "Gandalf", FollowState.ACTIVE)
        val json = MessageSerializer.encodeServerMessage(original)
        val decoded = MessageSerializer.decodeServerMessage(json)
        assertEquals(original, decoded)
        assertTrue(json.contains("\"type\":\"follow_update\""))
    }

    @Test
    fun testFollowUpdatePausedRoundTrip() {
        val original = ServerMessage.FollowUpdate("Bilbo", "Gandalf", FollowState.PAUSED)
        val json = MessageSerializer.encodeServerMessage(original)
        val decoded = MessageSerializer.decodeServerMessage(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testFollowUpdateOffRoundTrip() {
        val original = ServerMessage.FollowUpdate("Bilbo", "Gandalf", FollowState.OFF)
        val json = MessageSerializer.encodeServerMessage(original)
        val decoded = MessageSerializer.decodeServerMessage(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testRallyPingRoundTrip() {
        val original = ServerMessage.RallyPing(
            "Gandalf", "foothills:meadow", "Sunlit Meadow", "Foothills"
        )
        val json = MessageSerializer.encodeServerMessage(original)
        val decoded = MessageSerializer.decodeServerMessage(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testRallyPingDefaultZoneRoundTrip() {
        val original = ServerMessage.RallyPing("Gandalf", "town:square", "Town Square")
        val json = MessageSerializer.encodeServerMessage(original)
        val decoded = MessageSerializer.decodeServerMessage(json) as ServerMessage.RallyPing
        assertEquals("", decoded.zoneName)
    }

    @Test
    fun testFollowFailedRoundTrip() {
        val original = ServerMessage.FollowFailed("Bilbo", "Target is not in your party")
        val json = MessageSerializer.encodeServerMessage(original)
        val decoded = MessageSerializer.decodeServerMessage(json)
        assertEquals(original, decoded)
    }

    // ─── Backward compatibility ─────────────────────────────

    @Test
    fun testPartyMemberMinimalFieldsBackwardCompat() {
        val member = PartyMember("Gandalf", "MAGE", level = 10, currentHp = 80, maxHp = 120)
        val original = ServerMessage.PartyMemberJoined(member)
        val json = MessageSerializer.encodeServerMessage(original)
        val decoded = MessageSerializer.decodeServerMessage(json) as ServerMessage.PartyMemberJoined
        assertEquals("", decoded.member.race)
        assertEquals(0, decoded.member.currentMp)
        assertEquals(0, decoded.member.maxMp)
        assertEquals("", decoded.member.roomId)
        assertEquals(false, decoded.member.isLeader)
    }

    @Test
    fun testIgnoreUnknownKeysOnPartyMessages() {
        val jsonWithExtra = """{"type":"party_invite_received","inviterName":"Gandalf","partySize":2,"futureField":"whatever"}"""
        val decoded = MessageSerializer.decodeServerMessage(jsonWithExtra) as ServerMessage.PartyInviteReceived
        assertEquals("Gandalf", decoded.inviterName)
        assertEquals(2, decoded.partySize)
    }

    @Test
    fun testIgnoreUnknownKeysOnFollowMessages() {
        val jsonWithExtra = """{"type":"follow_update","followerName":"Bilbo","targetName":"Gandalf","state":"ACTIVE","futureField":42}"""
        val decoded = MessageSerializer.decodeServerMessage(jsonWithExtra) as ServerMessage.FollowUpdate
        assertEquals("Bilbo", decoded.followerName)
        assertEquals(FollowState.ACTIVE, decoded.state)
    }
}
