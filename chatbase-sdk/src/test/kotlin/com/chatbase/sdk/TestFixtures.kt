package com.chatbase.sdk

import com.chatbase.sdk.internal.ChatbaseClientImpl
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

object TestFixtures {

    // ── Health ────────────────────────────────────────────────────────
    val HEALTH_JSON = """{"status":"ok","timestamp":1700000000.0}"""

    // ── Chat response (text part only) ───────────────────────────────
    val CHAT_RESPONSE_JSON = """{
        "data": {
            "id": "msg-001",
            "role": "assistant",
            "parts": [{"type": "text", "text": "Hello there!"}],
            "metadata": {
                "userMessageId": "umsg-001",
                "conversationId": "conv-001",
                "userId": "user-001",
                "finishReason": "stop",
                "usage": {"credits": 0.5}
            }
        }
    }"""

    // ── Chat response with tool-call and tool-result parts ───────────
    val CHAT_TOOL_PARTS_JSON = """{
        "data": {
            "id": "msg-002",
            "role": "assistant",
            "parts": [
                {"type": "tool-call", "toolCallId": "tc-1", "toolName": "search", "input": {"query": "test"}},
                {"type": "tool-result", "toolCallId": "tc-1", "toolName": "search", "output": {"results": []}},
                {"type": "text", "text": "Based on the search..."}
            ],
            "metadata": {
                "userMessageId": "umsg-002",
                "conversationId": "conv-002",
                "finishReason": "stop",
                "usage": {"credits": 1.0}
            }
        }
    }"""

    // ── Paginated conversations ──────────────────────────────────────
    val PAGINATED_CONVERSATIONS_JSON = """{
        "data": [
            {"id": "conv-1", "createdAt": 1700000000.0, "updatedAt": 1700000001.0, "status": "active"},
            {"id": "conv-2", "createdAt": 1700000002.0, "updatedAt": 1700000003.0, "status": "active", "title": "Test"}
        ],
        "pagination": {"cursor": "cursor-abc", "hasMore": true, "total": 10}
    }"""

    // ── Paginated conversations (last page) ──────────────────────────
    val PAGINATED_CONVERSATIONS_LAST_PAGE_JSON = """{
        "data": [
            {"id": "conv-3", "createdAt": 1700000004.0, "updatedAt": 1700000005.0, "status": "active"}
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

    // ── Single conversation with messages ────────────────────────────
    val SINGLE_CONVERSATION_JSON = """{
        "data": {
            "id": "conv-100",
            "createdAt": 1700000000.0,
            "updatedAt": 1700000001.0,
            "status": "active",
            "userId": "user-42",
            "title": "Test Conversation",
            "messages": [
                {"id": "msg-1", "role": "user", "parts": [{"type": "text", "text": "Hello"}]},
                {"id": "msg-2", "role": "assistant", "parts": [{"type": "text", "text": "Hi!"}]}
            ]
        }
    }"""

    // ── Message with feedback ────────────────────────────────────────
    val MESSAGE_WITH_FEEDBACK_JSON = """{
        "data": {
            "id": "msg-fb-1",
            "role": "assistant",
            "parts": [{"type": "text", "text": "Great answer"}],
            "feedback": "positive",
            "metadata": {"score": 0.95}
        }
    }"""

    val MESSAGE_NULL_FEEDBACK_JSON = """{
        "data": {
            "id": "msg-fb-2",
            "role": "assistant",
            "parts": [{"type": "text", "text": "OK answer"}]
        }
    }"""

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

fun createMockServerClient(server: MockWebServer): ChatbaseClient {
    val baseUrl = server.url("/api/v2").toString().removeSuffix("/")
    val config = ChatbaseConfig(
        apiKey = "test-api-key",
        baseUrl = baseUrl
    )
    return ChatbaseClientImpl(config)
}
