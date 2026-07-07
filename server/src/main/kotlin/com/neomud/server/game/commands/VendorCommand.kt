package com.neomud.server.game.commands

import com.neomud.server.game.GameConfig
import com.neomud.server.game.MeditationUtils
import com.neomud.server.game.RestUtils
import com.neomud.server.game.StealthUtils
import com.neomud.server.game.npc.NpcManager
import com.neomud.server.persistence.repository.CoinRepository
import com.neomud.server.persistence.repository.InventoryRepository
import com.neomud.server.session.PlayerSession
import com.neomud.server.session.SessionManager
import com.neomud.server.world.ItemCatalog
import com.neomud.server.world.SkillCatalog
import com.neomud.shared.model.Coins
import com.neomud.shared.model.VendorItem
import com.neomud.shared.protocol.ServerMessage
import org.slf4j.LoggerFactory

class VendorCommand(
    private val npcManager: NpcManager,
    private val itemCatalog: ItemCatalog,
    private val inventoryRepository: InventoryRepository,
    private val coinRepository: CoinRepository,
    private val inventoryCommand: InventoryCommand,
    private val sessionManager: SessionManager,
    private val skillCatalog: SkillCatalog
) {
    private val logger = LoggerFactory.getLogger(VendorCommand::class.java)

    suspend fun handleInteract(session: PlayerSession) {
        val roomId = session.currentRoomId ?: return
        val playerName = session.playerName ?: return
        val player = session.player ?: return

        MeditationUtils.breakMeditation(session, "You stop meditating.")
        RestUtils.breakRest(session, "You stop resting.")
        StealthUtils.breakStealth(session, sessionManager, "Interacting with a vendor reveals your presence!")

        val vendor = npcManager.getVendorInRoom(roomId)
        if (vendor == null) {
            session.send(ServerMessage.SystemMessage("There is no vendor here."))
            return
        }

        val hasHaggle = skillCatalog.getSkill("HAGGLE")?.let { skill ->
            skill.classRestrictions.isEmpty() || player.characterClass in skill.classRestrictions
        } ?: false

        val charm = player.stats.charm
        val vendorItems = vendor.vendorItems.mapNotNull { itemId ->
            val item = itemCatalog.getItem(itemId) ?: return@mapNotNull null
            val buyPrice = Coins.buyPriceCopper(item.value, 1, charm, hasHaggle, GameConfig.Vendor.BUY_HAGGLE_MAX_DISCOUNT)
            VendorItem(item = item, price = Coins.fromCopper(buyPrice))
        }

        val playerCoins = coinRepository.getCoins(playerName)
        val playerInventory = inventoryRepository.getInventory(playerName)

        session.send(ServerMessage.VendorInfo(
            vendorName = vendor.name,
            items = vendorItems,
            playerCoins = playerCoins,
            playerInventory = playerInventory,
            playerCharm = charm,
            interactSound = vendor.interactSound,
            exitSound = vendor.exitSound,
            hasHaggle = hasHaggle
        ))
    }

    suspend fun handleBuy(session: PlayerSession, itemId: String, quantity: Int) {
        if (quantity < 1) return
        if (quantity > GameConfig.Vendor.MAX_BUY_QUANTITY) {
            session.send(ServerMessage.Error("You can only buy up to ${GameConfig.Vendor.MAX_BUY_QUANTITY} items at a time."))
            return
        }
        val roomId = session.currentRoomId ?: return
        val playerName = session.playerName ?: return
        val player = session.player ?: return

        val vendor = npcManager.getVendorInRoom(roomId)
        if (vendor == null) {
            session.send(ServerMessage.SystemMessage("There is no vendor here."))
            return
        }

        if (itemId !in vendor.vendorItems) {
            session.send(ServerMessage.Error("This vendor doesn't sell that item."))
            return
        }

        val item = itemCatalog.getItem(itemId)
        if (item == null) {
            session.send(ServerMessage.Error("Unknown item."))
            return
        }

        if (item.levelRequirement > player.level) {
            session.send(ServerMessage.Error("You need to be level ${item.levelRequirement} to buy ${item.name}."))
            return
        }

        val hasHaggle = skillCatalog.getSkill("HAGGLE")?.let { skill ->
            skill.classRestrictions.isEmpty() || player.characterClass in skill.classRestrictions
        } ?: false
        val unitPriceCopper = Coins.buyPriceCopper(item.value, 1, player.stats.charm, hasHaggle, GameConfig.Vendor.BUY_HAGGLE_MAX_DISCOUNT)
        val unitPrice = Coins.fromCopper(unitPriceCopper)
        val totalCost = Coins.fromCopper(Coins.buyPriceCopper(item.value, quantity, player.stats.charm, hasHaggle, GameConfig.Vendor.BUY_HAGGLE_MAX_DISCOUNT))
        val success = coinRepository.subtractCoins(playerName, totalCost)
        if (!success) {
            session.send(ServerMessage.Error("You can't afford ${item.name}. It costs ${totalCost.displayString()}."))
            return
        }

        val added = inventoryRepository.addItem(playerName, itemId, quantity)
        if (!added) {
            // Refund coins if item couldn't be added to inventory
            coinRepository.addCoins(playerName, totalCost)
            val errorMsg = if (item.stackable) {
                val currentQty = inventoryRepository.getInventory(playerName)
                    .find { it.itemId == itemId && !it.equipped }?.quantity ?: 0
                if (currentQty >= item.maxStack)
                    "You already have the maximum amount of ${item.name} (${item.maxStack})."
                else
                    "Failed to add ${item.name} to your inventory."
            } else {
                "Failed to add ${item.name} to your inventory."
            }
            session.send(ServerMessage.Error(errorMsg))
            return
        }
        logger.info("$playerName bought ${item.name} x$quantity for ${totalCost.displayString()}")

        // Auto-equip into empty slot if applicable (single item purchase with an equipment slot)
        var autoEquipMessage: String? = null
        if (quantity == 1 && item.slot.isNotEmpty()) {
            val currentEquipment = inventoryRepository.getEquippedItems(playerName)
            if (item.slot !in currentEquipment) {
                val equipped = inventoryRepository.equipItem(playerName, itemId, item.slot)
                if (equipped) {
                    autoEquipMessage = "You equip ${item.name} (${item.slot})."
                }
            }
        }

        val updatedCoins = coinRepository.getCoins(playerName)
        val updatedInventory = inventoryRepository.getInventory(playerName)
        val equipment = inventoryRepository.getEquippedItems(playerName)

        val buyMsg = if (quantity > 1) "You bought ${item.name} x$quantity for ${totalCost.displayString()} (${unitPrice.displayString()} each)."
                     else "You bought ${item.name} for ${totalCost.displayString()}."
        val fullMsg = if (autoEquipMessage != null) "$buyMsg $autoEquipMessage" else buyMsg

        session.send(ServerMessage.BuyResult(
            success = true,
            message = fullMsg,
            updatedCoins = updatedCoins,
            updatedInventory = updatedInventory,
            equipment = equipment
        ))

        // Also send inventory update to keep client state in sync
        inventoryCommand.sendInventoryUpdate(session)
    }

    suspend fun handleSell(session: PlayerSession, itemId: String, quantity: Int) {
        if (quantity < 1) return
        val roomId = session.currentRoomId ?: return
        val playerName = session.playerName ?: return
        val player = session.player ?: return

        val vendor = npcManager.getVendorInRoom(roomId)
        if (vendor == null) {
            session.send(ServerMessage.SystemMessage("There is no vendor here."))
            return
        }

        val item = itemCatalog.getItem(itemId)
        if (item == null) {
            session.send(ServerMessage.Error("Unknown item."))
            return
        }

        if (item.value <= 0) {
            session.send(ServerMessage.Error("${item.name} cannot be sold."))
            return
        }

        // Clamp quantity to actual unequipped amount to prevent sell exploit
        val inventory = inventoryRepository.getInventory(playerName)
        val availableQty = inventory.filter { it.itemId == itemId && !it.equipped }.sumOf { it.quantity }
        if (availableQty <= 0) {
            session.send(ServerMessage.Error("You don't have that item."))
            return
        }
        val actualQuantity = quantity.coerceAtMost(availableQty)

        val removed = inventoryRepository.removeItem(playerName, itemId, actualQuantity)
        if (!removed) {
            session.send(ServerMessage.Error("You don't have that item."))
            return
        }

        val hasHaggle = skillCatalog.getSkill("HAGGLE")?.let { skill ->
            skill.classRestrictions.isEmpty() || player.characterClass in skill.classRestrictions
        } ?: false
        val unitSellCopper = Coins.sellPriceCopper(
            item.value, 1, player.stats.charm, hasHaggle,
            basePercent = GameConfig.Vendor.SELL_BASE_PERCENT,
            charmScale = GameConfig.Vendor.SELL_CHARM_SCALE,
            haggleBonusScale = GameConfig.Vendor.SELL_HAGGLE_BONUS_SCALE,
            maxPercent = GameConfig.Vendor.SELL_MAX_PERCENT
        )
        val unitSellPrice = Coins.fromCopper(unitSellCopper)
        val sellPriceCopper = Coins.sellPriceCopper(
            item.value, actualQuantity, player.stats.charm, hasHaggle,
            basePercent = GameConfig.Vendor.SELL_BASE_PERCENT,
            charmScale = GameConfig.Vendor.SELL_CHARM_SCALE,
            haggleBonusScale = GameConfig.Vendor.SELL_HAGGLE_BONUS_SCALE,
            maxPercent = GameConfig.Vendor.SELL_MAX_PERCENT
        )
        val sellPrice = Coins.fromCopper(sellPriceCopper)
        coinRepository.addCoins(playerName, sellPrice)
        logger.info("$playerName sold ${item.name} x$actualQuantity for ${sellPrice.displayString()}")

        val updatedCoins = coinRepository.getCoins(playerName)
        val updatedInventory = inventoryRepository.getInventory(playerName)
        val equipment = inventoryRepository.getEquippedItems(playerName)

        session.send(ServerMessage.SellResult(
            success = true,
            message = if (actualQuantity > 1) "You sold ${item.name} x$actualQuantity for ${sellPrice.displayString()} (${unitSellPrice.displayString()} each)."
                      else "You sold ${item.name} for ${sellPrice.displayString()}.",
            updatedCoins = updatedCoins,
            updatedInventory = updatedInventory,
            equipment = equipment
        ))

        inventoryCommand.sendInventoryUpdate(session)
    }
}
