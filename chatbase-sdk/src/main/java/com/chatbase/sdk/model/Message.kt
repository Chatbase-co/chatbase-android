package com.chatbase.sdk.model

import kotlinx.serialization.Serializable

@Serializable
data class MessageMetadata(
    val score: Double? = null
)

@Serializable
data class Message(
    val id: String,
    val role: Role,
    val parts: List<Part>,
    val createdAt: Double? = null,
    val feedback: Feedback? = null,
    val metadata: MessageMetadata? = null
)
