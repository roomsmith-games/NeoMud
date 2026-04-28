package com.neomud.server.persistence.repository

import com.neomud.server.persistence.tables.InventoryTable
import com.neomud.server.world.ItemCatalog
import com.neomud.shared.model.InventoryItem
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update

open class InventoryRepository(private val itemCatalog: ItemCatalog) {

    open fun getInventory(playerName: String): List<InventoryItem> = transaction {
        InventoryTable.selectAll().where {
            InventoryTable.playerName eq playerName
        }.map { row ->
            InventoryItem(
                itemId = row[InventoryTable.itemId],
                quantity = row[InventoryTable.quantity],
                equipped = row[InventoryTable.equipped],
                slot = row[InventoryTable.slot]
            )
        }
    }

    open fun addItem(playerName: String, itemId: String, quantity: Int = 1): Boolean = transaction {
        val item = itemCatalog.getItem(itemId) ?: return@transaction false

        if (item.stackable) {
            // Try to stack with existing unequipped item
            val existing = InventoryTable.selectAll().where {
                (InventoryTable.playerName eq playerName) and
                (InventoryTable.itemId eq itemId) and
                (InventoryTable.equipped eq false)
            }.firstOrNull()

            if (existing != null) {
                val currentQty = existing[InventoryTable.quantity]
                if (currentQty >= item.maxStack) {
                    // Already at max stack — cannot add any more
                    return@transaction false
                }
                val canAdd = (item.maxStack - currentQty).coerceAtMost(quantity)
                if (canAdd <= 0) return@transaction false
                InventoryTable.update({
                    (InventoryTable.id eq existing[InventoryTable.id])
                }) {
                    it[InventoryTable.quantity] = currentQty + canAdd
                }
                // Return false if not all items could be added (overflow)
                return@transaction canAdd >= quantity
            }
        }

        // Insert new entry (clamp to maxStack for stackable items)
        val insertQty = if (item.stackable) quantity.coerceAtMost(item.maxStack) else quantity
        InventoryTable.insert {
            it[InventoryTable.playerName] = playerName
            it[InventoryTable.itemId] = itemId
            it[InventoryTable.quantity] = insertQty
            it[equipped] = false
            it[slot] = ""
        }
        // Return false if we had to clamp (overflow)
        insertQty >= quantity
    }

    open fun removeItem(playerName: String, itemId: String, quantity: Int = 1): Boolean = transaction {
        val existing = InventoryTable.selectAll().where {
            (InventoryTable.playerName eq playerName) and
            (InventoryTable.itemId eq itemId) and
            (InventoryTable.equipped eq false)
        }.firstOrNull() ?: return@transaction false

        val currentQty = existing[InventoryTable.quantity]
        if (currentQty <= quantity) {
            InventoryTable.deleteWhere {
                (InventoryTable.id eq existing[InventoryTable.id])
            }
        } else {
            InventoryTable.update({
                (InventoryTable.id eq existing[InventoryTable.id])
            }) {
                it[InventoryTable.quantity] = currentQty - quantity
            }
        }
        true
    }

    fun equipItem(playerName: String, itemId: String, slot: String): Boolean = transaction {
        // Find the unequipped item
        val itemRow = InventoryTable.selectAll().where {
            (InventoryTable.playerName eq playerName) and
            (InventoryTable.itemId eq itemId) and
            (InventoryTable.equipped eq false)
        }.firstOrNull() ?: return@transaction false

        // Unequip anything currently in that slot
        InventoryTable.update({
            (InventoryTable.playerName eq playerName) and
            (InventoryTable.equipped eq true) and
            (InventoryTable.slot eq slot)
        }) {
            it[equipped] = false
            it[InventoryTable.slot] = ""
        }

        // If stackable, split off 1 for equipping
        val currentQty = itemRow[InventoryTable.quantity]
        if (currentQty > 1) {
            InventoryTable.update({
                (InventoryTable.id eq itemRow[InventoryTable.id])
            }) {
                it[quantity] = currentQty - 1
            }
            InventoryTable.insert {
                it[InventoryTable.playerName] = playerName
                it[InventoryTable.itemId] = itemId
                it[quantity] = 1
                it[equipped] = true
                it[InventoryTable.slot] = slot
            }
        } else {
            InventoryTable.update({
                (InventoryTable.id eq itemRow[InventoryTable.id])
            }) {
                it[equipped] = true
                it[InventoryTable.slot] = slot
            }
        }
        true
    }

    fun unequipItem(playerName: String, slot: String): Boolean = transaction {
        val equipped = InventoryTable.selectAll().where {
            (InventoryTable.playerName eq playerName) and
            (InventoryTable.equipped eq true) and
            (InventoryTable.slot eq slot)
        }.firstOrNull() ?: return@transaction false

        val itemId = equipped[InventoryTable.itemId]
        val item = itemCatalog.getItem(itemId)

        // Try to merge back into existing stack
        if (item != null && item.stackable) {
            val existingStack = InventoryTable.selectAll().where {
                (InventoryTable.playerName eq playerName) and
                (InventoryTable.itemId eq itemId) and
                (InventoryTable.equipped eq false)
            }.firstOrNull()

            if (existingStack != null) {
                val newQty = (existingStack[InventoryTable.quantity] + 1).coerceAtMost(item.maxStack)
                InventoryTable.update({
                    (InventoryTable.id eq existingStack[InventoryTable.id])
                }) {
                    it[quantity] = newQty
                }
                InventoryTable.deleteWhere {
                    (InventoryTable.id eq equipped[InventoryTable.id])
                }
                return@transaction true
            }
        }

        InventoryTable.update({
            (InventoryTable.id eq equipped[InventoryTable.id])
        }) {
            it[InventoryTable.equipped] = false
            it[InventoryTable.slot] = ""
        }
        true
    }

    open fun getEquippedItems(playerName: String): Map<String, String> = transaction {
        InventoryTable.selectAll().where {
            (InventoryTable.playerName eq playerName) and
            (InventoryTable.equipped eq true)
        }.associate { row ->
            row[InventoryTable.slot] to row[InventoryTable.itemId]
        }
    }
}
