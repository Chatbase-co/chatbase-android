package com.chatbase.sdk.internal

import com.chatbase.sdk.exception.ChatbaseException
import com.chatbase.sdk.streaming.ChatStreamEvent
import org.junit.Assert.*
import org.junit.Test

class SseParserTest {

    // ── text-delta ───────────────────────────────────────────────────

    @Test
    fun `text-delta parses id and delta`() {
        val event = SseParser.parse(null, """{"type": "text-delta", "id": "t1", "delta": "Hello"}""")
        assertTrue(event is ChatStreamEvent.TextDelta)
        val td = event as ChatStreamEvent.TextDelta
        assertEquals("t1", td.id)
        assertEquals("Hello", td.delta)
    }

    @Test
    fun `text-delta with empty delta`() {
        val event = SseParser.parse(null, """{"type": "text-delta", "id": "t1", "delta": ""}""")
        assertTrue(event is ChatStreamEvent.TextDelta)
        assertEquals("", (event as ChatStreamEvent.TextDelta).delta)
    }

    @Test
    fun `text-delta type from eventType param`() {
        val event = SseParser.parse("text-delta", """{"id": "t1", "delta": "hi"}""")
        assertTrue(event is ChatStreamEvent.TextDelta)
        assertEquals("hi", (event as ChatStreamEvent.TextDelta).delta)
    }

    // ── text-start / text-end ────────────────────────────────────────

    @Test
    fun `text-start parses id`() {
        val event = SseParser.parse(null, """{"type": "text-start", "id": "t1"}""")
        assertTrue(event is ChatStreamEvent.TextStart)
        assertEquals("t1", (event as ChatStreamEvent.TextStart).id)
    }

    @Test
    fun `text-end parses id`() {
        val event = SseParser.parse(null, """{"type": "text-end", "id": "t1"}""")
        assertTrue(event is ChatStreamEvent.TextEnd)
        assertEquals("t1", (event as ChatStreamEvent.TextEnd).id)
    }

    // ── tool-input events ────────────────────────────────────────────

    @Test
    fun `tool-input-start parses toolCallId and toolName`() {
        val event = SseParser.parse(null, """{"type": "tool-input-start", "toolCallId": "tc-1", "toolName": "search"}""")
        assertTrue(event is ChatStreamEvent.ToolInputStart)
        val e = event as ChatStreamEvent.ToolInputStart
        assertEquals("tc-1", e.toolCallId)
        assertEquals("search", e.toolName)
    }

    @Test
    fun `tool-input-delta parses delta`() {
        val event = SseParser.parse(null, """{"type": "tool-input-delta", "toolCallId": "tc-1", "inputTextDelta": "{\"q\":"}""")
        assertTrue(event is ChatStreamEvent.ToolInputDelta)
        val e = event as ChatStreamEvent.ToolInputDelta
        assertEquals("tc-1", e.toolCallId)
    }

    @Test
    fun `tool-input-available parses full input`() {
        val event = SseParser.parse(null, """{"type": "tool-input-available", "toolCallId": "tc-1", "toolName": "search", "input": {"query": "test"}}""")
        assertTrue(event is ChatStreamEvent.ToolInputAvailable)
        val e = event as ChatStreamEvent.ToolInputAvailable
        assertEquals("tc-1", e.toolCallId)
        assertEquals("search", e.toolName)
        assertTrue(e.input.toString().contains("test"))
    }

    // ── tool-output-available ────────────────────────────────────────

    @Test
    fun `tool-output-available parses output`() {
        val event = SseParser.parse(null, """{"type": "tool-output-available", "toolCallId": "tc-1", "output": {"result": "ok"}}""")
        assertTrue(event is ChatStreamEvent.ToolOutputAvailable)
        val e = event as ChatStreamEvent.ToolOutputAvailable
        assertEquals("tc-1", e.toolCallId)
        assertTrue(e.output.toString().contains("ok"))
    }

    // ── step events ──────────────────────────────────────────────────

