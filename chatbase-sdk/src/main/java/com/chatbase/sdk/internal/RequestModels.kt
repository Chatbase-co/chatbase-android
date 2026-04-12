package com.chatbase.sdk.internal

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
internal data class ChatRequest(
    val message: String? = null,
    val conversationId: String? = null,
    val stream: Boolean = true
)

@Serializable
internal data class RetryRequest(
    val messageId: String,
    val stream: Boolean = true
)

@Serializable
internal data class ToolResultRequest(
    val toolCallId: String,
    val output: JsonElement
)

@Serializable
internal data class VerifyRequest(
    val token: String
)
