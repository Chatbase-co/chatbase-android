package com.chatbase.sdk.internal

import com.chatbase.sdk.exception.ChatbaseException
import com.chatbase.sdk.model.*
import com.chatbase.sdk.streaming.ChatStreamEvent
import kotlinx.serialization.json.*

internal object SseParser {

    fun parse(eventType: String?, data: String): ChatStreamEvent? {
        if (data == "[DONE]") return null

        return try {
            val json = chatbaseJson.parseToJsonElement(data).jsonObject
            val type = eventType ?: json["type"]?.jsonPrimitive?.content

            when (type) {
                "text-delta" -> parseTextDelta(json)
                "finish" -> parseFinish(json)
                "error" -> parseError(json)
                else -> null
            }
        } catch (e: Exception) {
            ChatStreamEvent.Error(
                ChatbaseException("Failed to parse SSE event '$eventType': ${e.message}", e)
            )
        }
    }

    private fun parseTextDelta(json: JsonObject): ChatStreamEvent {
        val text = json["delta"]?.jsonPrimitive?.content
            ?: json["text"]?.jsonPrimitive?.content
            ?: ""
        return ChatStreamEvent.TextDelta(text)
    }

    private fun parseFinish(json: JsonObject): ChatStreamEvent {
        val finishReason = json["finishReason"]?.jsonPrimitive?.content ?: "stop"
        val msgMeta = json["messageMetadata"]?.jsonObject

        val messageId = msgMeta?.get("messageId")?.jsonPrimitive?.content ?: ""
        val userMessageId = msgMeta?.get("userMessageId")?.jsonPrimitive?.content ?: ""
        val conversationId = msgMeta?.get("conversationId")?.jsonPrimitive?.content ?: ""
        val userId = msgMeta?.get("userId")?.let {
            if (it is JsonNull) null else it.jsonPrimitive.content
        }
        val credits = msgMeta?.get("usage")?.jsonObject
            ?.get("credits")?.jsonPrimitive?.double ?: 0.0

        val response = ChatResponse(
            id = messageId,
            role = "assistant",
            parts = emptyList(),
            metadata = ResponseMetadata(
                userMessageId = userMessageId,
                conversationId = conversationId,
                userId = userId,
                finishReason = FinishReason.valueOf(finishReason.uppercase()),
                usage = Usage(credits = credits)
            )
        )
        return ChatStreamEvent.Done(response)
    }

    private fun parseError(json: JsonObject): ChatStreamEvent {
        val error = json["error"]?.jsonObject
        val code = error?.get("code")?.jsonPrimitive?.content ?: "STREAM_ERROR"
        val message = error?.get("message")?.jsonPrimitive?.content
            ?: json["message"]?.jsonPrimitive?.content
            ?: "Unknown stream error"
        return ChatStreamEvent.Error(ChatbaseException("$code: $message"))
    }
}
