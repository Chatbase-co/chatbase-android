package com.chatbase.sdk.internal

import com.chatbase.sdk.ChatbaseClient
import com.chatbase.sdk.ChatbaseConfig
import com.chatbase.sdk.exception.ApiException
import com.chatbase.sdk.exception.ChatbaseException
import com.chatbase.sdk.exception.NetworkException
import com.chatbase.sdk.model.*
import com.chatbase.sdk.streaming.ChatStreamEvent
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources

internal class ChatbaseClientImpl(
    private val config: ChatbaseConfig
) : ChatbaseClient {

    private val httpClient: OkHttpClient = HttpClientFactory.create(config)
    private val sseClient: OkHttpClient = HttpClientFactory.createForSse(config)
    private val api = ApiExecutor(httpClient, config.baseUrl)

    override suspend fun health(): HealthResponse {
        val request = api.buildGetRequest("/health")
        val body = api.executeRequest(request)
        return chatbaseJson.decodeFromString<HealthResponse>(body)
    }

    override suspend fun generateResult(
        agentId: String,
        message: String,
        conversationId: String?,
        userId: String?
    ): ChatResponse {
        val requestBody = chatbaseJson.encodeToString(
            ChatRequest.serializer(),
            ChatRequest(message = message, conversationId = conversationId, stream = false, userId = userId)
        )
        val request = api.buildPostRequest("/agents/$agentId/chat", requestBody)
        val body = api.executeRequest(request)
        val wrapper = chatbaseJson.parseToJsonElement(body).jsonObject
        val data = wrapper["data"] ?: throw ChatbaseException("Missing 'data' field in response")
        return chatbaseJson.decodeFromString<ChatResponse>(data.toString())
    }

    override suspend fun generateText(
        agentId: String,
        message: String,
        conversationId: String?,
        userId: String?
    ): String {
        val response = generateResult(agentId, message, conversationId, userId)
        return response.parts.filterIsInstance<Part.Text>().joinToString("") { it.text }
    }

    override fun stream(
        agentId: String,
        message: String,
        conversationId: String?,
        userId: String?
    ): Flow<ChatStreamEvent> = createSseFlow(
        path = "/agents/$agentId/chat",
        body = chatbaseJson.encodeToString(
            ChatRequest.serializer(),
            ChatRequest(message = message, conversationId = conversationId, stream = true, userId = userId)
        )
    )

    override fun streamText(
        agentId: String,
        message: String,
        conversationId: String?,
        userId: String?
    ): Flow<String> =
        stream(agentId, message, conversationId, userId)
            .filterIsInstance<ChatStreamEvent.TextDelta>()
            .map { it.text }

    override suspend fun retryResult(
        agentId: String,
        conversationId: String,
        messageId: String
    ): ChatResponse {
        val requestBody = chatbaseJson.encodeToString(
            RetryRequest.serializer(),
            RetryRequest(messageId = messageId, stream = false)
        )
        val request = api.buildPostRequest("/agents/$agentId/conversations/$conversationId/retry", requestBody)
        val body = api.executeRequest(request)
        val wrapper = chatbaseJson.parseToJsonElement(body).jsonObject
        val data = wrapper["data"] ?: throw ChatbaseException("Missing 'data' field in response")
        return chatbaseJson.decodeFromString<ChatResponse>(data.toString())
    }

    override suspend fun retryText(
        agentId: String,
        conversationId: String,
        messageId: String
    ): String {
        val response = retryResult(agentId, conversationId, messageId)
        return response.parts.filterIsInstance<Part.Text>().joinToString("") { it.text }
    }

    override fun retryStream(
        agentId: String,
        conversationId: String,
        messageId: String
    ): Flow<ChatStreamEvent> = createSseFlow(
        path = "/agents/$agentId/conversations/$conversationId/retry",
        body = chatbaseJson.encodeToString(
            RetryRequest.serializer(),
            RetryRequest(messageId = messageId, stream = true)
        )
    )

    override fun retryStreamText(
        agentId: String,
        conversationId: String,
        messageId: String
    ): Flow<String> =
        retryStream(agentId, conversationId, messageId)
            .filterIsInstance<ChatStreamEvent.TextDelta>()
            .map { it.text }

    override suspend fun listConversations(
        agentId: String,
        cursor: String?,
        limit: Int?
    ): Page<Conversation> {
        val request = api.buildGetRequest(
            "/agents/$agentId/conversations",
            buildMap {
                cursor?.let { put("cursor", it) }
                limit?.let { put("limit", it.toString()) }
            }
        )
        val body = api.executeRequest(request)
        val page = parsePaginatedResponse<Conversation>(body)
        page.getNextPage = if (page.hasMore && page.cursor != null) {
            { listConversations(agentId, cursor = page.cursor, limit = limit) }
        } else null
        return page
    }

    override suspend fun getConversation(
        agentId: String,
        conversationId: String
    ): Conversation {
        val request = api.buildGetRequest("/agents/$agentId/conversations/$conversationId")
        val body = api.executeRequest(request)
        val wrapper = chatbaseJson.parseToJsonElement(body).jsonObject
        val data = wrapper["data"] ?: throw ChatbaseException("Missing 'data' field in response")
        return chatbaseJson.decodeFromString<Conversation>(data.toString())
    }

    override suspend fun listMessages(
        agentId: String,
        conversationId: String,
        cursor: String?,
        limit: Int?
    ): Page<Message> {
        val request = api.buildGetRequest(
            "/agents/$agentId/conversations/$conversationId/messages",
            buildMap {
                cursor?.let { put("cursor", it) }
                limit?.let { put("limit", it.toString()) }
            }
        )
        val body = api.executeRequest(request)
        val page = parsePaginatedResponse<Message>(body)
        page.getNextPage = if (page.hasMore && page.cursor != null) {
            { listMessages(agentId, conversationId, cursor = page.cursor, limit = limit) }
        } else null
        return page
    }

    override suspend fun listUserConversations(
        agentId: String,
        userId: String,
        cursor: String?,
        limit: Int?
    ): Page<Conversation> {
        val request = api.buildGetRequest(
            "/agents/$agentId/users/$userId/conversations",
            buildMap {
                cursor?.let { put("cursor", it) }
                limit?.let { put("limit", it.toString()) }
            }
        )
        val body = api.executeRequest(request)
        val page = parsePaginatedResponse<Conversation>(body)
        page.getNextPage = if (page.hasMore && page.cursor != null) {
            { listUserConversations(agentId, userId, cursor = page.cursor, limit = limit) }
        } else null
        return page
    }

    override suspend fun updateFeedback(
        agentId: String,
        conversationId: String,
        messageId: String,
        feedback: Feedback?
    ): Message {
        val feedbackValue = feedback?.name?.lowercase()
        val requestBody = buildJsonObject {
            if (feedbackValue != null) put("feedback", feedbackValue) else put("feedback", JsonNull)
        }.toString()
        val request = api.buildPatchRequest(
            "/agents/$agentId/conversations/$conversationId/messages/$messageId/feedback",
            requestBody
        )
        val body = api.executeRequest(request)
        val wrapper = chatbaseJson.parseToJsonElement(body).jsonObject
        val data = wrapper["data"] ?: throw ChatbaseException("Missing 'data' field in response")
        return chatbaseJson.decodeFromString<Message>(data.toString())
    }

    override fun close() {
        httpClient.dispatcher.executorService.shutdown()
        httpClient.connectionPool.evictAll()
        sseClient.dispatcher.executorService.shutdown()
        sseClient.connectionPool.evictAll()
    }

    private fun createSseFlow(path: String, body: String): Flow<ChatStreamEvent> = callbackFlow {
        val request = api.buildPostRequest(path, body)
        val factory = EventSources.createFactory(sseClient)

        val listener = object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                val event = SseParser.parse(type, data)
                if (event != null) {
                    trySend(event)
                    if (event is ChatStreamEvent.Done || event is ChatStreamEvent.Error) {
                        close()
                    }
                }
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                if (response != null && !response.isSuccessful) {
                    val errorBody = response.body?.string() ?: ""
                    val exception = ApiExecutor.parseApiError(response.code, errorBody)
                    trySend(ChatStreamEvent.Error(exception))
                } else if (t != null) {
                    trySend(ChatStreamEvent.Error(NetworkException("SSE connection failed: ${t.message}", t)))
                }
                close()
            }

            override fun onClosed(eventSource: EventSource) {
                close()
            }
        }

        val eventSource = factory.newEventSource(request, listener)

        awaitClose {
            eventSource.cancel()
        }
    }

    private inline fun <reified T> parsePaginatedResponse(body: String): Page<T> {
        val json = chatbaseJson.parseToJsonElement(body).jsonObject
        val dataArray = json["data"]?.jsonArray ?: throw ChatbaseException("Missing 'data' field in response")
        val pagination = json["pagination"]?.jsonObject ?: throw ChatbaseException("Missing 'pagination' field in response")

        val items = dataArray.map { chatbaseJson.decodeFromJsonElement<T>(it) }
        val cursor = pagination["cursor"]?.let {
            if (it is JsonNull) null else it.jsonPrimitive.content
        }
        val hasMore = pagination["hasMore"]?.jsonPrimitive?.boolean ?: false
        val total = pagination["total"]?.jsonPrimitive?.int ?: 0

        return Page(data = items, cursor = cursor, hasMore = hasMore, total = total)
    }
}
