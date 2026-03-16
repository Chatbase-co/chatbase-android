package com.chatbase.sdk.model

import kotlinx.serialization.Serializable

@Serializable
data class Usage(
    val credits: Double
)

@Serializable
data class ResponseMetadata(
    val userMessageId: String,
    val conversationId: String,
    val userId: String? = null,
    val finishReason: FinishReason,
    val usage: Usage
)

@Serializable
data class ChatResponse(
    val id: String,
    val role: String,
    val parts: List<Part>,
    val metadata: ResponseMetadata
)
