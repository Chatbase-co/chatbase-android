package com.chatbase.sdk.internal

import com.chatbase.sdk.*
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
        client = createTestClient(server)
    }

    @After
    fun tearDown() {
        client.close()
        server.shutdown()
    }

    // ── Headers ──────────────────────────────────────────────────────

    @Test
    fun `requests include User-Agent header`() = runBlocking {
        server.enqueueJson(TestFixtures.PAGINATED_CONVERSATIONS_JSON)
        client.listConversations()
        val recorded = server.takeRequest()
        assertEquals("Chatbase-Android-SDK/1.0.0", recorded.getHeader("User-Agent"))
    }

    @Test
    fun `requests include X-Device-Id header`() = runBlocking {
        server.enqueueJson(TestFixtures.PAGINATED_CONVERSATIONS_JSON)
        client.listConversations()
        val recorded = server.takeRequest()
        assertEquals(TestFixtures.TEST_DEVICE_ID, recorded.getHeader("X-Device-Id"))
    }

    @Test
    fun `requests do not include Authorization header`() = runBlocking {
        server.enqueueJson(TestFixtures.PAGINATED_CONVERSATIONS_JSON)
        client.listConversations()
        val recorded = server.takeRequest()
        assertNull(recorded.getHeader("Authorization"))
    }

    @Test
    fun `requests do not include X-User-Token when not identified`() = runBlocking {
        server.enqueueJson(TestFixtures.PAGINATED_CONVERSATIONS_JSON)
        client.listConversations()
        val recorded = server.takeRequest()
        assertNull(recorded.getHeader("X-User-Token"))
    }

    // ── Identity ─────────────────────────────────────────────────────

    @Test
    fun `deviceId returns configured device id`() {
        assertEquals(TestFixtures.TEST_DEVICE_ID, client.deviceId)
    }

    @Test
    fun `isIdentified is false initially`() {
        assertFalse(client.isIdentified)
    }

    @Test
    fun `identify stores JWT and userId`() = runBlocking {
        server.enqueueJson(TestFixtures.VERIFY_RESPONSE_JSON)
        client.identify("test-jwt-token")
        assertTrue(client.isIdentified)
        assertEquals("user-42", client.currentUserId)
    }

    @Test
    fun `after identify requests include X-User-Token`() = runBlocking {
        server.enqueueJson(TestFixtures.VERIFY_RESPONSE_JSON)
        client.identify("test-jwt-token")

        server.enqueueJson(TestFixtures.PAGINATED_CONVERSATIONS_JSON)
        client.listConversations()

        server.takeRequest() // discard verify request
        val recorded = server.takeRequest()
        assertEquals("test-jwt-token", recorded.getHeader("X-User-Token"))
    }

    @Test
    fun `logout clears identity`() = runBlocking {
        server.enqueueJson(TestFixtures.VERIFY_RESPONSE_JSON)
        client.identify("test-jwt-token")
        assertTrue(client.isIdentified)

        client.logout()
        assertFalse(client.isIdentified)
        assertNull(client.currentUserId)
    }

    // ── Verify ───────────────────────────────────────────────────────

    @Test
    fun `verify sends POST to correct path`() = runBlocking {
        server.enqueueJson(TestFixtures.VERIFY_RESPONSE_JSON)
        client.verify("jwt-token")
        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertTrue(recorded.path!!.contains("/${TestFixtures.TEST_AGENT_ID}/verify"))
        val body = recorded.body.readUtf8()
        assertTrue(body.contains("\"token\":\"jwt-token\""))
    }

    // ── Conversation state ───────────────────────────────────────────

    @Test
    fun `currentConversationId is null initially`() {
        assertNull(client.currentConversationId)
    }

    @Test
    fun `newConversation clears conversationId`() {
        client.newConversation()
        assertNull(client.currentConversationId)
    }

    // ── listConversations ────────────────────────────────────────────

    @Test
    fun `listConversations sends GET to correct path`() = runBlocking {
        server.enqueueJson(TestFixtures.PAGINATED_CONVERSATIONS_JSON)
        client.listConversations()
        val recorded = server.takeRequest()
        assertEquals("GET", recorded.method)
        assertTrue(recorded.path!!.contains("/${TestFixtures.TEST_AGENT_ID}/conversations"))
    }

    @Test
    fun `listConversations includes query params`() = runBlocking {
        server.enqueueJson(TestFixtures.PAGINATED_CONVERSATIONS_JSON)
        client.listConversations(cursor = "abc", limit = 5)
        val recorded = server.takeRequest()
        assertTrue(recorded.path!!.contains("cursor=abc"))
        assertTrue(recorded.path!!.contains("limit=5"))
    }

    @Test
    fun `listConversations parses pagination`() = runBlocking {
        server.enqueueJson(TestFixtures.PAGINATED_CONVERSATIONS_JSON)
        val page = client.listConversations()
        assertEquals(2, page.data.size)
        assertEquals("cursor-abc", page.cursor)
        assertTrue(page.hasMore)
        assertEquals(10, page.total)
        assertEquals("conv-1", page.data[0].id)
        assertEquals(ConversationStatus.ONGOING, page.data[0].status)
        assertEquals("Test", page.data[1].title)
    }

    @Test
    fun `listConversations sets getNextPage when hasMore`() = runBlocking {
        server.enqueueJson(TestFixtures.PAGINATED_CONVERSATIONS_JSON)
        val page = client.listConversations()
        assertNotNull(page.getNextPage)
    }

    @Test
    fun `listConversations getNextPage null when no more`() = runBlocking {
        server.enqueueJson(TestFixtures.PAGINATED_CONVERSATIONS_LAST_PAGE_JSON)
        val page = client.listConversations()
        assertNull(page.getNextPage)
    }

    @Test
    fun `listConversations getNextPage fetches with cursor`() = runBlocking {
        server.enqueueJson(TestFixtures.PAGINATED_CONVERSATIONS_JSON)
        server.enqueueJson(TestFixtures.PAGINATED_CONVERSATIONS_LAST_PAGE_JSON)

        val first = client.listConversations(limit = 2)
        val second = first.getNextPage!!.invoke()!!

        assertEquals(1, second.data.size)
        assertEquals("conv-3", second.data[0].id)
        assertEquals(ConversationStatus.ARCHIVED, second.data[0].status)

        server.takeRequest()
        val secondReq = server.takeRequest()
        assertTrue(secondReq.path!!.contains("cursor=cursor-abc"))
    }

    // ── listMessages ─────────────────────────────────────────────────

    @Test
    fun `listMessages sends GET to correct path`() = runBlocking {
        server.enqueueJson(TestFixtures.PAGINATED_MESSAGES_JSON)
        client.listMessages("conv-1")
        val recorded = server.takeRequest()
        assertEquals("GET", recorded.method)
        assertTrue(recorded.path!!.contains("/${TestFixtures.TEST_AGENT_ID}/conversations/conv-1/messages"))
    }

    @Test
    fun `listMessages parses messages`() = runBlocking {
        server.enqueueJson(TestFixtures.PAGINATED_MESSAGES_JSON)
        val page = client.listMessages("conv-1")
        assertEquals(2, page.data.size)
        assertEquals(Role.USER, page.data[0].role)
        assertEquals(Role.ASSISTANT, page.data[1].role)
        assertEquals(Feedback.POSITIVE, page.data[1].feedback)
    }

    // ── Tools ────────────────────────────────────────────────────────

    @Test
    fun `tool registration and removal`() {
        client.tool("myTool") { mapOf("result" to "ok") }
        client.removeTool("myTool")
        // No assertion — just verify no exception
    }

    // ── Error handling ───────────────────────────────────────────────

    @Test
    fun `API error response is parsed`() = runBlocking {
        server.enqueueJson(TestFixtures.API_ERROR_JSON, code = 429)
        try {
            client.listConversations()
            fail("Should throw")
        } catch (e: com.chatbase.sdk.exception.ApiException) {
            assertEquals(429, e.httpStatus)
            assertEquals("RATE_LIMITED", e.errorCode)
            assertTrue(e.isRateLimited)
        }
    }
}
