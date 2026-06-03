package com.neomud.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class OnlinePlayer(
    val name: String,
    val level: Int,
    val characterClass: String,
    val zone: String
)
