package com.chatbase.sdk.model

import kotlinx.serialization.Serializable

@Serializable
data class Usage(
    val credits: Double
)

@Serializable
data class ChatResponse(
    val id: String,
    val role: String,
    val parts: List<Part>,
    val metadata: ResponseMetadata
)

@Serializable
data class ResponseMetadata(
    val messageId: String? = null,
    val userMessageId: String? = null,
    val conversationId: String? = null,
    val finishReason: FinishReason = FinishReason.UNKNOWN,
    val usage: Usage? = null
)
