package com.neomud.server.game.commands

import com.neomud.server.game.MeditationUtils
import com.neomud.server.game.RestUtils
import com.neomud.server.game.StealthUtils
import com.neomud.server.game.npc.NpcManager
import com.neomud.server.persistence.repository.InventoryRepository
import com.neomud.server.persistence.repository.PlayerFlagsRepository
import com.neomud.server.session.PlayerSession
import com.neomud.server.session.SessionManager
import com.neomud.server.world.ItemCatalog
import com.neomud.shared.protocol.ServerMessage
import org.slf4j.LoggerFactory

class DialogueCommand(
    private val npcManager: NpcManager,
    private val sessionManager: SessionManager,
    private val inventoryRepository: InventoryRepository,
    private val playerFlagsRepository: PlayerFlagsRepository,
    private val inventoryCommand: InventoryCommand,
    private val itemCatalog: ItemCatalog
) {
    private val logger = LoggerFactory.getLogger(DialogueCommand::class.java)

    suspend fun execute(session: PlayerSession, npcId: String) {
        val roomId = session.currentRoomId ?: return
        val player = session.player ?: return
        val playerName = session.playerName ?: return

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

        val isRepeatVisit = npc.grantItemId.isNotBlank() && npc.grantItemFlag.isNotBlank() &&
            playerFlagsRepository.getFlag(playerName, npc.grantItemFlag) != null

        val dialogueContent = if (isRepeatVisit && npc.repeatDialogueScript.isNotBlank()) {
            npc.repeatDialogueScript
        } else {
            npc.dialogueScript
        }

        session.send(ServerMessage.NpcDialogue(
            npcId = npc.id,
            npcName = npc.name,
            content = dialogueContent
        ))

        if (isRepeatVisit) return

        // One-time item grant.
        if (npc.grantItemId.isNotBlank() && npc.grantItemFlag.isNotBlank()) {
            val granted = try {
                inventoryRepository.addItem(playerName, npc.grantItemId, 1)
            } catch (e: Exception) {
                logger.warn("Item grant failed for $playerName / ${npc.grantItemId}: ${e.message}")
                false
            }
            if (!granted) {
                session.send(ServerMessage.SystemMessage("${npc.name}'s gift slips away — there is no room for it."))
                return
            }

            try {
                playerFlagsRepository.setFlag(playerName, npc.grantItemFlag, "1")
            } catch (e: Exception) {
                logger.warn("Failed to persist grant flag ${npc.grantItemFlag} for $playerName: ${e.message}")
            }

            val itemName = itemCatalog.getItem(npc.grantItemId)?.name ?: npc.grantItemId
            session.send(ServerMessage.SystemMessage("${npc.name} gives you $itemName."))
            inventoryCommand.sendInventoryUpdate(session)
        }
    }
}
