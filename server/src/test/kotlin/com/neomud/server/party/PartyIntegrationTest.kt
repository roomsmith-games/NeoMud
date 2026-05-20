package com.neomud.server.party

import com.neomud.server.module
import com.neomud.shared.model.Stats
import com.neomud.shared.protocol.ClientMessage
import com.neomud.shared.protocol.MessageSerializer
import com.neomud.shared.protocol.ServerMessage
import io.ktor.client.plugins.websocket.*
import io.ktor.server.testing.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import java.io.File
import kotlin.test.*

class PartyIntegrationTest {

    private fun testDbUrl(): String {
        val tmpFile = File.createTempFile("neomud_party_test_", ".db")
        tmpFile.deleteOnExit()
        tmpFile.delete()
        return "jdbc:sqlite:${tmpFile.absolutePath}"
    }

    private suspend fun DefaultClientWebSocketSession.consumeCatalogSync() {
        assertIs<ServerMessage.ServerHello>(recv())
        assertIs<ServerMessage.ClassCatalogSync>(recv())
        assertIs<ServerMessage.ItemCatalogSync>(recv())
        assertIs<ServerMessage.SkillCatalogSync>(recv())
        assertIs<ServerMessage.RaceCatalogSync>(recv())
        assertIs<ServerMessage.SpellCatalogSync>(recv())
    }

    private suspend fun DefaultClientWebSocketSession.recv(): ServerMessage {
        val frame = withTimeout(5000) { incoming.receive() }
        assertIs<Frame.Text>(frame)
        return MessageSerializer.decodeServerMessage((frame as Frame.Text).readText())
    }

    private suspend fun DefaultClientWebSocketSession.tryRecv(timeoutMs: Long = 1000): ServerMessage? {
        return try {
            val frame = withTimeout(timeoutMs) { incoming.receive() }
            if (frame is Frame.Text) MessageSerializer.decodeServerMessage(frame.readText()) else null
        } catch (_: Exception) {
            null
        }
    }

    private fun encode(msg: ClientMessage): Frame.Text =
        Frame.Text(MessageSerializer.encodeClientMessage(msg))

    /**
     * Drain login messages, returning any "extra" messages that arrived during login.
     * This prevents important messages (like PartyInviteReceived) from being swallowed.
     */
    private suspend fun DefaultClientWebSocketSession.consumeLoginSequence(): Pair<ServerMessage.LoginOk, List<ServerMessage>> {
        val loginOk = recv()
        assertIs<ServerMessage.LoginOk>(loginOk)
        val extras = mutableListOf<ServerMessage>()
        var sawRoomItems = false
        repeat(12) {
            if (sawRoomItems) return@repeat
            val msg = tryRecv(2000) ?: return@repeat
            when (msg) {
                is ServerMessage.RoomItemsUpdate -> sawRoomItems = true
                is ServerMessage.Tutorial,
                is ServerMessage.RoomInfo,
                is ServerMessage.MapData,
                is ServerMessage.InventoryUpdate -> { /* expected login sequence messages */ }
                else -> extras.add(msg)
            }
        }
        return loginOk to extras
    }

    private suspend fun DefaultClientWebSocketSession.register(
        username: String, characterName: String, cls: String,
        stats: Stats = Stats(strength = 30, agility = 22, intellect = 18, willpower = 18, health = 30, charm = 18)
    ) {
        consumeCatalogSync()
        send(encode(ClientMessage.Register(username, "pass1234", characterName, cls, allocatedStats = stats)))
        assertIs<ServerMessage.RegisterOk>(recv())
    }

    private suspend fun DefaultClientWebSocketSession.login(username: String): ServerMessage.LoginOk {
        consumeCatalogSync()
        send(encode(ClientMessage.Login(username, "pass1234")))
        val (loginOk, _) = consumeLoginSequence()
        return loginOk
    }

    /**
     * Drain messages looking for one that matches the predicate.
     * Collects non-matching messages for inspection.
     */
    private suspend fun DefaultClientWebSocketSession.drainUntil(
        predicate: (ServerMessage) -> Boolean,
        maxMessages: Int = 20,
        timeoutMs: Long = 5000
    ): ServerMessage? {
        repeat(maxMessages) {
            val msg = tryRecv(timeoutMs) ?: return null
            if (predicate(msg)) return msg
        }
        return null
    }

