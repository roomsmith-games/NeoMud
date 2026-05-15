package com.neomud.server.game.commands

import com.neomud.server.game.MeditationUtils
import com.neomud.server.game.RestUtils
import com.neomud.server.game.StealthUtils
import com.neomud.server.game.inventory.RoomItemManager
import com.neomud.server.persistence.repository.CoinRepository
import com.neomud.server.persistence.repository.InventoryRepository
import com.neomud.server.session.PlayerSession
import com.neomud.server.session.SessionManager
import com.neomud.server.world.ItemCatalog
import com.neomud.shared.model.Coins
import com.neomud.shared.protocol.ServerMessage

class PickupCommand(
    private val roomItemManager: RoomItemManager,
    private val inventoryRepository: InventoryRepository,
    private val coinRepository: CoinRepository,
    private val itemCatalog: ItemCatalog,
    private val sessionManager: SessionManager,
    private val tutorialService: com.neomud.server.game.TutorialService? = null
) {
    suspend fun handlePickupItem(session: PlayerSession, itemId: String, quantity: Int) {
        if (quantity < 1) return
        val playerName = session.playerName ?: return
        val roomId = session.currentRoomId ?: return

        MeditationUtils.breakMeditation(session, "You stop meditating.")
        RestUtils.breakRest(session, "You stop resting.")
        StealthUtils.breakStealth(session, sessionManager, "Picking up items reveals your presence!")

        val removed = roomItemManager.removeItem(roomId, itemId, quantity)
        if (removed == 0) {
            session.send(ServerMessage.Error("Nothing to pick up."))
            return
        }

        val added = inventoryRepository.addItem(playerName, itemId, removed)
        if (!added) {
            // Could not add to inventory (stack full) — return items to ground
            roomItemManager.addItems(roomId, listOf(com.neomud.shared.model.GroundItem(itemId, removed)))
            val itemName = itemCatalog.getItem(itemId)?.name ?: itemId
            session.send(ServerMessage.Error("Your inventory is full for $itemName."))
            return
        }

        val itemName = itemCatalog.getItem(itemId)?.name ?: itemId
        session.send(ServerMessage.PickupResult(itemName, removed))

        // tut_inventory: first item pickup
        tutorialService?.trySend(session, "tut_inventory")

        // Send updated inventory to the picker
        sendInventoryUpdate(session, playerName)

        // Broadcast updated ground state to entire room
        broadcastRoomItems(roomId)
    }

    suspend fun handlePickupCoins(session: PlayerSession, coinType: String) {
        val playerName = session.playerName ?: return
        val roomId = session.currentRoomId ?: return

        val validCoinTypes = setOf("copper", "silver", "gold", "platinum", "all")
        if (coinType !in validCoinTypes) {
            session.send(ServerMessage.Error("Invalid coin type. Use: copper, silver, gold, platinum, or all."))
            return
        }

        MeditationUtils.breakMeditation(session, "You stop meditating.")
        RestUtils.breakRest(session, "You stop resting.")
        StealthUtils.breakStealth(session, sessionManager, "Picking up coins reveals your presence!")

        if (coinType == "all") {
            val coins = roomItemManager.removeAllCoins(roomId)
            if (coins == Coins()) {
                session.send(ServerMessage.Error("No coins to pick up."))
                return
            }
            coinRepository.addCoins(playerName, coins)
            val total = coins.copper + coins.silver + coins.gold + coins.platinum
            session.send(ServerMessage.PickupResult("coins", total, isCoin = true))
        } else {
            val amount = roomItemManager.removeCoins(roomId, coinType)
            if (amount == 0) {
                session.send(ServerMessage.Error("No $coinType to pick up."))
                return
            }
            val coins = when (coinType) {
                "copper" -> Coins(copper = amount)
                "silver" -> Coins(silver = amount)
                "gold" -> Coins(gold = amount)
                "platinum" -> Coins(platinum = amount)
                else -> return
            }
            coinRepository.addCoins(playerName, coins)
            session.send(ServerMessage.PickupResult(coinType, amount, isCoin = true))
        }

        sendInventoryUpdate(session, playerName)
        broadcastRoomItems(roomId)
    }

    private suspend fun sendInventoryUpdate(session: PlayerSession, playerName: String) {
        val inventory = inventoryRepository.getInventory(playerName)
        val equipment = inventoryRepository.getEquippedItems(playerName)
        val coins = coinRepository.getCoins(playerName)
        session.send(ServerMessage.InventoryUpdate(inventory, equipment, coins))
    }

    private suspend fun broadcastRoomItems(roomId: String) {
        val items = roomItemManager.getGroundItems(roomId)
        val coins = roomItemManager.getGroundCoins(roomId)
        sessionManager.broadcastToRoom(roomId, ServerMessage.RoomItemsUpdate(items, coins))
    }
}
