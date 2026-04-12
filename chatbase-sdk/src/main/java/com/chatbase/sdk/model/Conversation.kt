package com.chatbase.sdk.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ConversationStatus {
    @SerialName("ongoing") ONGOING,
    @SerialName("archived") ARCHIVED
}

@Serializable
data class Conversation(
    val id: String,
    val title: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val userId: String? = null,
    val status: ConversationStatus
)