    // ── Party Formation ──────────────────────────────────────────────

    @Test
    fun `two players form a party via invite and accept`() = testApplication {
        application { module(jdbcUrl = testDbUrl()) }
        val ws = createClient { install(WebSockets) }

        ws.webSocket("/game") { register("alice", "Alice", "WARRIOR") }
        ws.webSocket("/game") { register("bob", "Bob", "WARRIOR") }

        coroutineScope {
            val aliceReady = CompletableDeferred<Unit>()
            val bobReady = CompletableDeferred<Unit>()
            val aliceDone = CompletableDeferred<Unit>()

            val sessionA = async {
                ws.webSocket("/game") {
                    login("alice")
                    aliceReady.complete(Unit)

                    // Wait for Bob to finish login before inviting
                    bobReady.await()
                    delay(200)

                    send(encode(ClientMessage.PartyInvite("Bob")))

                    // Drain — should eventually get PartyFormed after Bob accepts
                    val formed = drainUntil({ it is ServerMessage.PartyFormed })
                    assertNotNull(formed, "Alice should receive PartyFormed")
                    assertIs<ServerMessage.PartyFormed>(formed)
                    assertEquals(2, formed.members.size)
                    assertEquals("Alice", formed.leaderId)
                    assertTrue(formed.members.any { it.name == "Alice" })
                    assertTrue(formed.members.any { it.name == "Bob" })

                    aliceDone.complete(Unit)
                    delay(500)
                }
            }

            val sessionB = async {
                aliceReady.await()
                delay(200)
                ws.webSocket("/game") {
                    login("bob")
                    bobReady.complete(Unit)

                    // Should receive PartyInviteReceived
                    val invite = drainUntil({ it is ServerMessage.PartyInviteReceived })
                    assertNotNull(invite, "Bob should receive party invite")
                    assertIs<ServerMessage.PartyInviteReceived>(invite)
                    assertEquals("Alice", invite.inviterName)

                    send(encode(ClientMessage.PartyAccept("Alice")))

                    val formed = drainUntil({ it is ServerMessage.PartyFormed })
                    assertNotNull(formed, "Bob should receive PartyFormed")
                    assertIs<ServerMessage.PartyFormed>(formed)
                    assertEquals(2, formed.members.size)

                    aliceDone.await()
                }
            }

            sessionA.await()
            sessionB.await()
        }
    }

    // ── Party Chat ──────────────────────────────────────────────────

    @Test
    fun `party chat is delivered to all party members`() = testApplication {
        application { module(jdbcUrl = testDbUrl()) }
        val ws = createClient { install(WebSockets) }

        ws.webSocket("/game") { register("chatter_a", "Aria", "WARRIOR") }
        ws.webSocket("/game") { register("chatter_b", "Bran", "WARRIOR") }

        coroutineScope {
            val ariaReady = CompletableDeferred<Unit>()
            val branReady = CompletableDeferred<Unit>()
            val partyReady = CompletableDeferred<Unit>()
            val chatDone = CompletableDeferred<Unit>()

            val sessionA = async {
                ws.webSocket("/game") {
                    login("chatter_a")
                    ariaReady.complete(Unit)
                    branReady.await()
                    delay(200)

                    send(encode(ClientMessage.PartyInvite("Bran")))
                    val formed = drainUntil({ it is ServerMessage.PartyFormed })
                    assertNotNull(formed)
                    partyReady.complete(Unit)

                    // Send party chat
                    send(encode(ClientMessage.PartySay("Hello party!")))

                    val chat = drainUntil({ it is ServerMessage.PartyChatMessage })
                    assertNotNull(chat, "Aria should receive own party chat")
                    assertIs<ServerMessage.PartyChatMessage>(chat)
                    assertEquals("Aria", chat.senderName)
                    assertEquals("Hello party!", chat.message)

                    chatDone.await()
                    delay(500)
                }
            }

            val sessionB = async {
                ariaReady.await()
                delay(200)
                ws.webSocket("/game") {
                    login("chatter_b")
                    branReady.complete(Unit)

                    val invite = drainUntil({ it is ServerMessage.PartyInviteReceived })
                    assertNotNull(invite)
                    send(encode(ClientMessage.PartyAccept("Aria")))
                    drainUntil({ it is ServerMessage.PartyFormed })

                    partyReady.await()

                    val chat = drainUntil({ it is ServerMessage.PartyChatMessage })
                    assertNotNull(chat, "Bran should receive party chat")
                    assertIs<ServerMessage.PartyChatMessage>(chat)
                    assertEquals("Aria", chat.senderName)
                    assertEquals("Hello party!", chat.message)

                    chatDone.complete(Unit)
                }
            }

            sessionA.await()
            sessionB.await()
        }
    }

