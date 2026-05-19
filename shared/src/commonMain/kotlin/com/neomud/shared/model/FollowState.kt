package com.neomud.shared.model

import kotlinx.serialization.Serializable

@Serializable
enum class FollowState {
    OFF,
    ACTIVE,
    PAUSED
}
