package com.chatbase.sdk.streaming

import com.chatbase.sdk.exception.ChatbaseException
import kotlinx.serialization.json.JsonElement

data class StreamMessageMetadata(
    val messageId: String? = null,
    val userMessageId: String? = null,
    val conversationId: String? = null,
    val userId: String? = null,
    val usage: StreamUsage? = null
)

data class StreamUsage(
    val credits: Double = 0.0
)

sealed interface ChatStreamEvent {

    // Text events
    data class TextStart(val id: String) : ChatStreamEvent
    data class TextDelta(val id: String, val delta: String) : ChatStreamEvent
    data class TextEnd(val id: String) : ChatStreamEvent

    // Tool input events
    data class ToolInputStart(val toolCallId: String, val toolName: String) : ChatStreamEvent
    data class ToolInputDelta(val toolCallId: String, val inputTextDelta: String) : ChatStreamEvent
    data class ToolInputAvailable(
        val toolCallId: String,
        val toolName: String,
        val input: JsonElement
    ) : ChatStreamEvent

    // Tool output events
    data class ToolOutputAvailable(val toolCallId: String, val output: JsonElement) : ChatStreamEvent

    // Step events
    data object StepStart : ChatStreamEvent
    data object StepFinish : ChatStreamEvent

    // Message lifecycle events
    data class Start(val messageId: String?, val messageMetadata: StreamMessageMetadata?) : ChatStreamEvent
    data class Finish(val finishReason: String, val messageMetadata: StreamMessageMetadata?) : ChatStreamEvent
    data class MessageMetadataEvent(val messageMetadata: StreamMessageMetadata) : ChatStreamEvent

    // Error
    data class Error(val exception: ChatbaseException) : ChatStreamEvent
}