    // ── Invite Decline ──────────────────────────────────────────────

    @Test
    fun `declining invite notifies both players`() = testApplication {
        application { module(jdbcUrl = testDbUrl()) }
        val ws = createClient { install(WebSockets) }

        ws.webSocket("/game") { register("dec_a", "Dina", "WARRIOR") }
        ws.webSocket("/game") { register("dec_b", "Erik", "WARRIOR") }

        coroutineScope {
            val dinaReady = CompletableDeferred<Unit>()
            val erikReady = CompletableDeferred<Unit>()
            val declineDone = CompletableDeferred<Unit>()

            val sessionA = async {
                ws.webSocket("/game") {
                    login("dec_a")
                    dinaReady.complete(Unit)
                    erikReady.await()
                    delay(200)

                    send(encode(ClientMessage.PartyInvite("Erik")))
                    // Dina gets invite confirm, then after decline, decline notification
                    declineDone.await()
                    val declined = drainUntil({
                        it is ServerMessage.SystemMessage &&
                        (it as ServerMessage.SystemMessage).message.contains("declined")
                    })
                    assertNotNull(declined, "Dina should be told invite was declined")
                    delay(500)
                }
            }

            val sessionB = async {
                dinaReady.await()
                delay(200)
                ws.webSocket("/game") {
                    login("dec_b")
                    erikReady.complete(Unit)

                    val invite = drainUntil({ it is ServerMessage.PartyInviteReceived })
                    assertNotNull(invite, "Erik should receive invite")

                    send(encode(ClientMessage.PartyDecline("Dina")))

                    val sysMsg = drainUntil({ it is ServerMessage.SystemMessage })
                    assertNotNull(sysMsg, "Erik should get decline confirmation")
                    assertIs<ServerMessage.SystemMessage>(sysMsg)
                    assertTrue(sysMsg.message.contains("declined"))

                    declineDone.complete(Unit)
                }
            }

            sessionA.await()
            sessionB.await()
        }
    }

    // ── Leave / Disband ─────────────────────────────────────────────

    @Test
    fun `leaving a 2-person party disbands it`() = testApplication {
        application { module(jdbcUrl = testDbUrl()) }
        val ws = createClient { install(WebSockets) }

        ws.webSocket("/game") { register("leave_a", "Fiona", "WARRIOR") }
        ws.webSocket("/game") { register("leave_b", "Gus", "WARRIOR") }

        coroutineScope {
            val fionaReady = CompletableDeferred<Unit>()
            val gusReady = CompletableDeferred<Unit>()
            val partyReady = CompletableDeferred<Unit>()
            val leftDone = CompletableDeferred<Unit>()

            val sessionA = async {
                ws.webSocket("/game") {
                    login("leave_a")
                    fionaReady.complete(Unit)
                    gusReady.await()
                    delay(200)

                    send(encode(ClientMessage.PartyInvite("Gus")))
                    drainUntil({ it is ServerMessage.PartyFormed })
                    partyReady.complete(Unit)

                    send(encode(ClientMessage.PartyLeave))
                    val sysMsg = drainUntil({
                        it is ServerMessage.SystemMessage &&
                        (it as ServerMessage.SystemMessage).message.contains("left")
                    })
                    assertNotNull(sysMsg, "Fiona should get leave confirmation")
                    leftDone.complete(Unit)
                    delay(500)
                }
            }

            val sessionB = async {
                fionaReady.await()
                delay(200)
                ws.webSocket("/game") {
                    login("leave_b")
                    gusReady.complete(Unit)

                    val invite = drainUntil({ it is ServerMessage.PartyInviteReceived })
                    assertNotNull(invite)
                    send(encode(ClientMessage.PartyAccept("Fiona")))
                    drainUntil({ it is ServerMessage.PartyFormed })
                    partyReady.await()

                    leftDone.await()
                    val disbanded = drainUntil({ it is ServerMessage.PartyDisbanded })
                    assertNotNull(disbanded, "Gus should receive PartyDisbanded")
                }
            }

            sessionA.await()
            sessionB.await()
        }
    }

