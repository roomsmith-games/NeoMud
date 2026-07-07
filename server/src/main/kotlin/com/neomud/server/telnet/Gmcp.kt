package com.neomud.server.telnet

import com.neomud.shared.model.Direction
import com.neomud.shared.model.Player
import com.neomud.shared.protocol.ServerMessage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * GMCP (Generic MUD Communication Protocol) encoder.
 *
 * GMCP rides on a telnet subnegotiation channel — `IAC SB GMCP "Package.Name {json}" IAC SE` —
 * alongside the normal text stream. Rich MUD clients (Mudlet, Mudslinger) use it to drive their
 * automapper, health bars, and inventory panels from the same data the graphical client renders.
 *
 * We only ever *send* GMCP; incoming client GMCP (Core.Supports.Set, External.Discord, …) is
 * silently ignored by [IacLineReader]. Packages are derived from cached [TelnetSessionState] and
 * the triggering [ServerMessage]. UTF-8-encoded JSON never contains a 0xFF byte, so no IAC
 * escaping of the payload is required.
 */
object Gmcp {
    private val json = Json { encodeDefaults = false }

    /** The GMCP packages we advertise support for in Core.Supports.Set. */
    private val SUPPORTED = listOf(
        "Char 1", "Char.Vitals 1", "Char.Stats 1",
        "Room 1", "Room.Info 1", "Char.Items 1", "Map 1",
    )

    /** Wraps `Package.Name {json}` in an `IAC SB GMCP … IAC SE` frame. */
    fun frame(pkg: String, payload: JsonElement): ByteArray {
        val text = "$pkg ${json.encodeToString(JsonElement.serializer(), payload)}"
        return Telnet.subNegotiationFrame(Telnet.GMCP, text.toByteArray(Charsets.UTF_8))
    }

    /**
     * Sent once, immediately after we answer `IAC DO GMCP` with `IAC WILL GMCP`.
     * Identifies the server and declares which packages we push.
     */
    fun handshakeFrames(): List<ByteArray> = listOf(
        frame("Core.Hello", buildJsonObject {
            put("client", "NeoMud")
            put("version", "1.0")
        }),
        frame("Core.Supports.Set", JsonArray(SUPPORTED.map { JsonPrimitive(it) })),
    )

    /**
     * Full snapshot of the current cached state, pushed the moment GMCP is enabled.
     *
     * A client's `IAC DO GMCP` sent at connect isn't processed until the command loop starts —
     * i.e. after the initial LoginOk/RoomInfo burst has already flowed by. Without this snapshot
     * Mudlet's health bar and mapper stay blank until the player's first action. Empty when nothing
     * is cached yet (very early negotiation), in which case the normal per-message path fills in.
     */
    fun snapshotFrames(state: TelnetSessionState): List<ByteArray> = buildList {
        if (state.playerName != null) {
            charStatsFromState(state)?.let { add(it) }
            add(charVitals(state))
        }
        if (state.currentRoomName != null) add(roomInfo(state))
        if (state.inventory.isNotEmpty()) add(charItems(state))
    }

    /**
     * GMCP packages triggered by an outgoing [message], sourced from the already-updated [state].
     * Returns an empty list for messages with no GMCP mapping.
     */
    fun framesFor(message: ServerMessage, state: TelnetSessionState): List<ByteArray> = buildList {
        when (message) {
            is ServerMessage.LoginOk -> {
                add(charStats(message.player))
                add(charVitals(state))
                state.currentRoomName?.let { add(roomInfo(state)) }
            }
            // Level/stat changes: stats cached in state, level/hp already applied by update().
            is ServerMessage.LevelUp,
            is ServerMessage.StatTrained -> {
                charStatsFromState(state)?.let { add(it) }
                add(charVitals(state))
            }
            // Anything that can move the player's HP/MP re-pushes vitals.
            is ServerMessage.CombatHit,
            is ServerMessage.SpellEffect,
            is ServerMessage.SkillEffect,
            is ServerMessage.ItemUsed,
            is ServerMessage.EffectTick,
            is ServerMessage.MeditateUpdate,
            is ServerMessage.RestUpdate -> add(charVitals(state))
            is ServerMessage.RoomInfo -> add(roomInfo(state))
            is ServerMessage.MoveOk -> add(roomInfo(state))
            is ServerMessage.InventoryUpdate -> add(charItems(state))
            is ServerMessage.MapData -> add(mapInfo(message))
            else -> {}
        }
    }

    // ─── Package builders ─────────────────────────────────────────────────────

    private fun charStats(player: Player): ByteArray = frame("Char.Stats", buildJsonObject {
        put("name", player.name)
        put("class", player.characterClass)
        put("level", player.level)
        put("str", player.stats.strength)
        put("agi", player.stats.agility)
        put("int", player.stats.intellect)
        put("wil", player.stats.willpower)
        put("hea", player.stats.health)
        put("chr", player.stats.charm)
    })

    private fun charStatsFromState(state: TelnetSessionState): ByteArray? {
        val stats = state.playerStats ?: return null
        val name = state.playerName ?: return null
        return frame("Char.Stats", buildJsonObject {
            put("name", name)
            put("class", state.playerClass ?: "")
            put("level", state.playerLevel)
            put("str", stats.strength)
            put("agi", stats.agility)
            put("int", stats.intellect)
            put("wil", stats.willpower)
            put("hea", stats.health)
            put("chr", stats.charm)
        })
    }

    private fun charVitals(state: TelnetSessionState): ByteArray = frame("Char.Vitals", buildJsonObject {
        put("hp", state.currentHp)
        put("maxhp", state.maxHp)
        put("mp", state.currentMp)
        put("maxmp", state.maxMp)
    })

    private fun roomInfo(state: TelnetSessionState): ByteArray = frame("Room.Info", buildJsonObject {
        put("id", state.currentRoomId ?: "")
        put("name", state.currentRoomName ?: "")
        put("zone", state.currentRoomZone ?: "")
        put("exits", JsonArray(state.currentRoomExits.map { JsonPrimitive(dirName(it)) }))
    })

    private fun charItems(state: TelnetSessionState): ByteArray = frame("Char.Items.List", buildJsonObject {
        put("location", "inv")
        put("items", buildJsonArray {
            for (item in state.inventory) {
                add(buildJsonObject {
                    put("id", item.itemId)
                    put("name", state.itemCatalog[item.itemId]?.name ?: item.itemId)
                    put("qty", item.quantity)
                })
            }
        })
    })

    private fun mapInfo(message: ServerMessage.MapData): ByteArray = frame("Map.Info", buildJsonObject {
        put("id", message.playerRoomId)
        put("rooms", buildJsonArray {
            for (room in message.rooms) {
                add(buildJsonObject {
                    put("id", room.id)
                    put("name", room.name)
                    put("x", room.x)
                    put("y", room.y)
                    put("z", room.z)
                    put("exits", JsonArray(room.exits.keys.map { JsonPrimitive(dirName(it)) }))
                })
            }
        })
    })

    private fun dirName(dir: Direction): String = dir.name.lowercase()
}
