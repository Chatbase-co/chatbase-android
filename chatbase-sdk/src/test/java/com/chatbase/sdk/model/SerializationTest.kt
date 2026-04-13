package com.chatbase.sdk.model

import com.chatbase.sdk.internal.ChatRequest
import com.chatbase.sdk.internal.chatbaseJson
import kotlinx.serialization.builtins.serializer
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
                "messageId": "msg-1",
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
        assertEquals("msg-1", response.metadata.messageId)
        assertEquals("umsg-1", response.metadata.userMessageId)
        assertEquals("conv-1", response.metadata.conversationId)
        assertEquals("user-1", response.metadata.userId)
        assertEquals(FinishReason.STOP, response.metadata.finishReason)
        assertEquals(1.5, response.metadata.usage!!.credits, 0.001)
    }

    @Test
    fun `ChatResponse with optional metadata fields`() {
        val json = """{
            "id": "msg-2",
            "role": "assistant",
            "parts": [],
            "metadata": {}
        }"""
        val response = chatbaseJson.decodeFromString<ChatResponse>(json)
        assertNull(response.metadata.userId)
        assertEquals(FinishReason.UNKNOWN, response.metadata.finishReason)
        assertNull(response.metadata.usage)
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
            "createdAt": 1700000000,
            "feedback": "positive",
            "metadata": {"score": 0.9}
        }"""
        val message = chatbaseJson.decodeFromString<Message>(json)
        assertEquals("msg-1", message.id)
        assertEquals(Role.ASSISTANT, message.role)
        assertEquals(1700000000L, message.createdAt)
        assertEquals(Feedback.POSITIVE, message.feedback)
        assertEquals(0.9, message.metadata!!.score!!, 0.001)
    }

    @Test
    fun `Message without optional fields`() {
        val json = """{"id": "msg-2", "role": "user", "parts": [{"type": "text", "text": "Hi"}]}"""
        val message = chatbaseJson.decodeFromString<Message>(json)
        assertNull(message.createdAt)
        assertNull(message.feedback)
        assertNull(message.metadata)
    }

    // ── Conversation deserialization ─────────────────────────────────

    @Test
    fun `Conversation with all fields`() {
        val json = """{
            "id": "conv-1", "createdAt": 1700000000, "updatedAt": 1700000001,
            "status": "ongoing", "title": "Test", "userId": "user-1"
        }"""
        val conv = chatbaseJson.decodeFromString<Conversation>(json)
        assertEquals("conv-1", conv.id)
        assertEquals(1700000000L, conv.createdAt)
        assertEquals(1700000001L, conv.updatedAt)
        assertEquals(ConversationStatus.ONGOING, conv.status)
        assertEquals("Test", conv.title)
        assertEquals("user-1", conv.userId)
    }

    @Test
    fun `Conversation with archived status`() {
        val json = """{"id": "conv-2", "createdAt": 100, "updatedAt": 200, "status": "archived"}"""
        val conv = chatbaseJson.decodeFromString<Conversation>(json)
        assertEquals(ConversationStatus.ARCHIVED, conv.status)
    }

    @Test
    fun `Conversation without optional fields`() {
        val json = """{"id": "conv-3", "createdAt": 100, "updatedAt": 200, "status": "ongoing"}"""
        val conv = chatbaseJson.decodeFromString<Conversation>(json)
        assertNull(conv.title)
        assertNull(conv.userId)
    }

    // ── Enum serialization ───────────────────────────────────────────

    @Test
    fun `FinishReason stop serializes`() {
        assertEquals("\"stop\"", chatbaseJson.encodeToString(FinishReason.serializer(), FinishReason.STOP))
    }

    @Test
    fun `FinishReason tool-calls serializes`() {
        assertEquals("\"tool-calls\"", chatbaseJson.encodeToString(FinishReason.serializer(), FinishReason.TOOL_CALLS))
    }

    @Test
    fun `Feedback serializes to lowercase`() {
        assertEquals("\"positive\"", chatbaseJson.encodeToString(Feedback.serializer(), Feedback.POSITIVE))
    }

    @Test
    fun `Role serializes to lowercase`() {
        assertEquals("\"assistant\"", chatbaseJson.encodeToString(Role.serializer(), Role.ASSISTANT))
    }

    // ── ChatRequest serialization ────────────────────────────────────

    @Test
    fun `ChatRequest with message`() {
        val request = ChatRequest(message = "hi")
        val json = chatbaseJson.encodeToString(ChatRequest.serializer(), request)
        val obj = chatbaseJson.parseToJsonElement(json).jsonObject
        assertEquals("hi", obj["message"]!!.jsonPrimitive.content)
        assertTrue(obj.containsKey("stream"))
        assertFalse(obj.containsKey("userId"))
        assertFalse(obj.containsKey("anonymousId"))
    }

    @Test
    fun `ChatRequest without message for tool continuation`() {
        val request = ChatRequest(conversationId = "conv-1", stream = true)
        val json = chatbaseJson.encodeToString(ChatRequest.serializer(), request)
        val obj = chatbaseJson.parseToJsonElement(json).jsonObject
        assertFalse(obj.containsKey("message"))
        assertEquals("conv-1", obj["conversationId"]!!.jsonPrimitive.content)
    }

    // ── Page ─────────────────────────────────────────────────────────

    @Test
    fun `Page serialization excludes getNextPage`() {
        val page = Page(data = listOf("a"), cursor = "c1", hasMore = true, total = 5)
        page.getNextPage = { null }
        val json = chatbaseJson.encodeToString(Page.serializer(String.serializer()), page)
        assertFalse(json.contains("getNextPage"))
    }

    // ── Unknown keys ignored ─────────────────────────────────────────

    @Test
    fun `unknown keys are ignored`() {
        val json = """{"id": "msg-1", "role": "user", "parts": [], "unknownField": 42}"""
        val message = chatbaseJson.decodeFromString<Message>(json)
        assertEquals("msg-1", message.id)
    }
}
