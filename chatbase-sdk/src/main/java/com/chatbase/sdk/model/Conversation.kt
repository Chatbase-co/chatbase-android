package com.chatbase.sdk.model

import kotlinx.serialization.Serializable

@Serializable
data class Conversation(
    val id: String,
    val title: String? = null,
    val createdAt: Double,
    val updatedAt: Double,
    val userId: String? = null,
    val status: String,
    val messages: List<Message>? = null
)
