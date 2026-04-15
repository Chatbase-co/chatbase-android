package com.chatbase.sdk.internal

import com.chatbase.sdk.enqueueJson
import com.chatbase.sdk.exception.ApiException
import com.chatbase.sdk.exception.NetworkException
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ApiExecutorTest {

    private lateinit var server: MockWebServer
    private lateinit var executor: ApiExecutor

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val baseUrl = server.url("/").toString().removeSuffix("/")
        executor = ApiExecutor(OkHttpClient(), baseUrl, "test-agent")
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    // ── buildGetRequest ──────────────────────────────────────────────

    @Test
    fun `buildGetRequest produces correct URL with agentId`() {
        val request = executor.buildGetRequest("/conversations")
        assertTrue(request.url.toString().contains("/api/sdk/agents/test-agent/conversations"))
        assertEquals("GET", request.method)
    }

    @Test
    fun `buildGetRequest with query params`() {
        val request = executor.buildGetRequest("/conversations", mapOf("cursor" to "abc", "limit" to "10"))
        val url = request.url
        assertEquals("abc", url.queryParameter("cursor"))
        assertEquals("10", url.queryParameter("limit"))
    }

    @Test
    fun `buildGetRequest omits null query params`() {
        val request = executor.buildGetRequest("/conversations", mapOf("cursor" to null, "limit" to "5"))
        val url = request.url
        assertNull(url.queryParameter("cursor"))
        assertEquals("5", url.queryParameter("limit"))
    }

    // ── buildPostRequest ─────────────────────────────────────────────

    @Test
    fun `buildPostRequest produces correct URL and method`() {
        val request = executor.buildPostRequest("/chat", """{"message":"hi"}""")
        assertTrue(request.url.toString().contains("/api/sdk/agents/test-agent/chat"))
        assertEquals("POST", request.method)
    }

    @Test
    fun `buildPostRequest has JSON content type`() {
        val request = executor.buildPostRequest("/chat", """{"message":"hi"}""")
        assertEquals("application/json; charset=utf-8", request.body?.contentType().toString())
    }

    @Test
    fun `buildPostRequest nested path`() {
        val request = executor.buildPostRequest("/conversations/conv-1/retry", """{"messageId":"m1"}""")
        assertTrue(request.url.toString().contains("/api/sdk/agents/test-agent/conversations/conv-1/retry"))
    }

    // ── executeRequest ───────────────────────────────────────────────

    @Test
    fun `executeRequest success returns body`() = runBlocking {
        server.enqueueJson("""{"data": {"success": true}}""")
        val request = executor.buildPostRequest("/chat", """{}""")
        val body = executor.executeRequest(request)
        assertTrue(body.contains("success"))
    }

    @Test
    fun `executeRequest HTTP error throws ApiException`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(429)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"error":{"code":"RATE_LIMITED","message":"Too many requests"}}""")
        )
        val request = executor.buildGetRequest("/conversations")
        try {
            executor.executeRequest(request)
            fail("Should throw ApiException")
        } catch (e: ApiException) {
            assertEquals(429, e.httpStatus)
            assertEquals("RATE_LIMITED", e.errorCode)
            assertTrue(e.isRateLimited)
        }
    }

    @Test
    fun `executeRequest network failure throws NetworkException`() = runBlocking {
        val closedServer = MockWebServer()
        closedServer.start()
        val url = closedServer.url("/").toString().removeSuffix("/")
        closedServer.shutdown()

        val offlineExecutor = ApiExecutor(OkHttpClient(), url, "test-agent")
        val request = offlineExecutor.buildGetRequest("/conversations")
        try {
            offlineExecutor.executeRequest(request)
            fail("Should throw NetworkException")
        } catch (e: NetworkException) {
            assertNotNull(e.message)
        }
    }

    // ── parseApiError ────────────────────────────────────────────────

    @Test
    fun `parseApiError well-formed JSON`() {
        val error = ApiExecutor.parseApiError(
            400,
            """{"error":{"code":"BAD_REQUEST","message":"Invalid input","details":{"field":"message"}}}"""
        )
        assertEquals(400, error.httpStatus)
        assertEquals("BAD_REQUEST", error.errorCode)
        assertEquals("Invalid input", error.errorMessage)
        assertEquals("message", error.details?.get("field"))
    }

    @Test
    fun `parseApiError invalid JSON`() {
        val error = ApiExecutor.parseApiError(500, "not json at all")
        assertEquals(500, error.httpStatus)
        assertEquals("UNKNOWN_ERROR", error.errorCode)
    }

    @Test
    fun `parseApiError empty body`() {
        val error = ApiExecutor.parseApiError(502, "")
        assertEquals(502, error.httpStatus)
        assertEquals("HTTP 502", error.errorMessage)
    }
}
