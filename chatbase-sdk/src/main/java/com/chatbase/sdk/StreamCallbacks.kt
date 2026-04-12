package com.chatbase.sdk

import com.chatbase.sdk.exception.ChatbaseException
import com.chatbase.sdk.model.ChatResponse
import kotlinx.serialization.json.JsonElement

data class ToolCallInfo(
    val toolCallId: String,
    val toolName: String,
    val input: JsonElement
)

data class ToolResultInfo(
    val toolCallId: String,
    val toolName: String,
    val output: Any
)

class StreamCallbacks {
    internal var onTextDelta: ((text: String) -> Unit)? = null
    internal var onToolCall: ((toolCall: ToolCallInfo) -> Unit)? = null
    internal var onToolResult: ((result: ToolResultInfo) -> Unit)? = null
    internal var onFinish: ((response: ChatResponse) -> Unit)? = null
    internal var onError: ((error: ChatbaseException) -> Unit)? = null

    fun onTextDelta(block: (text: String) -> Unit) { onTextDelta = block }
    fun onToolCall(block: (toolCall: ToolCallInfo) -> Unit) { onToolCall = block }
    fun onToolResult(block: (result: ToolResultInfo) -> Unit) { onToolResult = block }
    fun onFinish(block: (response: ChatResponse) -> Unit) { onFinish = block }
    fun onError(block: (error: ChatbaseException) -> Unit) { onError = block }
}