    // ── Kick ────────────────────────────────────────────────────────

    @Test
    fun `leader can kick a member from a 3-person party`() = testApplication {
        application { module(jdbcUrl = testDbUrl()) }
        val ws = createClient { install(WebSockets) }

        ws.webSocket("/game") { register("kick_a", "Hana", "WARRIOR") }
        ws.webSocket("/game") { register("kick_b", "Ivan", "WARRIOR") }
        ws.webSocket("/game") { register("kick_c", "Jade", "WARRIOR") }

        coroutineScope {
            val hanaReady = CompletableDeferred<Unit>()
            val ivanReady = CompletableDeferred<Unit>()
            val jadeReady = CompletableDeferred<Unit>()
            val partyOf3 = CompletableDeferred<Unit>()
            val kickDone = CompletableDeferred<Unit>()

            // Hana (leader)
            val sessionA = async {
                ws.webSocket("/game") {
                    login("kick_a")
                    hanaReady.complete(Unit)
                    ivanReady.await()
                    jadeReady.await()
                    delay(200)

                    // Invite Ivan
                    send(encode(ClientMessage.PartyInvite("Ivan")))
                    drainUntil({ it is ServerMessage.PartyFormed })

                    // Invite Jade
                    send(encode(ClientMessage.PartyInvite("Jade")))
                    drainUntil({ it is ServerMessage.PartyMemberJoined })

                    partyOf3.complete(Unit)

                    // Kick Ivan
                    send(encode(ClientMessage.PartyKick("Ivan")))
                    val left = drainUntil({ it is ServerMessage.PartyMemberLeft })
                    assertNotNull(left, "Hana should receive PartyMemberLeft for Ivan")
                    assertIs<ServerMessage.PartyMemberLeft>(left)
                    assertEquals("Ivan", left.memberName)
                    assertEquals("kicked", left.reason)

                    kickDone.complete(Unit)
                    delay(1000)
                }
            }

            // Ivan (will be kicked)
            val sessionB = async {
                hanaReady.await()
                delay(200)
                ws.webSocket("/game") {
                    login("kick_b")
                    ivanReady.complete(Unit)

                    val invite = drainUntil({ it is ServerMessage.PartyInviteReceived })
                    assertNotNull(invite)
                    send(encode(ClientMessage.PartyAccept("Hana")))
                    drainUntil({ it is ServerMessage.PartyFormed })

                    kickDone.await()
                    val disbanded = drainUntil({ it is ServerMessage.PartyDisbanded })
                    assertNotNull(disbanded, "Ivan should receive PartyDisbanded after kick")
                    assertTrue(disbanded!!.let { (it as ServerMessage.PartyDisbanded).reason.contains("kicked") })
                }
            }

            // Jade (stays in party)
            val sessionC = async {
                hanaReady.await()
                delay(400) // after Ivan
                ws.webSocket("/game") {
                    login("kick_c")
                    jadeReady.complete(Unit)

                    val invite = drainUntil({ it is ServerMessage.PartyInviteReceived })
                    assertNotNull(invite)
                    send(encode(ClientMessage.PartyAccept("Hana")))
                    drainUntil({ it is ServerMessage.PartyFormed })
                    partyOf3.await()

                    kickDone.await()
                    val left = drainUntil({ it is ServerMessage.PartyMemberLeft })
                    assertNotNull(left, "Jade should see Ivan was kicked")
                    assertIs<ServerMessage.PartyMemberLeft>(left)
                    assertEquals("Ivan", left.memberName)

                    delay(500)
                }
            }

            sessionA.await()
            sessionB.await()
            sessionC.await()
        }
    }