    @Test
    fun `start-step returns StepStart`() {
        val event = SseParser.parse(null, """{"type": "start-step"}""")
        assertEquals(ChatStreamEvent.StepStart, event)
    }

    @Test
    fun `finish-step returns StepFinish`() {
        val event = SseParser.parse(null, """{"type": "finish-step"}""")
        assertEquals(ChatStreamEvent.StepFinish, event)
    }

    // ── start / finish ───────────────────────────────────────────────

    @Test
    fun `start parses messageId`() {
        val event = SseParser.parse(null, """{"type": "start", "messageId": "msg-1"}""")
        assertTrue(event is ChatStreamEvent.Start)
        assertEquals("msg-1", (event as ChatStreamEvent.Start).messageId)
    }

    @Test
    fun `finish with full metadata`() {
        val data = """{
            "type": "finish",
            "finishReason": "stop",
            "messageMetadata": {
                "messageId": "msg-1",
                "userMessageId": "umsg-1",
                "conversationId": "conv-1",
                "usage": {"credits": 1.5}
            }
        }"""
        val event = SseParser.parse(null, data)
        assertTrue(event is ChatStreamEvent.Finish)
        val f = event as ChatStreamEvent.Finish
        assertEquals("stop", f.finishReason)
        assertNotNull(f.messageMetadata)
        assertEquals("msg-1", f.messageMetadata!!.messageId)
        assertEquals("conv-1", f.messageMetadata!!.conversationId)
        assertEquals(1.5, f.messageMetadata!!.usage!!.credits, 0.001)
    }

    @Test
    fun `finish with tool-calls reason`() {
        val data = """{"type": "finish", "finishReason": "tool-calls"}"""
        val event = SseParser.parse(null, data)
        assertTrue(event is ChatStreamEvent.Finish)
        assertEquals("tool-calls", (event as ChatStreamEvent.Finish).finishReason)
    }

    // ── message-metadata ─────────────────────────────────────────────

    @Test
    fun `message-metadata parses metadata`() {
        val data = """{
            "type": "message-metadata",
            "messageMetadata": {
                "messageId": "msg-1", "conversationId": "conv-1",
                "usage": {"credits": 2.0}
            }
        }"""
        val event = SseParser.parse(null, data)
        assertTrue(event is ChatStreamEvent.MessageMetadataEvent)
        val meta = (event as ChatStreamEvent.MessageMetadataEvent).messageMetadata
        assertEquals("msg-1", meta.messageId)
        assertEquals(2.0, meta.usage!!.credits, 0.001)
    }

    // ── error ────────────────────────────────────────────────────────

    @Test
    fun `error with errorText`() {
        val event = SseParser.parse(null, """{"type": "error", "errorText": "Something failed"}""")
        assertTrue(event is ChatStreamEvent.Error)
        assertTrue((event as ChatStreamEvent.Error).exception.message!!.contains("Something failed"))
    }

    @Test
    fun `error with structured error object`() {
        val event = SseParser.parse(null, """{"type": "error", "error": {"code": "ERR", "message": "Bad request"}}""")
        assertTrue(event is ChatStreamEvent.Error)
        assertTrue((event as ChatStreamEvent.Error).exception.message!!.contains("Bad request"))
    }

    // ── special cases ────────────────────────────────────────────────

    @Test
    fun `DONE sentinel returns null`() {
        assertNull(SseParser.parse(null, "[DONE]"))
    }

    @Test
    fun `unknown type returns null`() {
        assertNull(SseParser.parse(null, """{"type": "unknown-event", "data": "test"}"""))
    }

    @Test
    fun `malformed JSON returns Error event`() {
        val event = SseParser.parse(null, "not valid json{{{")
        assertTrue(event is ChatStreamEvent.Error)
        assertNotNull((event as ChatStreamEvent.Error).exception.message)
    }

    @Test
    fun `no type field returns null`() {
        assertNull(SseParser.parse(null, """{"data": "no type"}"""))
    }
}
