package com.neomud.server.game.commands

import com.neomud.server.game.MeditationUtils
import com.neomud.server.game.RestUtils
import com.neomud.server.game.StealthUtils
import com.neomud.server.game.npc.NpcManager
import com.neomud.server.persistence.repository.InventoryRepository
import com.neomud.server.persistence.repository.PlayerFlagsRepository
import com.neomud.server.session.PlayerSession
import com.neomud.server.session.SessionManager
import com.neomud.shared.protocol.ServerMessage
import org.slf4j.LoggerFactory

/**
 * Handles `/interact <npcId>` for lore/quest NPCs that have a `dialogueScript`
 * configured. Trainer/vendor/crafter NPCs continue to use their existing
 * dedicated handlers ([TrainerCommand], [VendorCommand], [CraftCommand]) —
 * this is the path for everything else.
 *
 * **Dialogue is repeatable**: every interaction re-sends the same blocking
 * dialogue. The client renders [ServerMessage.NpcDialogue] in its tutorial
 * modal slot, but the server does NOT persist via `PlayerDiscoveryTable` so
 * subsequent interactions still fire.
 *
 * **Item grant is one-time**: if the NPC has `grantItemId` and `grantItemFlag`
 * configured, the first successful interaction grants the item and sets the
 * flag in [PlayerFlagsRepository]. Subsequent interactions still send the
 * dialogue but skip the grant. If the inventory add fails (no slot, stack
 * overflow), the flag is NOT set so the player can retry.
 */
class DialogueCommand(
    private val npcManager: NpcManager,
    private val sessionManager: SessionManager,
    private val inventoryRepository: InventoryRepository,
    private val playerFlagsRepository: PlayerFlagsRepository,
    private val inventoryCommand: InventoryCommand
) {
    private val logger = LoggerFactory.getLogger(DialogueCommand::class.java)

    suspend fun execute(session: PlayerSession, npcId: String) {
        val roomId = session.currentRoomId ?: return
        val player = session.player ?: return
        val playerName = session.playerName ?: return

        // Same etiquette as other interact commands.
        MeditationUtils.breakMeditation(session, "You stop meditating.")
        RestUtils.breakRest(session, "You stop resting.")
        StealthUtils.breakStealth(session, sessionManager, "Talking reveals your presence!")

        val npc = npcManager.getNpcState(npcId)
        if (npc == null || npc.currentRoomId != roomId) {
            session.send(ServerMessage.SystemMessage("They are not here."))
            return
        }
        if (npc.dialogueScript.isBlank()) {
            session.send(ServerMessage.SystemMessage("${npc.name} has nothing to say."))
            return
        }

        // Always send the dialogue first — the player's expectation is "click NPC, see dialogue".
        session.send(ServerMessage.NpcDialogue(
            npcId = npc.id,
            npcName = npc.name,
            content = npc.dialogueScript
        ))

        // One-time item grant.
        if (npc.grantItemId.isNotBlank() && npc.grantItemFlag.isNotBlank()) {
            val alreadyGranted = playerFlagsRepository.getFlag(playerName, npc.grantItemFlag) != null
            if (alreadyGranted) return

            val granted = try {
                inventoryRepository.addItem(playerName, npc.grantItemId, 1)
            } catch (e: Exception) {
                logger.warn("Item grant failed for $playerName / ${npc.grantItemId}: ${e.message}")
                false
            }
            if (!granted) {
                session.send(ServerMessage.SystemMessage("Your inventory is too full to receive ${npc.name}'s gift."))
                return
            }

            try {
                playerFlagsRepository.setFlag(playerName, npc.grantItemFlag, "1")
            } catch (e: Exception) {
                // Flag-set failure means re-interaction will re-grant — log loudly so it's caught
                // before the duplicate is exploited.
                logger.warn("Failed to persist grant flag ${npc.grantItemFlag} for $playerName: ${e.message}")
            }

            inventoryCommand.sendInventoryUpdate(session)
        }
    }
}
