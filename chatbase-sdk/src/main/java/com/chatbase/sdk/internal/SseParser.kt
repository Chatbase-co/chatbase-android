package com.chatbase.sdk.internal

import com.chatbase.sdk.exception.ChatbaseException
import com.chatbase.sdk.streaming.ChatStreamEvent
import com.chatbase.sdk.streaming.StreamMessageMetadata
import com.chatbase.sdk.streaming.StreamUsage
import kotlinx.serialization.json.*

internal object SseParser {

    fun parse(eventType: String?, data: String): ChatStreamEvent? {
        if (data == "[DONE]") return null

        return try {
            val json = chatbaseJson.parseToJsonElement(data).jsonObject
            val type = eventType ?: json["type"]?.jsonPrimitive?.content ?: return null

            when (type) {
                "text-start" -> parseTextStart(json)
                "text-delta" -> parseTextDelta(json)
                "text-end" -> parseTextEnd(json)
                "tool-input-start" -> parseToolInputStart(json)
                "tool-input-delta" -> parseToolInputDelta(json)
                "tool-input-available" -> parseToolInputAvailable(json)
                "tool-output-available" -> parseToolOutputAvailable(json)
                "start-step" -> ChatStreamEvent.StepStart
                "finish-step" -> ChatStreamEvent.StepFinish
                "start" -> parseStart(json)
                "finish" -> parseFinish(json)
                "message-metadata" -> parseMessageMetadata(json)
                "error" -> parseError(json)
                else -> null
            }
        } catch (e: Exception) {
            ChatStreamEvent.Error(
                ChatbaseException("Failed to parse SSE event '$eventType': ${e.message}", e)
            )
        }
    }

    private fun parseTextStart(json: JsonObject): ChatStreamEvent {
        val id = json["id"]?.jsonPrimitive?.content ?: ""
        return ChatStreamEvent.TextStart(id)
    }

    private fun parseTextDelta(json: JsonObject): ChatStreamEvent {
        val id = json["id"]?.jsonPrimitive?.content ?: ""
        val delta = json["delta"]?.jsonPrimitive?.content ?: ""
        return ChatStreamEvent.TextDelta(id, delta)
    }

    private fun parseTextEnd(json: JsonObject): ChatStreamEvent {
        val id = json["id"]?.jsonPrimitive?.content ?: ""
        return ChatStreamEvent.TextEnd(id)
    }

    private fun parseToolInputStart(json: JsonObject): ChatStreamEvent {
        return ChatStreamEvent.ToolInputStart(
            toolCallId = json["toolCallId"]?.jsonPrimitive?.content ?: "",
            toolName = json["toolName"]?.jsonPrimitive?.content ?: ""
        )
    }

    private fun parseToolInputDelta(json: JsonObject): ChatStreamEvent {
        return ChatStreamEvent.ToolInputDelta(
            toolCallId = json["toolCallId"]?.jsonPrimitive?.content ?: "",
            inputTextDelta = json["inputTextDelta"]?.jsonPrimitive?.content ?: ""
        )
    }

    private fun parseToolInputAvailable(json: JsonObject): ChatStreamEvent {
        return ChatStreamEvent.ToolInputAvailable(
            toolCallId = json["toolCallId"]?.jsonPrimitive?.content ?: "",
            toolName = json["toolName"]?.jsonPrimitive?.content ?: "",
            input = json["input"] ?: JsonNull
        )
    }

    private fun parseToolOutputAvailable(json: JsonObject): ChatStreamEvent {
        return ChatStreamEvent.ToolOutputAvailable(
            toolCallId = json["toolCallId"]?.jsonPrimitive?.content ?: "",
            output = json["output"] ?: JsonNull
        )
    }

    private fun parseStart(json: JsonObject): ChatStreamEvent {
        return ChatStreamEvent.Start(
            messageId = json["messageId"]?.jsonPrimitive?.content,
            messageMetadata = json["messageMetadata"]?.let { parseMetadataObject(it) }
        )
    }

    private fun parseFinish(json: JsonObject): ChatStreamEvent {
        return ChatStreamEvent.Finish(
            finishReason = json["finishReason"]?.jsonPrimitive?.content ?: "unknown",
            messageMetadata = json["messageMetadata"]?.let { parseMetadataObject(it) }
        )
    }

    private fun parseMessageMetadata(json: JsonObject): ChatStreamEvent {
        val metadata = json["messageMetadata"]?.let { parseMetadataObject(it) }
            ?: StreamMessageMetadata()
        return ChatStreamEvent.MessageMetadataEvent(metadata)
    }

    private fun parseError(json: JsonObject): ChatStreamEvent {
        val errorText = json["errorText"]?.jsonPrimitive?.content
            ?: json["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content
            ?: json["message"]?.jsonPrimitive?.content
            ?: "Unknown stream error"
        return ChatStreamEvent.Error(ChatbaseException(errorText))
    }

    internal fun parseMetadataObject(element: JsonElement): StreamMessageMetadata? {
        if (element is JsonNull) return null
        val obj = element.jsonObject
        return StreamMessageMetadata(
            messageId = obj["messageId"]?.jsonPrimitive?.content,
            userMessageId = obj["userMessageId"]?.jsonPrimitive?.content,
            conversationId = obj["conversationId"]?.jsonPrimitive?.content,
            userId = obj["userId"]?.let { if (it is JsonNull) null else it.jsonPrimitive.content },
            usage = obj["usage"]?.jsonObject?.let { usage ->
                StreamUsage(credits = usage["credits"]?.jsonPrimitive?.double ?: 0.0)
            }
        )
    }
}