    // ── Follow System ───────────────────────────────────────────────

    @Test
    fun `follow works between party members in same room`() = testApplication {
        application { module(jdbcUrl = testDbUrl()) }
        val ws = createClient { install(WebSockets) }

        ws.webSocket("/game") { register("follow_a", "Kara", "WARRIOR") }
        ws.webSocket("/game") { register("follow_b", "Leo", "WARRIOR") }

        coroutineScope {
            val karaReady = CompletableDeferred<Unit>()
            val leoReady = CompletableDeferred<Unit>()
            val partyReady = CompletableDeferred<Unit>()
            val followDone = CompletableDeferred<Unit>()

            val sessionA = async {
                ws.webSocket("/game") {
                    login("follow_a")
                    karaReady.complete(Unit)
                    leoReady.await()
                    delay(200)

                    send(encode(ClientMessage.PartyInvite("Leo")))
                    drainUntil({ it is ServerMessage.PartyFormed })
                    partyReady.complete(Unit)
                    followDone.await()
                    delay(500)
                }
            }

            val sessionB = async {
                karaReady.await()
                delay(200)
                ws.webSocket("/game") {
                    login("follow_b")
                    leoReady.complete(Unit)

                    val invite = drainUntil({ it is ServerMessage.PartyInviteReceived })
                    assertNotNull(invite)
                    send(encode(ClientMessage.PartyAccept("Kara")))
                    drainUntil({ it is ServerMessage.PartyFormed })
                    partyReady.await()

                    // Follow Kara
                    send(encode(ClientMessage.Follow("Kara")))

                    val followUpdate = drainUntil({ it is ServerMessage.FollowUpdate })
                    assertNotNull(followUpdate, "Leo should receive FollowUpdate")
                    assertIs<ServerMessage.FollowUpdate>(followUpdate)
                    assertEquals("Kara", followUpdate.targetName)
                    assertEquals(com.neomud.shared.model.FollowState.ACTIVE, followUpdate.state)

                    followDone.complete(Unit)
                }
            }

            sessionA.await()
            sessionB.await()
        }
    }

    // ── Self-invite rejected ────────────────────────────────────────

    @Test
    fun `cannot invite yourself to a party`() = testApplication {
        application { module(jdbcUrl = testDbUrl()) }
        val ws = createClient { install(WebSockets) }

        ws.webSocket("/game") { register("self_inv", "Solo", "WARRIOR") }

        ws.webSocket("/game") {
            login("self_inv")
            send(encode(ClientMessage.PartyInvite("Solo")))
            val sysMsg = drainUntil({
                it is ServerMessage.SystemMessage &&
                (it as ServerMessage.SystemMessage).message.contains("can't invite yourself")
            })
            assertNotNull(sysMsg, "Should reject self-invite")
        }
    }

    // ── Party max size enforced ─────────────────────────────────────

