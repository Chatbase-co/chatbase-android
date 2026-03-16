package com.chatbase.sdk.model

import com.chatbase.sdk.internal.ChatRequest
import com.chatbase.sdk.internal.chatbaseJson
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test

class SerializationTest {

    // ── ChatResponse deserialization ─────────────────────────────────

    @Test
    fun `ChatResponse deserialization with all fields`() {
        val json = """{
            "id": "msg-1",
            "role": "assistant",
            "parts": [{"type": "text", "text": "Hello"}],
            "metadata": {
                "userMessageId": "umsg-1",
                "conversationId": "conv-1",
                "userId": "user-1",
                "finishReason": "stop",
                "usage": {"credits": 1.5}
            }
        }"""
        val response = chatbaseJson.decodeFromString<ChatResponse>(json)
        assertEquals("msg-1", response.id)
        assertEquals("assistant", response.role)
        assertEquals(1, response.parts.size)
        assertEquals("umsg-1", response.metadata.userMessageId)
        assertEquals("conv-1", response.metadata.conversationId)
        assertEquals("user-1", response.metadata.userId)
        assertEquals(FinishReason.STOP, response.metadata.finishReason)
        assertEquals(1.5, response.metadata.usage.credits, 0.001)
    }

    // ── Part polymorphic deserialization ──────────────────────────────

    @Test
    fun `Part Text deserialization`() {
        val json = """{"type": "text", "text": "hello world"}"""
        val part = chatbaseJson.decodeFromString<Part>(json)
        assertTrue(part is Part.Text)
        assertEquals("hello world", (part as Part.Text).text)
    }

    @Test
    fun `Part ToolCall deserialization`() {
        val json = """{"type": "tool-call", "toolCallId": "tc-1", "toolName": "search", "input": {"q": "test"}}"""
        val part = chatbaseJson.decodeFromString<Part>(json)
        assertTrue(part is Part.ToolCall)
        val toolCall = part as Part.ToolCall
        assertEquals("tc-1", toolCall.toolCallId)
        assertEquals("search", toolCall.toolName)
        assertNotNull(toolCall.input)
    }

    @Test
    fun `Part ToolResult deserialization`() {
        val json = """{"type": "tool-result", "toolCallId": "tc-1", "toolName": "search", "output": ["a", "b"]}"""
        val part = chatbaseJson.decodeFromString<Part>(json)
        assertTrue(part is Part.ToolResult)
        val toolResult = part as Part.ToolResult
        assertEquals("tc-1", toolResult.toolCallId)
        assertEquals("search", toolResult.toolName)
        assertNotNull(toolResult.output)
    }

    @Test
    fun `Part ToolCall with null input`() {
        val json = """{"type": "tool-call", "toolCallId": "tc-2", "toolName": "noop"}"""
        val part = chatbaseJson.decodeFromString<Part>(json)
        assertTrue(part is Part.ToolCall)
        assertNull((part as Part.ToolCall).input)
    }

    // ── Message deserialization ──────────────────────────────────────

    @Test
    fun `Message with all optional fields`() {
        val json = """{
            "id": "msg-1",
            "role": "assistant",
            "parts": [{"type": "text", "text": "Hi"}],
            "createdAt": 1700000000.0,
            "feedback": "positive",
            "metadata": {"score": 0.9}
        }"""
        val message = chatbaseJson.decodeFromString<Message>(json)
        assertEquals("msg-1", message.id)
        assertEquals(Role.ASSISTANT, message.role)
        assertEquals(1700000000.0, message.createdAt!!, 0.001)
        assertEquals(Feedback.POSITIVE, message.feedback)
        assertEquals(0.9, message.metadata!!.score!!, 0.001)
    }

    @Test
    fun `Message without optional fields`() {
        val json = """{"id": "msg-2", "role": "user", "parts": [{"type": "text", "text": "Hi"}]}"""
        val message = chatbaseJson.decodeFromString<Message>(json)
        assertEquals("msg-2", message.id)
        assertEquals(Role.USER, message.role)
        assertNull(message.createdAt)
        assertNull(message.feedback)
        assertNull(message.metadata)
    }

    // ── Conversation deserialization ─────────────────────────────────

    @Test
    fun `Conversation with messages`() {
        val json = """{
            "id": "conv-1", "createdAt": 100.0, "updatedAt": 200.0, "status": "active",
            "messages": [{"id": "m1", "role": "user", "parts": [{"type": "text", "text": "hey"}]}]
        }"""
        val conv = chatbaseJson.decodeFromString<Conversation>(json)
        assertEquals("conv-1", conv.id)
        assertNotNull(conv.messages)
        assertEquals(1, conv.messages!!.size)
    }

    @Test
    fun `Conversation without messages`() {
        val json = """{"id": "conv-2", "createdAt": 100.0, "updatedAt": 200.0, "status": "active"}"""
        val conv = chatbaseJson.decodeFromString<Conversation>(json)
        assertNull(conv.messages)
    }

    // ── Enum serialization ───────────────────────────────────────────

    @Test
    fun `Feedback serializes to lowercase`() {
        val json = chatbaseJson.encodeToString(Feedback.serializer(), Feedback.POSITIVE)
        assertEquals("\"positive\"", json)
    }

    @Test
    fun `FinishReason serializes to lowercase`() {
        val json = chatbaseJson.encodeToString(FinishReason.serializer(), FinishReason.STOP)
        assertEquals("\"stop\"", json)
    }

    @Test
    fun `Role serializes to lowercase`() {
        val json = chatbaseJson.encodeToString(Role.serializer(), Role.ASSISTANT)
        assertEquals("\"assistant\"", json)
    }

    // ── ChatRequest serialization ────────────────────────────────────

    @Test
    fun `ChatRequest omits nulls`() {
        val request = ChatRequest(message = "hi")
        val json = chatbaseJson.encodeToString(ChatRequest.serializer(), request)
        val obj = chatbaseJson.parseToJsonElement(json).jsonObject
        assertFalse(obj.containsKey("conversationId"))
        assertFalse(obj.containsKey("userId"))
    }

    @Test
    fun `ChatRequest includes defaults`() {
        val request = ChatRequest(message = "hi")
        val json = chatbaseJson.encodeToString(ChatRequest.serializer(), request)
        val obj = chatbaseJson.parseToJsonElement(json).jsonObject
        assertEquals("hi", obj["message"]!!.jsonPrimitive.content)
        assertEquals("false", obj["stream"]!!.jsonPrimitive.content)
    }

    // ── Page getNextPage excluded from serialization ─────────────────

    @Test
    fun `Page serialization excludes getNextPage`() {
        val page = Page(data = listOf("a", "b"), cursor = "c1", hasMore = true, total = 5)
        page.getNextPage = { null }
        val json = chatbaseJson.encodeToString(Page.serializer(String.serializer()), page)
        assertFalse("getNextPage should not appear in JSON", json.contains("getNextPage"))
    }

    @Test
    fun `Page deserialization works without getNextPage field`() {
        val json = """{"data":["x"],"cursor":"c2","hasMore":false,"total":1}"""
        val page = chatbaseJson.decodeFromString(Page.serializer(String.serializer()), json)
        assertEquals(listOf("x"), page.data)
        assertNull(page.getNextPage)
    }

    // ── Unknown keys ignored ─────────────────────────────────────────

    @Test
    fun `unknown keys are ignored`() {
        val json = """{"id": "msg-1", "role": "user", "parts": [], "unknownField": 42, "anotherUnknown": "val"}"""
        val message = chatbaseJson.decodeFromString<Message>(json)
        assertEquals("msg-1", message.id)
    }
}
