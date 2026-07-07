package com.neomud.server.telnet

import com.neomud.shared.protocol.ServerMessage

/**
 * MSDP (Mud Server Data Protocol) encoder — the TinTin++ counterpart to [Gmcp].
 *
 * Wire format: `IAC SB MSDP (MSDP_VAR <name> MSDP_VAL <value>)… IAC SE`. We push a flat set of
 * scalar variables (no nested tables) whenever vitals or the current room change. Only ever sent
 * after the client requests MSDP with `IAC DO MSDP`.
 */
object Msdp {
    /** Variables reported on any relevant state change. Keep the whole set together so the
     *  client's variable cache stays coherent regardless of which message triggered the push. */
    fun framesFor(message: ServerMessage, state: TelnetSessionState): List<ByteArray> = when (message) {
        is ServerMessage.LoginOk,
        is ServerMessage.LevelUp,
        is ServerMessage.StatTrained,
        is ServerMessage.CombatHit,
        is ServerMessage.SpellEffect,
        is ServerMessage.SkillEffect,
        is ServerMessage.ItemUsed,
        is ServerMessage.EffectTick,
        is ServerMessage.MeditateUpdate,
        is ServerMessage.RestUpdate,
        is ServerMessage.RoomInfo,
        is ServerMessage.MoveOk -> listOf(variablesFrame(state))
        else -> emptyList()
    }

    private fun variablesFrame(state: TelnetSessionState): ByteArray {
        val vars = linkedMapOf(
            "HEALTH" to state.currentHp.toString(),
            "HEALTH_MAX" to state.maxHp.toString(),
            "MANA" to state.currentMp.toString(),
            "MANA_MAX" to state.maxMp.toString(),
            "LEVEL" to state.playerLevel.toString(),
            "ROOM_NAME" to (state.currentRoomName ?: ""),
            "ROOM_EXITS" to state.currentRoomExits.joinToString(",") { it.name.lowercase() },
        )
        val body = ArrayList<Byte>()
        for ((name, value) in vars) {
            body.add(Telnet.MSDP_VAR)
            body.addAll(name.toByteArray(Charsets.US_ASCII).toList())
            body.add(Telnet.MSDP_VAL)
            body.addAll(value.toByteArray(Charsets.UTF_8).toList())
        }
        return Telnet.subNegotiationFrame(Telnet.MSDP, body.toByteArray())
    }
}