    @Test
    fun `cannot invite when party is full`() = testApplication {
        application { module(jdbcUrl = testDbUrl()) }
        val ws = createClient { install(WebSockets) }

        ws.webSocket("/game") { register("full_a", "P1", "WARRIOR") }
        ws.webSocket("/game") { register("full_b", "P2", "WARRIOR") }
        ws.webSocket("/game") { register("full_c", "P3", "WARRIOR") }
        ws.webSocket("/game") { register("full_d", "P4", "WARRIOR") }
        ws.webSocket("/game") { register("full_e", "P5", "WARRIOR") }

        coroutineScope {
            val p1Ready = CompletableDeferred<Unit>()
            val p2Ready = CompletableDeferred<Unit>()
            val p3Ready = CompletableDeferred<Unit>()
            val p4Ready = CompletableDeferred<Unit>()
            val p5Ready = CompletableDeferred<Unit>()
            val testDone = CompletableDeferred<Unit>()

            // P1 is leader
            val sessionA = async {
                ws.webSocket("/game") {
                    login("full_a")
                    p1Ready.complete(Unit)
                    p2Ready.await(); p3Ready.await(); p4Ready.await(); p5Ready.await()
                    delay(300)

                    send(encode(ClientMessage.PartyInvite("P2")))
                    drainUntil({ it is ServerMessage.PartyFormed })

                    delay(200)
                    send(encode(ClientMessage.PartyInvite("P3")))
                    drainUntil({ it is ServerMessage.PartyMemberJoined })

                    delay(200)
                    send(encode(ClientMessage.PartyInvite("P4")))
                    drainUntil({ it is ServerMessage.PartyMemberJoined })

                    delay(200)
                    // Try to invite P5 — party is full (4/4)
                    send(encode(ClientMessage.PartyInvite("P5")))
                    val sysMsg = drainUntil({
                        it is ServerMessage.SystemMessage &&
                        (it as ServerMessage.SystemMessage).message.contains("full")
                    })
                    assertNotNull(sysMsg, "Should be told party is full")

                    testDone.complete(Unit)
                    delay(1000)
                }
            }

            // P2-P4 accept invites
            val otherLogins = listOf(
                "full_b" to p2Ready,
                "full_c" to p3Ready,
                "full_d" to p4Ready
            )
            val others = otherLogins.mapIndexed { i, (user, ready) ->
                async {
                    p1Ready.await()
                    delay((200 + i * 200).toLong())
                    ws.webSocket("/game") {
                        login(user)
                        ready.complete(Unit)

                        val invite = drainUntil({ it is ServerMessage.PartyInviteReceived })
                        assertNotNull(invite, "$user should receive invite")
                        send(encode(ClientMessage.PartyAccept("P1")))
                        drainUntil({ it is ServerMessage.PartyFormed })
                        testDone.await()
                    }
                }
            }

            // P5 just stays logged in
            val sessionE = async {
                p1Ready.await()
                delay(800)
                ws.webSocket("/game") {
                    login("full_e")
                    p5Ready.complete(Unit)
                    testDone.await()
                }
            }

            sessionA.await()
            others.awaitAll()
            sessionE.await()
        }
    }

    // ── Disconnect Notifications ──────────────────────────────────────

    @Test
    fun `disconnect in 2-person party sends PartyDisbanded immediately`() = testApplication {
        application { module(jdbcUrl = testDbUrl()) }
        val ws = createClient { install(WebSockets) }

        ws.webSocket("/game") { register("dc2_a", "Vera", "WARRIOR") }
        ws.webSocket("/game") { register("dc2_b", "Walt", "WARRIOR") }

        coroutineScope {
            val veraReady = CompletableDeferred<Unit>()
            val waltReady = CompletableDeferred<Unit>()
            val partyFormed = CompletableDeferred<Unit>()
            val testDone = CompletableDeferred<Unit>()

            val sessionA = async {
                ws.webSocket("/game") {
                    login("dc2_a")
                    veraReady.complete(Unit)
                    waltReady.await()
                    delay(200)

                    send(encode(ClientMessage.PartyInvite("Walt")))
                    drainUntil({ it is ServerMessage.PartyFormed })
                    partyFormed.complete(Unit)

                    // Walt disconnects — Vera should receive PartyDisbanded immediately
                    val disbanded = drainUntil({ it is ServerMessage.PartyDisbanded })
                    assertNotNull(disbanded, "Vera should receive PartyDisbanded when Walt disconnects")

                    testDone.complete(Unit)
                    delay(500)
                }
            }

            val sessionB = async {
                veraReady.await()
                delay(200)
                ws.webSocket("/game") {
                    login("dc2_b")
                    waltReady.complete(Unit)

                    val invite = drainUntil({ it is ServerMessage.PartyInviteReceived })
                    assertNotNull(invite)
                    send(encode(ClientMessage.PartyAccept("Vera")))
                    drainUntil({ it is ServerMessage.PartyFormed })
                    partyFormed.await()
                    // Disconnect by exiting the block
                }
                testDone.await()
            }

            sessionA.await()
            sessionB.await()
        }
    }

