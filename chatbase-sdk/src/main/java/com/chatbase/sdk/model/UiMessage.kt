package com.chatbase.sdk.model

import java.util.UUID

/**
 * Display-ready content for a single message item.
 */
sealed interface UiMessageContent {
    data class Text(val text: String) : UiMessageContent
    data class ToolCall(
        val toolCallId: String,
        val toolName: String,
        val input: String,
        val output: String? = null,
        val isExecuting: Boolean = false
    ) : UiMessageContent
}

/**
 * Display-ready message model for use in UI layers.
 *
 * Wraps the SDK's [Part]-based message model into a flat structure
 * suitable for rendering in a list. Includes streaming and error state
 * so consumers don't need to track these separately.
 */
data class UiMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: Role,
    val content: UiMessageContent,
    val messageId: String? = null,
    val conversationId: String? = null,
    val isStreaming: Boolean = false,
    val isError: Boolean = false
)
