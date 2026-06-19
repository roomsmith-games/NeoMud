package com.neomud.server.telnet

import com.neomud.shared.model.Direction
import com.neomud.shared.protocol.ServerMessage

object AsciiMap {

    fun render(msg: ServerMessage.MapData, useColor: Boolean = false): List<String> {
        val playerRoom = msg.rooms.find { it.id == msg.playerRoomId } ?: return emptyList()
        val zLevel = playerRoom.z
        val floorRooms = msg.rooms.filter { it.z == zLevel }
        val px = playerRoom.x
        val py = playerRoom.y
        val radius = 2

        val lines = mutableListOf<String>()
        for (dy in radius downTo -radius) {
            val gy = py + dy
            val roomRow = StringBuilder()
            val connRow = StringBuilder()

            for (dx in -radius..radius) {
                val gx = px + dx
                val room = floorRooms.find { it.x == gx && it.y == gy }
                val isVisited = room != null && room.id in msg.visitedRooms

                val cell = if (!isVisited) "   " else when {
                    room.id == msg.playerRoomId -> "[+]"
                    room.hasPlayers -> "[*]"
                    room.exits.containsKey(Direction.UP) && room.exits.containsKey(Direction.DOWN) -> "[±]"
                    room.exits.containsKey(Direction.UP) -> "[^]"
                    room.exits.containsKey(Direction.DOWN) -> "[v]"
                    else -> "[ ]"
                }
                roomRow.append(cell)
                if (dx < radius) {
                    val hasEast = room != null && isVisited && room.exits.containsKey(Direction.EAST)
                    roomRow.append(if (hasEast) "--" else "  ")
                }

                val hasSouth = room != null && isVisited && room.exits.containsKey(Direction.SOUTH)
                connRow.append(if (hasSouth) " | " else "   ")
                if (dx < radius) connRow.append("  ")
            }

            lines.add(roomRow.toString().trimEnd())
            if (dy > -radius) {
                val conn = connRow.toString().trimEnd()
                lines.add(if (conn.isBlank()) "" else conn)
            }
        }

        while (lines.isNotEmpty() && lines.last().isBlank()) lines.removeLast()
        lines.add("")
        lines.add(Ansi.c("[+]You  [ ]Room  [*]Players  [^]Up  [v]Down", Ansi.GRAY, useColor))
        return lines
    }

    fun renderAtlas(msg: ServerMessage.AtlasData, useColor: Boolean = false): List<String> {
        val zones = msg.rooms.groupBy { it.zoneId }
        return buildList {
            add(Ansi.c("=== World Atlas ===", Ansi.BOLD_CYAN, useColor))
            zones.forEach { (zoneId, rooms) ->
                val name = (msg.zoneNames[zoneId] ?: zoneId).padEnd(20)
                add(Ansi.c("  $name (${rooms.size} rooms)", Ansi.CYAN, useColor))
            }
            add(Ansi.c("Type 'map' for your local minimap.", Ansi.GRAY, useColor))
        }
    }
}