    @Test
    fun `disconnect in 3-person party sends PartyMemberLeft immediately`() = testApplication {
        application { module(jdbcUrl = testDbUrl()) }
        val ws = createClient { install(WebSockets) }

        ws.webSocket("/game") { register("dc3_a", "Xena", "WARRIOR") }
        ws.webSocket("/game") { register("dc3_b", "Yuri", "WARRIOR") }
        ws.webSocket("/game") { register("dc3_c", "Zara", "WARRIOR") }

        coroutineScope {
            val xenaReady = CompletableDeferred<Unit>()
            val yuriReady = CompletableDeferred<Unit>()
            val zaraReady = CompletableDeferred<Unit>()
            val partyFormed = CompletableDeferred<Unit>()
            val yuriDisconnected = CompletableDeferred<Unit>()
            val testDone = CompletableDeferred<Unit>()

            val sessionA = async {
                ws.webSocket("/game") {
                    login("dc3_a")
                    xenaReady.complete(Unit)
                    yuriReady.await(); zaraReady.await()
                    delay(200)

                    send(encode(ClientMessage.PartyInvite("Yuri")))
                    drainUntil({ it is ServerMessage.PartyFormed })
                    delay(200)
                    send(encode(ClientMessage.PartyInvite("Zara")))
                    drainUntil({ it is ServerMessage.PartyMemberJoined })
                    partyFormed.complete(Unit)

                    // Yuri disconnects — Xena should receive PartyMemberLeft immediately
                    val left = drainUntil({ it is ServerMessage.PartyMemberLeft })
                    assertNotNull(left, "Xena should receive PartyMemberLeft when Yuri disconnects")
                    assertIs<ServerMessage.PartyMemberLeft>(left)
                    assertEquals("Yuri", left.memberName)
                    assertEquals("disconnected", left.reason)

                    testDone.complete(Unit)
                    delay(500)
                }
            }

            val sessionB = async {
                xenaReady.await()
                delay(200)
                ws.webSocket("/game") {
                    login("dc3_b")
                    yuriReady.complete(Unit)

                    val invite = drainUntil({ it is ServerMessage.PartyInviteReceived })
                    assertNotNull(invite)
                    send(encode(ClientMessage.PartyAccept("Xena")))
                    drainUntil({ it is ServerMessage.PartyFormed })
                    partyFormed.await()
                    // Disconnect
                }
                yuriDisconnected.complete(Unit)
                testDone.await()
            }

            val sessionC = async {
                xenaReady.await()
                delay(400)
                ws.webSocket("/game") {
                    login("dc3_c")
                    zaraReady.complete(Unit)

                    val invite = drainUntil({ it is ServerMessage.PartyInviteReceived })
                    assertNotNull(invite)
                    send(encode(ClientMessage.PartyAccept("Xena")))
                    drainUntil({ it is ServerMessage.PartyFormed })
                    partyFormed.await()

                    yuriDisconnected.await()
                    val left = drainUntil({ it is ServerMessage.PartyMemberLeft })
                    assertNotNull(left, "Zara should receive PartyMemberLeft when Yuri disconnects")

                    testDone.await()
                    delay(500)
                }
            }

            sessionA.await()
            sessionB.await()
            sessionC.await()
        }
    }

    // ── Disconnect Grace Period ─────────────────────────────────────

