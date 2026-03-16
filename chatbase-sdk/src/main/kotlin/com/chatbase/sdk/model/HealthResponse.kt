package com.chatbase.sdk.model

import kotlinx.serialization.Serializable

@Serializable
data class HealthResponse(
    val status: String,
    val timestamp: Double
)
