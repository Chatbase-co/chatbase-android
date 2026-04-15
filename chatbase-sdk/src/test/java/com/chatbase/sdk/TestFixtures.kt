package com.chatbase.sdk

import com.chatbase.sdk.internal.AnonymousIdProvider
import com.chatbase.sdk.internal.ChatbaseClientImpl
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

object TestFixtures {

    const val TEST_AGENT_ID = "test-agent"
    const val TEST_DEVICE_ID = "test-device-id-123"

    // ── Chat response (text part only) ───────────────────────────────
    val CHAT_RESPONSE_JSON = """{
        "data": {
            "id": "msg-001",
            "role": "assistant",
            "parts": [{"type": "text", "text": "Hello there!"}],
            "metadata": {
                "userMessageId": "umsg-001",
                "conversationId": "conv-001",
                "finishReason": "stop",
                "usage": {"credits": 0.5}
            }
        }
    }"""

    // ── Chat response with tool-call parts ───────────────────────────
    val CHAT_TOOL_CALL_JSON = """{
        "data": {
            "id": "msg-002",
            "role": "assistant",
            "parts": [
                {"type": "tool-call", "toolCallId": "tc-1", "toolName": "search", "input": {"query": "test"}}
            ],
            "metadata": {
                "userMessageId": "umsg-002",
                "conversationId": "conv-002",
                "finishReason": "tool-calls",
                "usage": {"credits": 1.0}
            }
        }
    }"""

    // ── Tool result success ──────────────────────────────────────────
    val TOOL_RESULT_SUCCESS_JSON = """{"data": {"success": true}}"""

    // ── Paginated conversations ──────────────────────────────────────
    val PAGINATED_CONVERSATIONS_JSON = """{
        "data": [
            {"id": "conv-1", "createdAt": 1700000000, "updatedAt": 1700000001, "status": "ongoing"},
            {"id": "conv-2", "createdAt": 1700000002, "updatedAt": 1700000003, "status": "ongoing", "title": "Test"}
        ],
        "pagination": {"cursor": "cursor-abc", "hasMore": true, "total": 10}
    }"""

    // ── Paginated conversations (last page) ──────────────────────────
    val PAGINATED_CONVERSATIONS_LAST_PAGE_JSON = """{
        "data": [
            {"id": "conv-3", "createdAt": 1700000004, "updatedAt": 1700000005, "status": "ended"}
        ],
        "pagination": {"cursor": null, "hasMore": false, "total": 10}
    }"""

    // ── Paginated messages ───────────────────────────────────────────
    val PAGINATED_MESSAGES_JSON = """{
        "data": [
            {"id": "msg-1", "role": "user", "parts": [{"type": "text", "text": "Hi"}]},
            {"id": "msg-2", "role": "assistant", "parts": [{"type": "text", "text": "Hello!"}], "feedback": "positive"}
        ],
        "pagination": {"cursor": null, "hasMore": false, "total": 2}
    }"""

    // ── Verify response ──────────────────────────────────────────────
    val VERIFY_RESPONSE_JSON = """{"data": {"userId": "user-42"}}"""

    // ── API error ────────────────────────────────────────────────────
    val API_ERROR_JSON = """{
        "error": {
            "code": "RATE_LIMITED",
            "message": "Too many requests",
            "details": {"retryAfter": "30"}
        }
    }"""

    val MISSING_DATA_JSON = """{"something": "else"}"""
}

// ── MockWebServer helpers ────────────────────────────────────────────

fun MockWebServer.enqueueJson(body: String, code: Int = 200) {
    enqueue(
        MockResponse()
            .setResponseCode(code)
            .setHeader("Content-Type", "application/json")
            .setBody(body)
    )
}

fun MockWebServer.enqueueSse(vararg events: String) {
    val body = events.joinToString("") { "data: $it\n\n" } + "data: [DONE]\n\n"
    enqueue(
        MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "text/event-stream")
            .setBody(body)
    )
}

fun createTestClient(server: MockWebServer): ChatbaseClient {
    val baseUrl = server.url("/api/sdk/agents").toString().removeSuffix("/")
    val config = ChatbaseConfig(
        agentId = TestFixtures.TEST_AGENT_ID,
        baseUrl = baseUrl
    )
    val idProvider = AnonymousIdProvider { TestFixtures.TEST_DEVICE_ID }
    return ChatbaseClientImpl(config, idProvider)
}
