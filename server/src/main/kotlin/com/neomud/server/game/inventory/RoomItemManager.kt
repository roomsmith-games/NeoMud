package com.neomud.server.game.inventory

import com.neomud.server.game.GameConfig
import com.neomud.shared.model.Coins
import com.neomud.shared.model.GroundItem
import java.util.concurrent.ConcurrentHashMap

class RoomItemManager {

    private data class TimestampedItem(
        val item: GroundItem,
        val droppedAt: Long = System.currentTimeMillis()
    )

    private data class RoomGroundState(
        val items: MutableList<TimestampedItem> = mutableListOf(),
        var coins: Coins = Coins(),
        var coinsDroppedAt: Long = System.currentTimeMillis()
    )

    private val rooms = ConcurrentHashMap<String, RoomGroundState>()

    fun addItems(roomId: String, items: List<GroundItem>) {
        val state = rooms.getOrPut(roomId) { RoomGroundState() }
        val now = System.currentTimeMillis()
        synchronized(state) {
            for (item in items) {
                val existing = state.items.find { it.item.itemId == item.itemId }
                if (existing != null) {
                    val idx = state.items.indexOf(existing)
                    state.items[idx] = existing.copy(
                        item = existing.item.copy(quantity = existing.item.quantity + item.quantity),
                        droppedAt = now // refresh timer on stack
                    )
                } else {
                    state.items.add(TimestampedItem(item, now))
                }
            }
            // Enforce per-room cap
            while (state.items.size > GameConfig.GroundItems.MAX_ITEMS_PER_ROOM) {
                state.items.removeAt(0) // remove oldest
            }
        }
    }

    fun addCoins(roomId: String, coins: Coins) {
        if (coins.isEmpty()) return
        val state = rooms.getOrPut(roomId) { RoomGroundState() }
        synchronized(state) {
            state.coins = state.coins + coins
            state.coinsDroppedAt = System.currentTimeMillis()
        }
    }

    fun removeItem(roomId: String, itemId: String, quantity: Int): Int {
        val state = rooms[roomId] ?: return 0
        synchronized(state) {
            val existing = state.items.find { it.item.itemId == itemId } ?: return 0
            val actual = minOf(quantity, existing.item.quantity)
            if (actual >= existing.item.quantity) {
                state.items.remove(existing)
            } else {
                val idx = state.items.indexOf(existing)
                state.items[idx] = existing.copy(item = existing.item.copy(quantity = existing.item.quantity - actual))
            }
            cleanupIfEmpty(roomId, state)
            return actual
        }
    }

    fun removeCoins(roomId: String, coinType: String): Int {
        val state = rooms[roomId] ?: return 0
        synchronized(state) {
            val amount = when (coinType) {
                "copper" -> state.coins.copper
                "silver" -> state.coins.silver
                "gold" -> state.coins.gold
                "platinum" -> state.coins.platinum
                else -> return 0
            }
            if (amount == 0) return 0
            state.coins = when (coinType) {
                "copper" -> state.coins.copy(copper = 0)
                "silver" -> state.coins.copy(silver = 0)
                "gold" -> state.coins.copy(gold = 0)
                "platinum" -> state.coins.copy(platinum = 0)
                else -> state.coins
            }
            cleanupIfEmpty(roomId, state)
            return amount
        }
    }

    fun removeAllCoins(roomId: String): Coins {
        val state = rooms[roomId] ?: return Coins()
        synchronized(state) {
            val coins = state.coins.copy()
            if (coins == Coins()) return Coins()
            state.coins = Coins()
            cleanupIfEmpty(roomId, state)
            return coins
        }
    }

    fun getGroundItems(roomId: String): List<GroundItem> {
        val state = rooms[roomId] ?: return emptyList()
        synchronized(state) {
            return state.items.map { it.item }
        }
    }

    fun getGroundCoins(roomId: String): Coins {
        val state = rooms[roomId] ?: return Coins()
        synchronized(state) {
            return state.coins
        }
    }

    /** Remove expired ground items and coins. Called periodically from the game loop. */
    fun pruneExpired(now: Long = System.currentTimeMillis()): Int {
        val cutoff = now - GameConfig.GroundItems.EXPIRY_MS
        var pruned = 0
        val iter = rooms.entries.iterator()
        while (iter.hasNext()) {
            val (_, state) = iter.next()
            synchronized(state) {
                val before = state.items.size
                state.items.removeAll { it.droppedAt < cutoff }
                pruned += before - state.items.size
                // Expire coins too
                if (!state.coins.isEmpty() && state.coinsDroppedAt < cutoff) {
                    state.coins = Coins()
                    pruned++
                }
                if (state.items.isEmpty() && state.coins.isEmpty()) {
                    iter.remove()
                }
            }
        }
        return pruned
    }

    private fun cleanupIfEmpty(roomId: String, state: RoomGroundState) {
        if (state.items.isEmpty() && state.coins.isEmpty()) {
            rooms.remove(roomId)
        }
    }
}
