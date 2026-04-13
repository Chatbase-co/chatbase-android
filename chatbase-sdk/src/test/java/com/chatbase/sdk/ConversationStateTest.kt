package com.chatbase.sdk

import com.chatbase.sdk.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationStateTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var server: MockWebServer
    private lateinit var client: ChatbaseClient
    private lateinit var conversation: ConversationState

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        server = MockWebServer()
        server.start()
        client = createTestClient(server)
        conversation = ConversationState(client)
    }

    @After
    fun tearDown() {
        conversation.close()
        client.close()
        server.shutdown()
        Dispatchers.resetMain()
    }

    // ── Initial state ────────────────────────────────────────────────

    @Test
    fun `initial state has empty messages and no conversation id`() {
        val state = conversation.state.value
        assertTrue(state.messages.isEmpty())
        assertNull(state.conversationId)
        assertFalse(state.isSending)
        assertFalse(state.isLoadingHistory)
        assertFalse(state.hasMoreHistory)
        assertNull(state.error)
    }

    // ── setConversationId ────────────────────────────────────────────

    @Test
    fun `setConversationId updates state`() {
        conversation.setConversationId("conv-123")
        assertEquals("conv-123", conversation.state.value.conversationId)
    }

    @Test
    fun `setConversationId to null clears id`() {
        conversation.setConversationId("conv-123")
        conversation.setConversationId(null)
        assertNull(conversation.state.value.conversationId)
    }

    // ── clearError ───────────────────────────────────────────────────

    @Test
    fun `clearError resets error to null`() {
        // Force an error by making loadHistory fail
        server.enqueueJson("""{"error": "bad"}""", code = 500)
        runBlocking { conversation.loadHistory("conv-1") }
        assertNotNull(conversation.state.value.error)

        conversation.clearError()
        assertNull(conversation.state.value.error)
    }

    // ── loadHistory ──────────────────────────────────────────────────

    @Test
    fun `loadHistory populates messages in chronological order`() = runBlocking {
        // API fixture returns newest-first: msg-1(user), msg-2(assistant)
        // Reversed for chronological: msg-2(assistant), msg-1(user)
        server.enqueueJson(TestFixtures.PAGINATED_MESSAGES_JSON)
        conversation.loadHistory("conv-1")

        val state = conversation.state.value
        assertEquals("conv-1", state.conversationId)
        assertFalse(state.isLoadingHistory)
        assertFalse(state.hasMoreHistory)
        assertEquals(2, state.messages.size)

        // Reversed from API order: assistant first, then user
        val first = state.messages[0]
        assertEquals(Role.ASSISTANT, first.role)
        assertEquals("Hello!", (first.content as UiMessageContent.Text).text)
        assertEquals("msg-2", first.messageId)

        val second = state.messages[1]
        assertEquals(Role.USER, second.role)
        assertEquals("Hi", (second.content as UiMessageContent.Text).text)
        assertEquals("msg-1", second.messageId)
    }

    @Test
    fun `loadHistory sets hasMoreHistory when page has more`() = runBlocking {
        server.enqueueJson(MESSAGES_WITH_MORE_JSON)
        conversation.loadHistory("conv-1")

        assertTrue(conversation.state.value.hasMoreHistory)
    }

    @Test
    fun `loadHistory error sets error state`() = runBlocking {
        server.enqueueJson(TestFixtures.API_ERROR_JSON, code = 429)
        conversation.loadHistory("conv-1")

        val state = conversation.state.value
        assertFalse(state.isLoadingHistory)
        assertNotNull(state.error)
    }

    // ── loadMoreHistory ──────────────────────────────────────────────

    @Test
    fun `loadMoreHistory does nothing without prior loadHistory`() = runBlocking {
        conversation.loadMoreHistory()
        assertTrue(conversation.state.value.messages.isEmpty())
    }

    @Test
    fun `loadMoreHistory does nothing when no more pages`() = runBlocking {
        server.enqueueJson(TestFixtures.PAGINATED_MESSAGES_JSON)
        conversation.loadHistory("conv-1")

        val messageCountBefore = conversation.state.value.messages.size
        conversation.loadMoreHistory()
        assertEquals(messageCountBefore, conversation.state.value.messages.size)
    }

    // ── sendMessage ──────────────────────────────────────────────────

    @Test
    fun `sendMessage with blank text does nothing`() = runBlocking {
        conversation.sendMessage("   ")
        assertTrue(conversation.state.value.messages.isEmpty())
        assertFalse(conversation.state.value.isSending)
    }

    @Test
    fun `sendMessage creates user message and streams response`() = runBlocking {
        server.enqueueSse(
            SSE_TEXT_START,
            SSE_TEXT_DELTA_HELLO,
            SSE_TEXT_END,
            SSE_MESSAGE_METADATA,
            SSE_FINISH_STOP
        )

        conversation.sendMessage("Hi")

        val state = conversation.state.value
        assertFalse(state.isSending)

        // Should have user message + assistant message
        val userMessages = state.messages.filter { it.role == Role.USER }
        assertEquals(1, userMessages.size)
        assertEquals("Hi", (userMessages[0].content as UiMessageContent.Text).text)

        val assistantMessages = state.messages.filter {
            it.role == Role.ASSISTANT && it.content is UiMessageContent.Text
        }
        assertTrue(assistantMessages.isNotEmpty())

        // Assistant message should have accumulated text
        val lastAssistant = assistantMessages.last()
        val text = (lastAssistant.content as UiMessageContent.Text).text
        assertEquals("Hello", text)
    }

    @Test
    fun `sendMessage updates conversationId from response`() = runBlocking {
        assertNull(conversation.state.value.conversationId)

        server.enqueueSse(
            SSE_TEXT_START,
            SSE_TEXT_DELTA_HELLO,
            SSE_TEXT_END,
            SSE_MESSAGE_METADATA,
            SSE_FINISH_STOP
        )
        conversation.sendMessage("Hi")

        assertEquals("conv-001", conversation.state.value.conversationId)
    }

    @Test
    fun `sendMessage filters out empty text placeholders`() = runBlocking {
        server.enqueueSse(
            SSE_TEXT_START,
            SSE_TEXT_DELTA_HELLO,
            SSE_TEXT_END,
            SSE_MESSAGE_METADATA,
            SSE_FINISH_STOP
        )
        conversation.sendMessage("Hi")

        // No empty-text messages should remain
        val emptyTexts = conversation.state.value.messages.filter {
            it.content is UiMessageContent.Text && (it.content as UiMessageContent.Text).text.isEmpty()
        }
        assertTrue(emptyTexts.isEmpty())
    }

    // ── Page extensions ──────────────────────────────────────────────

    @Test
    fun `toUiMessages converts text parts`() {
        val messages = listOf(
            Message(id = "m1", role = Role.USER, parts = listOf(Part.Text("hello"))),
            Message(id = "m2", role = Role.ASSISTANT, parts = listOf(Part.Text("world")))
        )
        val uiMessages = messages.toUiMessages("conv-1")
        assertEquals(2, uiMessages.size)
        assertEquals(Role.USER, uiMessages[0].role)
        assertEquals("hello", (uiMessages[0].content as UiMessageContent.Text).text)
        assertEquals("m1", uiMessages[0].messageId)
        assertEquals("conv-1", uiMessages[0].conversationId)
        assertEquals(Role.ASSISTANT, uiMessages[1].role)
        assertEquals("world", (uiMessages[1].content as UiMessageContent.Text).text)
    }

    @Test
    fun `toUiMessages converts tool call parts`() {
        val messages = listOf(
            Message(
                id = "m1",
                role = Role.ASSISTANT,
                parts = listOf(
                    Part.ToolCall(toolCallId = "tc-1", toolName = "search", input = null)
                )
            )
        )
        val uiMessages = messages.toUiMessages("conv-1")
        assertEquals(1, uiMessages.size)
        val content = uiMessages[0].content as UiMessageContent.ToolCall
        assertEquals("tc-1", content.toolCallId)
        assertEquals("search", content.toolName)
        assertFalse(content.isExecuting)
    }

    @Test
    fun `chronological reverses page data`() {
        val page = Page(
            data = listOf(
                Message(id = "m2", role = Role.ASSISTANT, parts = listOf(Part.Text("second"))),
                Message(id = "m1", role = Role.USER, parts = listOf(Part.Text("first")))
            ),
            hasMore = false,
            total = 2
        )
        val chrono = page.chronological()
        assertEquals("m1", chrono.data[0].id)
        assertEquals("m2", chrono.data[1].id)
    }

    // ── SSE fixtures ─────────────────────────────────────────────────

    companion object {
        val MESSAGES_WITH_MORE_JSON = """{
            "data": [
                {"id": "msg-1", "role": "user", "parts": [{"type": "text", "text": "Hi"}]}
            ],
            "pagination": {"cursor": "cursor-xyz", "hasMore": true, "total": 5}
        }"""

        const val SSE_TEXT_START = """{"type":"text-start","id":"text-0"}"""
        const val SSE_TEXT_DELTA_HELLO = """{"type":"text-delta","delta":"Hello"}"""
        const val SSE_TEXT_END = """{"type":"text-end","id":"text-0"}"""
        const val SSE_MESSAGE_METADATA = """{"type":"message-metadata","messageMetadata":{"messageId":"msg-001","userMessageId":"umsg-001","conversationId":"conv-001","usage":{"credits":0.5}}}"""
        const val SSE_FINISH_STOP = """{"type":"finish","finishReason":"stop","messageMetadata":{"messageId":"msg-001","userMessageId":"umsg-001","conversationId":"conv-001","usage":{"credits":0.5}}}"""
    }
}
