package com.chatbase.sdk.internal

import com.chatbase.sdk.exception.ChatbaseException
import com.chatbase.sdk.model.FinishReason
import com.chatbase.sdk.streaming.ChatStreamEvent
import org.junit.Assert.*
import org.junit.Test

class SseParserTest {

    // ── text-delta ───────────────────────────────────────────────────

    @Test
    fun `text-delta with delta field`() {
        val event = SseParser.parse("text-delta", """{"delta": "Hello"}""")
        assertTrue(event is ChatStreamEvent.TextDelta)
        assertEquals("Hello", (event as ChatStreamEvent.TextDelta).text)
    }

    @Test
    fun `text-delta with text field fallback`() {
        val event = SseParser.parse("text-delta", """{"text": "World"}""")
        assertTrue(event is ChatStreamEvent.TextDelta)
        assertEquals("World", (event as ChatStreamEvent.TextDelta).text)
    }

    @Test
    fun `text-delta with neither delta nor text returns empty`() {
        val event = SseParser.parse("text-delta", """{"other": "value"}""")
        assertTrue(event is ChatStreamEvent.TextDelta)
        assertEquals("", (event as ChatStreamEvent.TextDelta).text)
    }

    @Test
    fun `text-delta with empty delta`() {
        val event = SseParser.parse("text-delta", """{"delta": ""}""")
        assertTrue(event is ChatStreamEvent.TextDelta)
        assertEquals("", (event as ChatStreamEvent.TextDelta).text)
    }

    @Test
    fun `text-delta type inferred from json when eventType is null`() {
        val event = SseParser.parse(null, """{"type": "text-delta", "delta": "inferred"}""")
        assertTrue(event is ChatStreamEvent.TextDelta)
        assertEquals("inferred", (event as ChatStreamEvent.TextDelta).text)
    }

    // ── finish ───────────────────────────────────────────────────────

    @Test
    fun `finish with full metadata`() {
        val data = """{
            "finishReason": "stop",
            "messageMetadata": {
                "messageId": "msg-1",
                "userMessageId": "umsg-1",
                "conversationId": "conv-1",
                "userId": "user-1",
                "usage": {"credits": 1.5}
            }
        }"""
        val event = SseParser.parse("finish", data)
        assertTrue(event is ChatStreamEvent.Done)
        val response = (event as ChatStreamEvent.Done).response
        assertEquals("msg-1", response.id)
        assertEquals("assistant", response.role)
        assertEquals("umsg-1", response.metadata.userMessageId)
        assertEquals("conv-1", response.metadata.conversationId)
        assertEquals("user-1", response.metadata.userId)
        assertEquals(FinishReason.STOP, response.metadata.finishReason)
        assertEquals(1.5, response.metadata.usage.credits, 0.001)
    }

    @Test
    fun `finish with null userId`() {
        val data = """{
            "finishReason": "stop",
            "messageMetadata": {
                "messageId": "msg-2",
                "userMessageId": "umsg-2",
                "conversationId": "conv-2",
                "userId": null,
                "usage": {"credits": 0.5}
            }
        }"""
        val event = SseParser.parse("finish", data)
        assertTrue(event is ChatStreamEvent.Done)
        assertNull((event as ChatStreamEvent.Done).response.metadata.userId)
    }

    @Test
    fun `finish with missing fields uses defaults`() {
        val data = """{}"""
        val event = SseParser.parse("finish", data)
        assertTrue(event is ChatStreamEvent.Done)
        val response = (event as ChatStreamEvent.Done).response
        assertEquals("", response.id)
        assertEquals("", response.metadata.userMessageId)
        assertEquals("", response.metadata.conversationId)
        assertNull(response.metadata.userId)
        assertEquals(FinishReason.STOP, response.metadata.finishReason)
        assertEquals(0.0, response.metadata.usage.credits, 0.001)
    }

    @Test
    fun `finish with error finishReason`() {
        val data = """{
            "finishReason": "error",
            "messageMetadata": {
                "messageId": "msg-3",
                "userMessageId": "umsg-3",
                "conversationId": "conv-3",
                "usage": {"credits": 0.0}
            }
        }"""
        val event = SseParser.parse("finish", data)
        assertTrue(event is ChatStreamEvent.Done)
        assertEquals(FinishReason.ERROR, (event as ChatStreamEvent.Done).response.metadata.finishReason)
    }

    // ── error ────────────────────────────────────────────────────────

    @Test
    fun `error with structured error object`() {
        val data = """{"error": {"code": "LIMIT_EXCEEDED", "message": "Rate limit hit"}}"""
        val event = SseParser.parse("error", data)
        assertTrue(event is ChatStreamEvent.Error)
        val msg = (event as ChatStreamEvent.Error).exception.message!!
        assertTrue(msg.contains("LIMIT_EXCEEDED"))
        assertTrue(msg.contains("Rate limit hit"))
    }

    @Test
    fun `error with top-level message fallback`() {
        val data = """{"message": "Something went wrong"}"""
        val event = SseParser.parse("error", data)
        assertTrue(event is ChatStreamEvent.Error)
        assertTrue((event as ChatStreamEvent.Error).exception.message!!.contains("Something went wrong"))
    }

    @Test
    fun `error with no message`() {
        val data = """{}"""
        val event = SseParser.parse("error", data)
        assertTrue(event is ChatStreamEvent.Error)
        assertTrue((event as ChatStreamEvent.Error).exception.message!!.contains("Unknown stream error"))
    }

    // ── special cases ────────────────────────────────────────────────

    @Test
    fun `DONE sentinel returns null`() {
        val event = SseParser.parse("message", "[DONE]")
        assertNull(event)
    }

    @Test
    fun `unknown type returns null`() {
        val event = SseParser.parse("unknown-type", """{"data": "test"}""")
        assertNull(event)
    }

    @Test
    fun `malformed JSON returns Error event`() {
        val event = SseParser.parse("text-delta", "not valid json{{{")
        assertTrue(event is ChatStreamEvent.Error)
        assertTrue((event as ChatStreamEvent.Error).exception is ChatbaseException)
    }
}
