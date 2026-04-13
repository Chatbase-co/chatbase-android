package com.chatbase.sdk.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * Converts a list of SDK [Message] objects into display-ready [UiMessage] items.
 *
 * Each [Part] in a message becomes a separate [UiMessage]:
 * - [Part.Text] → [UiMessageContent.Text]
 * - [Part.ToolCall] → [UiMessageContent.ToolCall] (with pretty-printed input)
 * - [Part.ToolResult] → [UiMessageContent.ToolCall] (with pretty-printed output)
 */
fun List<Message>.toUiMessages(conversationId: String): List<UiMessage> {
    return flatMap { msg ->
        msg.parts.map { part ->
            when (part) {
                is Part.Text -> UiMessage(
                    role = msg.role,
                    content = UiMessageContent.Text(part.text),
                    messageId = msg.id,
                    conversationId = conversationId
                )
                is Part.ToolCall -> UiMessage(
                    role = Role.ASSISTANT,
                    content = UiMessageContent.ToolCall(
                        toolCallId = part.toolCallId,
                        toolName = part.toolName,
                        input = prettyPrintJson(part.input),
                        output = null,
                        isExecuting = false
                    ),
                    messageId = msg.id,
                    conversationId = conversationId
                )
                is Part.ToolResult -> UiMessage(
                    role = Role.ASSISTANT,
                    content = UiMessageContent.ToolCall(
                        toolCallId = part.toolCallId,
                        toolName = part.toolName,
                        input = "",
                        output = prettyPrintJson(part.output),
                        isExecuting = false
                    ),
                    messageId = msg.id,
                    conversationId = conversationId
                )
            }
        }
    }
}

private val prettyJson = Json { prettyPrint = true }

private fun prettyPrintJson(element: JsonElement?): String {
    if (element == null) return ""
    return try {
        prettyJson.encodeToString(JsonElement.serializer(), element)
    } catch (_: Exception) {
        element.toString()
    }
}
