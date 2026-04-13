package com.chatbase.sdk

import com.chatbase.sdk.exception.ChatbaseException
import com.chatbase.sdk.model.ChatResponse
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull

data class ToolCallInfo(
    val toolCallId: String,
    val toolName: String,
    val input: JsonElement
) {
    /** Returns tool input as a Map for easy access without requiring kotlinx-serialization. */
    fun inputAsMap(): Map<String, Any?> = jsonElementToMap(input)
}

data class ToolResultInfo(
    val toolCallId: String,
    val toolName: String,
    val output: Any
) {
    /** Returns tool output as a JSON string for display. */
    fun outputAsString(): String = output.toString()
}

class StreamCallbacks {
    internal var onStart: (() -> Unit)? = null
    internal var onTextDelta: ((text: String) -> Unit)? = null
    internal var onToolCall: ((toolCall: ToolCallInfo) -> Unit)? = null
    internal var onToolResult: ((result: ToolResultInfo) -> Unit)? = null
    internal var onFinish: ((response: ChatResponse) -> Unit)? = null
    internal var onError: ((error: ChatbaseException) -> Unit)? = null

    fun onStart(block: () -> Unit) { onStart = block }
    fun onTextDelta(block: (text: String) -> Unit) { onTextDelta = block }
    fun onToolCall(block: (toolCall: ToolCallInfo) -> Unit) { onToolCall = block }
    fun onToolResult(block: (result: ToolResultInfo) -> Unit) { onToolResult = block }
    fun onFinish(block: (response: ChatResponse) -> Unit) { onFinish = block }
    fun onError(block: (error: ChatbaseException) -> Unit) { onError = block }
}

private fun jsonElementToMap(element: JsonElement): Map<String, Any?> {
    if (element !is JsonObject) return emptyMap()
    return element.entries.associate { (k, v) -> k to jsonElementToAny(v) }
}

private fun jsonElementToAny(element: JsonElement): Any? = when (element) {
    is JsonNull -> null
    is JsonPrimitive -> when {
        element.isString -> element.content
        element.booleanOrNull != null -> element.boolean
        element.longOrNull != null -> element.long
        element.doubleOrNull != null -> element.double
        else -> element.content
    }
    is JsonArray -> element.map { jsonElementToAny(it) }
    is JsonObject -> element.entries.associate { (k, v) -> k to jsonElementToAny(v) }
}
