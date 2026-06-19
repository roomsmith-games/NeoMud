package com.neomud.server.telnet

import com.neomud.shared.model.GroundItem
import com.neomud.shared.model.InventoryItem
import com.neomud.shared.model.Item
import com.neomud.shared.model.Npc
import com.neomud.shared.model.RecipeInfo
import com.neomud.shared.model.RoomInteractable

object NameResolver {

    sealed class Resolve<out T> {
        data class Found<out T>(val entity: T) : Resolve<T>()
        data class Ambiguous<out T>(val candidates: List<@UnsafeVariance T>, val names: List<String>) : Resolve<T>()
        object NotFound : Resolve<Nothing>()
    }

    fun ambiguityMessage(query: String, names: List<String>): String {
        val opts = names.take(4).mapIndexed { i, n -> "${i + 1}. $n" }.joinToString("  ")
        return "Which '$query'? $opts — use '2.$query' to pick the second."
    }

    fun resolveNpc(query: String, npcs: List<Npc>): Resolve<Npc> =
        resolve(query, npcs) { it.name }

    fun resolveGroundItem(query: String, items: List<GroundItem>, catalog: Map<String, Item>): Resolve<GroundItem> =
        resolve(query, items) { catalog[it.itemId]?.name ?: it.itemId }

    fun resolveInventoryItem(query: String, items: List<InventoryItem>, catalog: Map<String, Item>): Resolve<InventoryItem> =
        resolve(query, items) { catalog[it.itemId]?.name ?: it.itemId }

    fun resolveInteractable(query: String, interactables: List<RoomInteractable>): Resolve<RoomInteractable> =
        resolve(query, interactables) { it.label }

    fun resolveRecipe(query: String, recipes: List<RecipeInfo>): Resolve<RecipeInfo> =
        resolve(query, recipes) { it.recipe.name }

    private fun <T> resolve(query: String, entities: List<T>, nameOf: (T) -> String): Resolve<T> {
        val (number, base) = parseNumberedQuery(query)
        val q = base.lowercase().trim()
        if (q.isEmpty()) return Resolve.NotFound

        var matches = entities.filter { nameOf(it).equals(q, ignoreCase = true) }
        if (matches.isEmpty()) matches = entities.filter { nameOf(it).lowercase().startsWith(q) }
        if (matches.isEmpty()) matches = entities.filter { nameOf(it).lowercase().contains(q) }
        if (matches.isEmpty()) return Resolve.NotFound

        if (number != null) {
            val idx = number - 1
            return if (idx in matches.indices) Resolve.Found(matches[idx]) else Resolve.NotFound
        }
        return if (matches.size == 1) Resolve.Found(matches[0])
        else Resolve.Ambiguous(matches, matches.map { nameOf(it) })
    }

    private fun parseNumberedQuery(query: String): Pair<Int?, String> {
        val dot = query.indexOf('.')
        if (dot > 0) {
            val num = query.substring(0, dot).toIntOrNull()
            if (num != null && num > 0) return num to query.substring(dot + 1)
        }
        return null to query
    }
}
