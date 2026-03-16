package com.chatbase.sdk.internal

import kotlinx.serialization.Serializable

@Serializable
internal data class ChatRequest(
    val message: String,
    val conversationId: String? = null,
    val stream: Boolean = false,
    val userId: String? = null
)

@Serializable
internal data class RetryRequest(
    val messageId: String,
    val stream: Boolean = false
)

@Serializable
internal data class FeedbackRequest(
    val feedback: String? = null
)
