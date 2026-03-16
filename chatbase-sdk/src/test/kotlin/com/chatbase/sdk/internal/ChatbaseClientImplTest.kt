package com.chatbase.sdk.internal

import com.chatbase.sdk.*
import com.chatbase.sdk.exception.ChatbaseException
import com.chatbase.sdk.model.*
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ChatbaseClientImplTest {

    private lateinit var server: MockWebServer
    private lateinit var client: ChatbaseClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = createMockServerClient(server)
    }

    @After
    fun tearDown() {
        client.close()
        server.shutdown()
    }

    // ── health() ─────────────────────────────────────────────────────

    @Test
    fun `health sends GET to correct path`() = runBlocking {
        server.enqueueJson(TestFixtures.HEALTH_JSON)
        client.health()
        val recorded = server.takeRequest()
        assertEquals("GET", recorded.method)
        assertTrue(recorded.path!!.endsWith("/api/v2/health"))
    }

    @Test
    fun `health parses response`() = runBlocking {
        server.enqueueJson(TestFixtures.HEALTH_JSON)
        val health = client.health()
        assertEquals("ok", health.status)
        assertEquals(1700000000.0, health.timestamp, 0.001)
    }

    @Test
    fun `health includes auth header`() = runBlocking {
        server.enqueueJson(TestFixtures.HEALTH_JSON)
        client.health()
        val recorded = server.takeRequest()
        assertEquals("Bearer test-api-key", recorded.getHeader("Authorization"))
    }

    // ── generateResult() ──────────────────────────────────────────────

    @Test
    fun `generateResult sends POST to correct path`() = runBlocking {
        server.enqueueJson(TestFixtures.CHAT_RESPONSE_JSON)
        client.generateResult(agentId = "agent-1", message = "Hello")
        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertTrue(recorded.path!!.endsWith("/api/v2/agents/agent-1/chat"))
    }

    @Test
    fun `generateResult request body contains message and stream false`() = runBlocking {
        server.enqueueJson(TestFixtures.CHAT_RESPONSE_JSON)
        client.generateResult(agentId = "agent-1", message = "Hello")
        val recorded = server.takeRequest()
        val body = recorded.body.readUtf8()
        assertTrue(body.contains("\"message\":\"Hello\""))
        assertTrue(body.contains("\"stream\":false"))
    }

    @Test
    fun `generateResult request omits null optional fields`() = runBlocking {
        server.enqueueJson(TestFixtures.CHAT_RESPONSE_JSON)
        client.generateResult(agentId = "agent-1", message = "Hello")
        val recorded = server.takeRequest()
        val body = recorded.body.readUtf8()
        assertFalse(body.contains("\"conversationId\""))
        assertFalse(body.contains("\"userId\""))
    }

    @Test
    fun `generateResult request includes conversationId and userId when provided`() = runBlocking {
        server.enqueueJson(TestFixtures.CHAT_RESPONSE_JSON)
        client.generateResult(agentId = "agent-1", message = "Hello", conversationId = "conv-1", userId = "user-1")
        val recorded = server.takeRequest()
        val body = recorded.body.readUtf8()
        assertTrue(body.contains("\"conversationId\":\"conv-1\""))
        assertTrue(body.contains("\"userId\":\"user-1\""))
    }

    @Test
    fun `generateResult parses data wrapper`() = runBlocking {
        server.enqueueJson(TestFixtures.CHAT_RESPONSE_JSON)
        val response = client.generateResult(agentId = "agent-1", message = "Hello")
        assertEquals("msg-001", response.id)
        assertEquals("assistant", response.role)
        assertEquals(FinishReason.STOP, response.metadata.finishReason)
        assertEquals("conv-001", response.metadata.conversationId)
        assertEquals("user-001", response.metadata.userId)
        assertEquals(0.5, response.metadata.usage.credits, 0.001)
    }

    @Test
    fun `generateResult parses text part`() = runBlocking {
        server.enqueueJson(TestFixtures.CHAT_RESPONSE_JSON)
        val response = client.generateResult(agentId = "agent-1", message = "Hello")
        assertEquals(1, response.parts.size)
        val part = response.parts[0] as Part.Text
        assertEquals("Hello there!", part.text)
    }

    @Test
    fun `generateResult parses tool-call and tool-result parts`() = runBlocking {
        server.enqueueJson(TestFixtures.CHAT_TOOL_PARTS_JSON)
        val response = client.generateResult(agentId = "agent-1", message = "Search something")
        assertEquals(3, response.parts.size)

        val toolCall = response.parts[0] as Part.ToolCall
        assertEquals("tc-1", toolCall.toolCallId)
        assertEquals("search", toolCall.toolName)
        assertNotNull(toolCall.input)

        val toolResult = response.parts[1] as Part.ToolResult
        assertEquals("tc-1", toolResult.toolCallId)
        assertEquals("search", toolResult.toolName)

        val text = response.parts[2] as Part.Text
        assertEquals("Based on the search...", text.text)
    }

    @Test
    fun `generateResult throws when data field is missing`() = runBlocking {
        server.enqueueJson(TestFixtures.MISSING_DATA_JSON)
        try {
            client.generateResult(agentId = "agent-1", message = "Hello")
            fail("Should throw ChatbaseException")
        } catch (e: ChatbaseException) {
            assertTrue(e.message!!.contains("data"))
        }
    }

    // ── generateText() ────────────────────────────────────────────────

    @Test
    fun `generateText returns joined text parts`() = runBlocking {
        server.enqueueJson(TestFixtures.CHAT_RESPONSE_JSON)
        val text = client.generateText(agentId = "agent-1", message = "Hello")
        assertEquals("Hello there!", text)
    }

    @Test
    fun `generateText returns empty string when no text parts`() = runBlocking {
        server.enqueueJson(TestFixtures.CHAT_TOOL_PARTS_JSON)
        val text = client.generateText(agentId = "agent-1", message = "Search something")
        assertEquals("Based on the search...", text)
    }

    // ── retryResult() ─────────────────────────────────────────────────

    @Test
    fun `retryResult sends POST to correct path`() = runBlocking {
        server.enqueueJson(TestFixtures.CHAT_RESPONSE_JSON)
        client.retryResult(agentId = "agent-1", conversationId = "conv-1", messageId = "msg-1")
        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertTrue(recorded.path!!.endsWith("/api/v2/agents/agent-1/conversations/conv-1/retry"))
    }

    @Test
    fun `retryResult request body contains messageId and stream false`() = runBlocking {
        server.enqueueJson(TestFixtures.CHAT_RESPONSE_JSON)
        client.retryResult(agentId = "agent-1", conversationId = "conv-1", messageId = "msg-1")
        val recorded = server.takeRequest()
        val body = recorded.body.readUtf8()
        assertTrue(body.contains("\"messageId\":\"msg-1\""))
        assertTrue(body.contains("\"stream\":false"))
    }

    @Test
    fun `retryResult parses response`() = runBlocking {
        server.enqueueJson(TestFixtures.CHAT_RESPONSE_JSON)
        val response = client.retryResult(agentId = "agent-1", conversationId = "conv-1", messageId = "msg-1")
        assertEquals("msg-001", response.id)
    }

    // ── retryText() ───────────────────────────────────────────────────

    @Test
    fun `retryText returns joined text parts`() = runBlocking {
        server.enqueueJson(TestFixtures.CHAT_RESPONSE_JSON)
        val text = client.retryText(agentId = "agent-1", conversationId = "conv-1", messageId = "msg-1")
        assertEquals("Hello there!", text)
    }

    // ── listConversations() ──────────────────────────────────────────

    @Test
    fun `listConversations sends GET to correct path`() = runBlocking {
        server.enqueueJson(TestFixtures.PAGINATED_CONVERSATIONS_JSON)
        client.listConversations(agentId = "agent-1")
        val recorded = server.takeRequest()
        assertEquals("GET", recorded.method)
        assertTrue(recorded.path!!.contains("/api/v2/agents/agent-1/conversations"))
    }

    @Test
    fun `listConversations includes query params`() = runBlocking {
        server.enqueueJson(TestFixtures.PAGINATED_CONVERSATIONS_JSON)
        client.listConversations(agentId = "agent-1", cursor = "abc", limit = 5)
        val recorded = server.takeRequest()
        assertTrue(recorded.path!!.contains("cursor=abc"))
        assertTrue(recorded.path!!.contains("limit=5"))
    }

    @Test
    fun `listConversations parses pagination`() = runBlocking {
        server.enqueueJson(TestFixtures.PAGINATED_CONVERSATIONS_JSON)
        val page = client.listConversations(agentId = "agent-1")
        assertEquals(2, page.data.size)
        assertEquals("cursor-abc", page.cursor)
        assertTrue(page.hasMore)
        assertEquals(10, page.total)
        assertEquals("conv-1", page.data[0].id)
        assertEquals("Test", page.data[1].title)
    }

    @Test
    fun `listConversations sets getNextPage when hasMore is true`() = runBlocking {
        server.enqueueJson(TestFixtures.PAGINATED_CONVERSATIONS_JSON)
        val page = client.listConversations(agentId = "agent-1")
        assertNotNull("getNextPage should be set when hasMore=true", page.getNextPage)
    }

    @Test
    fun `listConversations sets getNextPage to null when hasMore is false`() = runBlocking {
        server.enqueueJson(TestFixtures.PAGINATED_CONVERSATIONS_LAST_PAGE_JSON)
        val page = client.listConversations(agentId = "agent-1")
        assertNull("getNextPage should be null when hasMore=false", page.getNextPage)
    }

    @Test
    fun `listConversations getNextPage fetches next page with cursor`() = runBlocking {
        server.enqueueJson(TestFixtures.PAGINATED_CONVERSATIONS_JSON)
        server.enqueueJson(TestFixtures.PAGINATED_CONVERSATIONS_LAST_PAGE_JSON)

        val firstPage = client.listConversations(agentId = "agent-1", limit = 2)
        assertEquals(2, firstPage.data.size)
        assertNotNull(firstPage.getNextPage)

        val secondPage = firstPage.getNextPage!!.invoke()!!
        assertEquals(1, secondPage.data.size)
        assertEquals("conv-3", secondPage.data[0].id)
        assertNull("Last page should have null getNextPage", secondPage.getNextPage)

        // Verify the second request used the cursor from the first page
        server.takeRequest() // discard first request
        val secondRequest = server.takeRequest()
        assertTrue(secondRequest.path!!.contains("cursor=cursor-abc"))
    }

    // ── listMessages() getNextPage ────────────────────────────────────

    @Test
    fun `listMessages sets getNextPage to null when hasMore is false`() = runBlocking {
        server.enqueueJson(TestFixtures.PAGINATED_MESSAGES_JSON)
        val page = client.listMessages(agentId = "agent-1", conversationId = "conv-1")
        assertNull("getNextPage should be null when hasMore=false", page.getNextPage)
    }

    // ── listUserConversations() getNextPage ───────────────────────────

    @Test
    fun `listUserConversations sets getNextPage when hasMore is true`() = runBlocking {
        server.enqueueJson(TestFixtures.PAGINATED_CONVERSATIONS_JSON)
        val page = client.listUserConversations(agentId = "agent-1", userId = "user-42")
        assertNotNull("getNextPage should be set when hasMore=true", page.getNextPage)
    }

    @Test
    fun `listUserConversations getNextPage fetches next page with cursor`() = runBlocking {
        server.enqueueJson(TestFixtures.PAGINATED_CONVERSATIONS_JSON)
        server.enqueueJson(TestFixtures.PAGINATED_CONVERSATIONS_LAST_PAGE_JSON)

        val firstPage = client.listUserConversations(agentId = "agent-1", userId = "user-42", limit = 2)
        val secondPage = firstPage.getNextPage!!.invoke()!!

        assertEquals(1, secondPage.data.size)
        server.takeRequest()
        val secondRequest = server.takeRequest()
        assertTrue(secondRequest.path!!.contains("cursor=cursor-abc"))
        assertTrue(secondRequest.path!!.contains("/users/user-42/"))
    }

    // ── getConversation() ────────────────────────────────────────────

    @Test
    fun `getConversation sends GET to correct path`() = runBlocking {
        server.enqueueJson(TestFixtures.SINGLE_CONVERSATION_JSON)
        client.getConversation(agentId = "agent-1", conversationId = "conv-100")
        val recorded = server.takeRequest()
        assertEquals("GET", recorded.method)
        assertTrue(recorded.path!!.endsWith("/api/v2/agents/agent-1/conversations/conv-100"))
    }

    @Test
    fun `getConversation parses conversation with messages`() = runBlocking {
        server.enqueueJson(TestFixtures.SINGLE_CONVERSATION_JSON)
        val conversation = client.getConversation(agentId = "agent-1", conversationId = "conv-100")
        assertEquals("conv-100", conversation.id)
        assertEquals("Test Conversation", conversation.title)
        assertEquals("user-42", conversation.userId)
        assertEquals("active", conversation.status)
        assertNotNull(conversation.messages)
        assertEquals(2, conversation.messages!!.size)
    }

    // ── listMessages() ───────────────────────────────────────────────

    @Test
    fun `listMessages sends GET to correct path`() = runBlocking {
        server.enqueueJson(TestFixtures.PAGINATED_MESSAGES_JSON)
        client.listMessages(agentId = "agent-1", conversationId = "conv-1")
        val recorded = server.takeRequest()
        assertEquals("GET", recorded.method)
        assertTrue(recorded.path!!.contains("/api/v2/agents/agent-1/conversations/conv-1/messages"))
    }

    @Test
    fun `listMessages parses pagination`() = runBlocking {
        server.enqueueJson(TestFixtures.PAGINATED_MESSAGES_JSON)
        val page = client.listMessages(agentId = "agent-1", conversationId = "conv-1")
        assertEquals(2, page.data.size)
        assertNull(page.cursor)
        assertFalse(page.hasMore)
        assertEquals(2, page.total)
        assertEquals(Role.USER, page.data[0].role)
        assertEquals(Role.ASSISTANT, page.data[1].role)
        assertEquals(Feedback.POSITIVE, page.data[1].feedback)
    }

    // ── listUserConversations() ──────────────────────────────────────

    @Test
    fun `listUserConversations sends GET to correct path with userId`() = runBlocking {
        server.enqueueJson(TestFixtures.PAGINATED_CONVERSATIONS_JSON)
        client.listUserConversations(agentId = "agent-1", userId = "user-42")
        val recorded = server.takeRequest()
        assertEquals("GET", recorded.method)
        assertTrue(recorded.path!!.contains("/api/v2/agents/agent-1/users/user-42/conversations"))
    }

    // ── updateFeedback() ─────────────────────────────────────────────

    @Test
    fun `updateFeedback sends PATCH to correct path`() = runBlocking {
        server.enqueueJson(TestFixtures.MESSAGE_WITH_FEEDBACK_JSON)
        client.updateFeedback(agentId = "agent-1", conversationId = "conv-1", messageId = "msg-1", feedback = Feedback.POSITIVE)
        val recorded = server.takeRequest()
        assertEquals("PATCH", recorded.method)
        assertTrue(recorded.path!!.endsWith("/api/v2/agents/agent-1/conversations/conv-1/messages/msg-1/feedback"))
    }

    @Test
    fun `updateFeedback positive feedback body`() = runBlocking {
        server.enqueueJson(TestFixtures.MESSAGE_WITH_FEEDBACK_JSON)
        client.updateFeedback(agentId = "agent-1", conversationId = "conv-1", messageId = "msg-1", feedback = Feedback.POSITIVE)
        val recorded = server.takeRequest()
        val body = recorded.body.readUtf8()
        assertTrue(body.contains("\"feedback\":\"positive\""))
    }

    @Test
    fun `updateFeedback negative feedback body`() = runBlocking {
        server.enqueueJson(TestFixtures.MESSAGE_WITH_FEEDBACK_JSON)
        client.updateFeedback(agentId = "agent-1", conversationId = "conv-1", messageId = "msg-1", feedback = Feedback.NEGATIVE)
        val recorded = server.takeRequest()
        val body = recorded.body.readUtf8()
        assertTrue(body.contains("\"feedback\":\"negative\""))
    }

    @Test
    fun `updateFeedback null feedback sends null`() = runBlocking {
        server.enqueueJson(TestFixtures.MESSAGE_NULL_FEEDBACK_JSON)
        client.updateFeedback(agentId = "agent-1", conversationId = "conv-1", messageId = "msg-1", feedback = null)
        val recorded = server.takeRequest()
        val body = recorded.body.readUtf8()
        assertTrue(body.contains("\"feedback\":null"))
    }

    @Test
    fun `updateFeedback parses returned message with feedback`() = runBlocking {
        server.enqueueJson(TestFixtures.MESSAGE_WITH_FEEDBACK_JSON)
        val message = client.updateFeedback(agentId = "agent-1", conversationId = "conv-1", messageId = "msg-1", feedback = Feedback.POSITIVE)
        assertEquals("msg-fb-1", message.id)
        assertEquals(Role.ASSISTANT, message.role)
        assertEquals(Feedback.POSITIVE, message.feedback)
        assertNotNull(message.metadata)
        assertEquals(0.95, message.metadata!!.score!!, 0.001)
    }

    @Test
    fun `updateFeedback parses returned message without feedback`() = runBlocking {
        server.enqueueJson(TestFixtures.MESSAGE_NULL_FEEDBACK_JSON)
        val message = client.updateFeedback(agentId = "agent-1", conversationId = "conv-1", messageId = "msg-1", feedback = null)
        assertEquals("msg-fb-2", message.id)
        assertNull(message.feedback)
    }
}
