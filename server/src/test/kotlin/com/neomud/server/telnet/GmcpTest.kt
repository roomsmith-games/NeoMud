package com.neomud.server.telnet

import com.neomud.shared.model.*
import com.neomud.shared.protocol.ServerMessage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GmcpTest {

    // ─── Frame decoding helper ────────────────────────────────────────────────

    /** Decodes an `IAC SB GMCP "<pkg> <json>" IAC SE` frame into (package, jsonText). */
    private fun decode(frame: ByteArray): Pair<String, String> {
        assertEquals(Telnet.IAC, frame[0], "frame must start with IAC")
        assertEquals(Telnet.SB, frame[1], "frame must have SB")
        assertEquals(Telnet.GMCP, frame[2], "frame must target GMCP option")
        assertEquals(Telnet.IAC, frame[frame.size - 2], "frame must end with IAC SE")
        assertEquals(Telnet.SE, frame[frame.size - 1], "frame must end with SE")
        val body = String(frame.copyOfRange(3, frame.size - 2), Charsets.UTF_8)
        val sp = body.indexOf(' ')
        return body.substring(0, sp) to body.substring(sp + 1)
    }

    private fun state() = TelnetSessionState()

    private fun testPlayer() = Player(
        name = "Aldric", characterClass = "Warrior",
        stats = Stats(strength = 14, agility = 10, intellect = 8, willpower = 9, health = 12, charm = 7),
        currentHp = 60, maxHp = 80, currentMp = 20, maxMp = 30, level = 5, currentRoomId = "z:r"
    )

    // ─── Handshake ────────────────────────────────────────────────────────────

    @Test fun handshakeSendsHelloThenSupports() {
        val frames = Gmcp.handshakeFrames()
        assertEquals(2, frames.size)
        val (helloPkg, helloJson) = decode(frames[0])
        assertEquals("Core.Hello", helloPkg)
        val hello = Json.parseToJsonElement(helloJson).jsonObject
        assertEquals("NeoMud", hello["client"]!!.jsonPrimitive.content)

        val (supPkg, supJson) = decode(frames[1])
        assertEquals("Core.Supports.Set", supPkg)
        val supports = Json.parseToJsonElement(supJson) as JsonArray
        assertTrue(supports.any { it.jsonPrimitive.content == "Room.Info 1" })
        assertTrue(supports.any { it.jsonPrimitive.content == "Char.Vitals 1" })
    }

    // ─── Char.Stats + Char.Vitals on login ────────────────────────────────────

    @Test fun loginEmitsStatsAndVitals() {
        val s = state()
        val msg = ServerMessage.LoginOk(testPlayer())
        s.update(msg)
        val decoded = Gmcp.framesFor(msg, s).map { decode(it) }
        val pkgs = decoded.map { it.first }
        assertTrue("Char.Stats" in pkgs)
        assertTrue("Char.Vitals" in pkgs)

        val stats = Json.parseToJsonElement(decoded.first { it.first == "Char.Stats" }.second).jsonObject
        assertEquals("Aldric", stats["name"]!!.jsonPrimitive.content)
        assertEquals(5, stats["level"]!!.jsonPrimitive.content.toInt())
        assertEquals(14, stats["str"]!!.jsonPrimitive.content.toInt())

        val vitals = Json.parseToJsonElement(decoded.first { it.first == "Char.Vitals" }.second).jsonObject
        assertEquals(60, vitals["hp"]!!.jsonPrimitive.content.toInt())
        assertEquals(80, vitals["maxhp"]!!.jsonPrimitive.content.toInt())
    }

    // ─── Char.Vitals reflects absorbed HP changes ─────────────────────────────

    @Test fun itemUsedEmitsUpdatedVitals() {
        val s = state()
        s.update(ServerMessage.LoginOk(testPlayer()))
        val heal = ServerMessage.ItemUsed("Potion", "You heal.", newHp = 78, newMp = 25)
        s.update(heal)
        val vitals = Gmcp.framesFor(heal, s).map { decode(it) }.first { it.first == "Char.Vitals" }
        val obj = Json.parseToJsonElement(vitals.second).jsonObject
        assertEquals(78, obj["hp"]!!.jsonPrimitive.content.toInt())
        assertEquals(25, obj["mp"]!!.jsonPrimitive.content.toInt())
    }

    // ─── Room.Info ────────────────────────────────────────────────────────────

    @Test fun roomInfoEmitsRoomPackage() {
        val s = state()
        val r = Room(id = "millhaven:square", name = "Town Square", description = "",
            exits = mapOf(Direction.NORTH to "millhaven:road", Direction.EAST to "millhaven:market"),
            zoneId = "millhaven", x = 0, y = 0)
        val msg = ServerMessage.RoomInfo(r, emptyList(), emptyList())
        s.update(msg)
        val (pkg, jsonText) = Gmcp.framesFor(msg, s).map { decode(it) }.single()
        assertEquals("Room.Info", pkg)
        val obj = Json.parseToJsonElement(jsonText).jsonObject
        assertEquals("millhaven:square", obj["id"]!!.jsonPrimitive.content)
        assertEquals("Town Square", obj["name"]!!.jsonPrimitive.content)
        assertEquals("millhaven", obj["zone"]!!.jsonPrimitive.content)
        val exits = obj["exits"]!!.jsonArray.map { it.jsonPrimitive.content }.toSet()
        assertEquals(setOf("north", "east"), exits)
    }

    // ─── Char.Items.List ──────────────────────────────────────────────────────

    @Test fun inventoryEmitsItemsWithCatalogNames() {
        val s = state()
        s.itemCatalog = mapOf("item:sword" to Item("item:sword", "Iron Sword", "", "weapon", "weapon"))
        val inv = listOf(InventoryItem("item:sword", quantity = 1), InventoryItem("item:unknown", quantity = 3))
        val msg = ServerMessage.InventoryUpdate(inv, emptyMap(), Coins())
        s.update(msg)
        val (pkg, jsonText) = Gmcp.framesFor(msg, s).map { decode(it) }.single()
        assertEquals("Char.Items.List", pkg)
        val obj = Json.parseToJsonElement(jsonText).jsonObject
        assertEquals("inv", obj["location"]!!.jsonPrimitive.content)
        val items = obj["items"]!!.jsonArray
        assertEquals(2, items.size)
        assertEquals("Iron Sword", items[0].jsonObject["name"]!!.jsonPrimitive.content)
        // Falls back to id when the catalog has no name.
        assertEquals("item:unknown", items[1].jsonObject["name"]!!.jsonPrimitive.content)
        assertEquals(3, items[1].jsonObject["qty"]!!.jsonPrimitive.content.toInt())
    }

    // ─── Map.Info ─────────────────────────────────────────────────────────────

    @Test fun mapDataEmitsMapInfo() {
        val s = state()
        val rooms = listOf(
            MapRoom(id = "z:a", name = "A", x = 0, y = 0, exits = mapOf(Direction.EAST to "z:b")),
            MapRoom(id = "z:b", name = "B", x = 1, y = 0, exits = mapOf(Direction.WEST to "z:a")),
        )
        val msg = ServerMessage.MapData(rooms, "z:a")
        val (pkg, jsonText) = Gmcp.framesFor(msg, s).map { decode(it) }.single()
        assertEquals("Map.Info", pkg)
        val obj = Json.parseToJsonElement(jsonText).jsonObject
        assertEquals("z:a", obj["id"]!!.jsonPrimitive.content)
        assertEquals(2, obj["rooms"]!!.jsonArray.size)
    }

    // ─── Silent messages ──────────────────────────────────────────────────────

    @Test fun silentMessageEmitsNothing() {
        val s = state()
        assertTrue(Gmcp.framesFor(ServerMessage.Pong, s).isEmpty())
        assertTrue(Gmcp.framesFor(ServerMessage.ItemCatalogSync(emptyList()), s).isEmpty())
    }

    // ─── Snapshot on GMCP enable ──────────────────────────────────────────────

    @Test fun snapshotAfterLoginAndRoomPushesVitalsStatsAndRoom() {
        val s = state()
        s.update(ServerMessage.LoginOk(testPlayer()))
        s.update(ServerMessage.RoomInfo(
            Room(id = "z:sq", name = "Square", description = "", exits = emptyMap(), zoneId = "z", x = 0, y = 0),
            emptyList(), emptyList()))
        val pkgs = Gmcp.snapshotFrames(s).map { decode(it).first }
        assertTrue("Char.Stats" in pkgs)
        assertTrue("Char.Vitals" in pkgs)
        assertTrue("Room.Info" in pkgs)
    }

    @Test fun snapshotBeforeLoginIsEmpty() {
        // Very early negotiation: nothing cached yet, so nothing to snapshot.
        assertTrue(Gmcp.snapshotFrames(state()).isEmpty())
    }

    // ─── Char.Stats re-emitted from cache after a stat train ──────────────────

    @Test fun statTrainedReEmitsUpdatedStatsFromCache() {
        val s = state()
        s.update(ServerMessage.LoginOk(testPlayer()))
        val train = ServerMessage.StatTrained("strength", newValue = 20, cpSpent = 6, remainingCp = 0,
            currentHp = 60, maxHp = 80, currentMp = 20, maxMp = 30)
        s.update(train)
        val statsFrame = Gmcp.framesFor(train, s).map { decode(it) }.firstOrNull { it.first == "Char.Stats" }
        assertNotNull(statsFrame)
        val obj = Json.parseToJsonElement(statsFrame.second).jsonObject
        assertEquals(20, obj["str"]!!.jsonPrimitive.content.toInt())
    }
}
