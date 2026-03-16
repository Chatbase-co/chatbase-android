package com.chatbase.sdk.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
sealed interface Part {
    val type: String

    @Serializable
    @SerialName("text")
    data class Text(
        val text: String
    ) : Part {
        override val type: String get() = "text"
    }

    @Serializable
    @SerialName("tool-call")
    data class ToolCall(
        val toolCallId: String,
        val toolName: String,
        val input: JsonElement? = null
    ) : Part {
        override val type: String get() = "tool-call"
    }

    @Serializable
    @SerialName("tool-result")
    data class ToolResult(
        val toolCallId: String,
        val toolName: String,
        val output: JsonElement? = null
    ) : Part {
        override val type: String get() = "tool-result"
    }
}