    @Test
    fun `disconnected member rejoins party within grace period`() = testApplication {
        application { module(jdbcUrl = testDbUrl()) }
        val ws = createClient { install(WebSockets) }

        ws.webSocket("/game") { register("grace_a", "Mira", "WARRIOR") }
        ws.webSocket("/game") { register("grace_b", "Nate", "WARRIOR") }
        ws.webSocket("/game") { register("grace_c", "Ora", "WARRIOR") }

        coroutineScope {
            val miraReady = CompletableDeferred<Unit>()
            val nateReady = CompletableDeferred<Unit>()
            val oraReady = CompletableDeferred<Unit>()
            val partyFormed = CompletableDeferred<Unit>()
            val nateDisconnected = CompletableDeferred<Unit>()
            val testDone = CompletableDeferred<Unit>()

            // Mira — leader, stays connected
            val sessionA = async {
                ws.webSocket("/game") {
                    login("grace_a")
                    miraReady.complete(Unit)
                    nateReady.await(); oraReady.await()
                    delay(200)

                    send(encode(ClientMessage.PartyInvite("Nate")))
                    drainUntil({ it is ServerMessage.PartyFormed })

                    delay(200)
                    send(encode(ClientMessage.PartyInvite("Ora")))
                    drainUntil({ it is ServerMessage.PartyMemberJoined })

                    partyFormed.complete(Unit)
                    testDone.await()
                    delay(500)
                }
            }

            // Ora — stays connected
            val sessionC = async {
                miraReady.await()
                delay(400)
                ws.webSocket("/game") {
                    login("grace_c")
                    oraReady.complete(Unit)

                    val invite = drainUntil({ it is ServerMessage.PartyInviteReceived })
                    assertNotNull(invite)
                    send(encode(ClientMessage.PartyAccept("Mira")))
                    drainUntil({ it is ServerMessage.PartyFormed })
                    testDone.await()
                    delay(500)
                }
            }

            // Nate — joins party, disconnects, reconnects
            val sessionB = async {
                miraReady.await()
                delay(200)
                ws.webSocket("/game") {
                    login("grace_b")
                    nateReady.complete(Unit)

                    val invite = drainUntil({ it is ServerMessage.PartyInviteReceived })
                    assertNotNull(invite)
                    send(encode(ClientMessage.PartyAccept("Mira")))
                    drainUntil({ it is ServerMessage.PartyFormed })
                    partyFormed.await()
                    // Disconnect
                }
                nateDisconnected.complete(Unit)

                delay(500) // let disconnect handler run

                // Reconnect
                ws.webSocket("/game") {
                    consumeCatalogSync()
                    send(encode(ClientMessage.Login("grace_b", "pass1234")))
                    val loginOk = recv()
                    assertIs<ServerMessage.LoginOk>(loginOk)

                    // Drain all post-login messages, looking for PartyInfo
                    val partyInfo = drainUntil({ it is ServerMessage.PartyInfo })
                    assertNotNull(partyInfo, "Nate should receive PartyInfo after reconnecting within grace")
                    assertIs<ServerMessage.PartyInfo>(partyInfo)
                    assertTrue(partyInfo.members.any { it.name == "Nate" })
                    assertTrue(partyInfo.members.any { it.name == "Mira" })
                    assertEquals("Mira", partyInfo.leaderId)

                    testDone.complete(Unit)
                }
            }

            sessionA.await()
            sessionB.await()
            sessionC.await()
        }
    }

    // ── Not in party errors ─────────────────────────────────────────

    @Test
    fun `party chat without party returns error`() = testApplication {
        application { module(jdbcUrl = testDbUrl()) }
        val ws = createClient { install(WebSockets) }

        ws.webSocket("/game") { register("nochat", "NoChatHero", "WARRIOR") }

        ws.webSocket("/game") {
            login("nochat")
            send(encode(ClientMessage.PartySay("hello?")))
            val sysMsg = drainUntil({
                it is ServerMessage.SystemMessage &&
                (it as ServerMessage.SystemMessage).message.contains("not in a party")
            })
            assertNotNull(sysMsg, "Should say not in a party")
        }
    }

    @Test
    fun `follow without party returns error`() = testApplication {
        application { module(jdbcUrl = testDbUrl()) }
        val ws = createClient { install(WebSockets) }

        ws.webSocket("/game") { register("nofollow_a", "NF1", "WARRIOR") }
        ws.webSocket("/game") { register("nofollow_b", "NF2", "WARRIOR") }

        coroutineScope {
            val nf1Ready = CompletableDeferred<Unit>()
            val testDone = CompletableDeferred<Unit>()

            val sessionA = async {
                ws.webSocket("/game") {
                    login("nofollow_a")
                    nf1Ready.complete(Unit)
                    testDone.await()
                    delay(500)
                }
            }

            val sessionB = async {
                nf1Ready.await()
                delay(200)
                ws.webSocket("/game") {
                    login("nofollow_b")
                    send(encode(ClientMessage.Follow("NF1")))
                    val err = drainUntil({
                        it is ServerMessage.SystemMessage || it is ServerMessage.FollowFailed
                    })
                    assertNotNull(err, "Should get error when following without party")
                    testDone.complete(Unit)
                }
            }

            sessionA.await()
            sessionB.await()
        }
    }
}
