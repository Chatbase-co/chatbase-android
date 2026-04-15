package com.chatbase.sdk.internal

import com.chatbase.sdk.ChatbaseClient
import com.chatbase.sdk.ChatbaseConfig
import com.chatbase.sdk.StreamCallbacks
import com.chatbase.sdk.ToolCallInfo
import com.chatbase.sdk.ToolResultInfo
import com.chatbase.sdk.exception.ChatbaseException
import com.chatbase.sdk.exception.NetworkException
import com.chatbase.sdk.model.*
import com.chatbase.sdk.streaming.ChatStreamEvent
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources

internal class ChatbaseClientImpl(
    private val config: ChatbaseConfig,
    anonymousIdProvider: AnonymousIdProvider
) : ChatbaseClient {

    private val identityManager = IdentityManager(anonymousIdProvider)
    private val conversationState = ConversationIdHolder()
    private val toolRegistry = ToolRegistry()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val httpClient: OkHttpClient = HttpClientFactory.create(config, identityManager)
    private val sseClient: OkHttpClient = HttpClientFactory.createForSse(config, identityManager)
    private val api = ApiExecutor(httpClient, config.baseUrl, config.agentId)

    // -- Identity --

    override val deviceId: String get() = identityManager.deviceId
    override val isIdentified: Boolean get() = identityManager.isIdentified
    override val currentUserId: String? get() = identityManager.currentUserId

    override suspend fun identify(token: String) = withContext(Dispatchers.IO) {
        verify(token)
    }

    override fun logout() {
        identityManager.clearIdentity()
        conversationState.clear()
    }

    // -- Conversation state --

    override val currentConversationId: String? get() = conversationState.conversationId

    override fun newConversation() {
        conversationState.clear()
    }

    // -- Chat (DSL) --

    override suspend fun sendMessage(
        message: String,
        conversationId: String?,
        callbacks: StreamCallbacks.() -> Unit
    ): ChatResponse {
        val cb = StreamCallbacks().apply(callbacks)
        val effectiveConversationId = conversationId ?: conversationState.conversationId
        return withContext(scope.coroutineContext) {
            executeWithToolLoop(
                path = "/chat",
                buildRequestBody = { msg, convId ->
                    chatbaseJson.encodeToString(
                        ChatRequest.serializer(),
                        ChatRequest(message = msg, conversationId = convId, stream = true)
                    )
                },
                initialMessage = message,
                conversationId = effectiveConversationId,
                callbacks = cb
            )
        }
    }

    // -- Chat (Flow) --

    override fun sendMessageStream(
        message: String,
        conversationId: String?
    ): Flow<ChatStreamEvent> {
        val effectiveConversationId = conversationId ?: conversationState.conversationId
        return createSseFlow(
            path = "/chat",
            body = chatbaseJson.encodeToString(
                ChatRequest.serializer(),
                ChatRequest(message = message, conversationId = effectiveConversationId, stream = true)
            )
        )
    }

    // -- Retry --

    override suspend fun retry(
        conversationId: String,
        messageId: String,
        callbacks: StreamCallbacks.() -> Unit
    ): ChatResponse {
        val cb = StreamCallbacks().apply(callbacks)
        return withContext(scope.coroutineContext) {
            executeWithToolLoop(
                path = "/conversations/$conversationId/retry",
                buildRequestBody = { _, _ ->
                    chatbaseJson.encodeToString(
                        RetryRequest.serializer(),
                        RetryRequest(messageId = messageId, stream = true)
                    )
                },
                initialMessage = null,
                conversationId = conversationId,
                callbacks = cb
            )
        }
    }

    override fun retryStream(
        conversationId: String,
        messageId: String
    ): Flow<ChatStreamEvent> = createSseFlow(
        path = "/conversations/$conversationId/retry",
        body = chatbaseJson.encodeToString(
            RetryRequest.serializer(),
            RetryRequest(messageId = messageId, stream = true)
        )
    )

    // -- Conversations --

    override suspend fun listConversations(
        cursor: String?,
        limit: Int?
    ): Page<Conversation> = withContext(Dispatchers.IO) {
        val request = api.buildGetRequest(
            "/conversations",
            buildMap {
                cursor?.let { put("cursor", it) }
                limit?.let { put("limit", it.toString()) }
            }
        )
        val body = api.executeRequest(request)
        val page = parsePaginatedResponse<Conversation>(body)
        page.getNextPage = if (page.hasMore && page.cursor != null) {
            { listConversations(cursor = page.cursor, limit = limit) }
        } else null
        page
    }

    // -- Messages --

    override suspend fun listMessages(
        conversationId: String,
        cursor: String?,
        limit: Int?
    ): Page<Message> = withContext(Dispatchers.IO) {
        val request = api.buildGetRequest(
            "/conversations/$conversationId/messages",
            buildMap {
                cursor?.let { put("cursor", it) }
                limit?.let { put("limit", it.toString()) }
            }
        )
        val body = api.executeRequest(request)
        val page = parsePaginatedResponse<Message>(body)
        page.getNextPage = if (page.hasMore && page.cursor != null) {
            { listMessages(conversationId, cursor = page.cursor, limit = limit) }
        } else null
        page
    }

    // -- Verify --

    override suspend fun verify(token: String) = withContext(Dispatchers.IO) {
        val requestBody = chatbaseJson.encodeToString(
            VerifyRequest.serializer(),
            VerifyRequest(token = token)
        )
        val request = api.buildPostRequest("/verify", requestBody)
        val body = api.executeRequest(request)
        val wrapper = chatbaseJson.parseToJsonElement(body).jsonObject
        val data = wrapper["data"]?.jsonObject ?: throw ChatbaseException("Missing 'data' in verify response")
        val userId = data["userId"]?.jsonPrimitive?.content
            ?: throw ChatbaseException("Missing 'userId' in verify response")
        identityManager.setIdentified(token, userId)
    }

    // -- Tools --

    override fun tool(name: String, handler: suspend (input: Map<String, Any?>) -> Any) {
        toolRegistry.register(name, handler)
    }

    override fun removeTool(name: String) {
        toolRegistry.remove(name)
    }

    // -- Lifecycle --

    override fun close() {
        scope.cancel()
        httpClient.dispatcher.executorService.shutdown()
        httpClient.connectionPool.evictAll()
        sseClient.dispatcher.executorService.shutdown()
        sseClient.connectionPool.evictAll()
    }

    // -- Internal: Tool Loop --

    private suspend fun executeWithToolLoop(
        path: String,
        buildRequestBody: (message: String?, conversationId: String?) -> String,
        initialMessage: String?,
        conversationId: String?,
        callbacks: StreamCallbacks
    ): ChatResponse {
        var currentMessage: String? = initialMessage
        var currentConversationId: String? = conversationId
        var isFirstRequest = true

        for (iteration in 1..MAX_TOOL_LOOP_ITERATIONS) {
            val requestPath: String
            val requestBody: String

            if (isFirstRequest) {
                requestPath = path
                requestBody = buildRequestBody(currentMessage, currentConversationId)
                isFirstRequest = false
            } else {
                // Continuation after tool results — always POST /chat with no message
                requestPath = "/chat"
                requestBody = chatbaseJson.encodeToString(
                    ChatRequest.serializer(),
                    ChatRequest(conversationId = currentConversationId, stream = true)
                )
            }

            val result = streamAndCollect(requestPath, requestBody, callbacks)

            // Update conversation state from response
            result.metadata.conversationId?.let {
                currentConversationId = it
                conversationState.conversationId = it
            }

            if (result.metadata.finishReason == FinishReason.TOOL_CALLS) {
                val toolCallParts = result.parts.filterIsInstance<Part.ToolCall>()
                for (toolCall in toolCallParts) {
                    val handler = toolRegistry.get(toolCall.toolName)
                    if (handler == null) {
                        val error = ChatbaseException("No handler registered for tool '${toolCall.toolName}'")
                        invokeOnMain(callbacks.onError, error)
                        throw error
                    }

                    try {
                        val input = toolCall.input?.let { jsonElementToMap(it) } ?: emptyMap()
                        val output = handler(input)
                        val outputJson = anyToJsonElement(output)

                        // Submit tool result
                        val toolResultBody = chatbaseJson.encodeToString(
                            ToolResultRequest.serializer(),
                            ToolResultRequest(toolCallId = toolCall.toolCallId, output = outputJson)
                        )
                        val submitRequest = api.buildPostRequest(
                            "/conversations/$currentConversationId/tool-result",
                            toolResultBody
                        )
                        api.executeRequest(submitRequest)

                        invokeOnMain(callbacks.onToolResult,
                            ToolResultInfo(toolCall.toolCallId, toolCall.toolName, output)
                        )
                    } catch (e: ChatbaseException) {
                        invokeOnMain(callbacks.onError, e)
                        throw e
                    } catch (e: Exception) {
                        val wrapped = ChatbaseException("Tool '${toolCall.toolName}' failed: ${e.message}", e)
                        invokeOnMain(callbacks.onError, wrapped)
                        throw wrapped
                    }
                }

                currentMessage = null
            } else {
                // Terminal — return response
                invokeOnMain(callbacks.onFinish, result)
                return result
            }
        }

        throw ChatbaseException("Tool loop exceeded maximum iterations ($MAX_TOOL_LOOP_ITERATIONS)")
    }

    /** Invoke a callback on [Dispatchers.Main] so consumers can safely update UI state. */
    private suspend inline fun <T> invokeOnMain(noinline callback: ((T) -> Unit)?, value: T) {
        if (callback != null) {
            withContext(Dispatchers.Main) { callback(value) }
        }
    }

    companion object {
        private const val MAX_TOOL_LOOP_ITERATIONS = 10
    }

    // -- Internal: Stream & Collect --

    private suspend fun streamAndCollect(
        path: String,
        body: String,
        callbacks: StreamCallbacks
    ): ChatResponse {
        val textParts = mutableListOf<StringBuilder>()
        var currentTextId: String? = null
        val toolCalls = mutableListOf<Part.ToolCall>()
        var finishReason = FinishReason.STOP
        var metadata: ResponseMetadata? = null

        val flow = createSseFlow(path, body)
        flow.collect { event ->
            when (event) {
                is ChatStreamEvent.TextStart -> {
                    currentTextId = event.id
                    textParts.add(StringBuilder())
                }
                is ChatStreamEvent.TextDelta -> {
                    if (textParts.isEmpty()) textParts.add(StringBuilder())
                    textParts.last().append(event.delta)
                    invokeOnMain(callbacks.onTextDelta, event.delta)
                }
                is ChatStreamEvent.TextEnd -> {
                    currentTextId = null
                }
                is ChatStreamEvent.ToolInputAvailable -> {
                    toolCalls.add(Part.ToolCall(
                        toolCallId = event.toolCallId,
                        toolName = event.toolName,
                        input = event.input
                    ))
                    invokeOnMain(callbacks.onToolCall,
                        ToolCallInfo(event.toolCallId, event.toolName, event.input)
                    )
                }
                is ChatStreamEvent.Finish -> {
                    finishReason = FinishReason.entries.firstOrNull {
                        it.name.equals(event.finishReason.replace("-", "_"), ignoreCase = true)
                    } ?: FinishReason.UNKNOWN

                    event.messageMetadata?.let { meta ->
                        metadata = ResponseMetadata(
                            messageId = meta.messageId,
                            userMessageId = meta.userMessageId,
                            conversationId = meta.conversationId,
                            finishReason = finishReason,
                            usage = meta.usage?.let { Usage(it.credits) }
                        )
                    }
                }
                is ChatStreamEvent.MessageMetadataEvent -> {
                    val meta = event.messageMetadata
                    metadata = ResponseMetadata(
                        messageId = meta.messageId,
                        userMessageId = meta.userMessageId,
                        conversationId = meta.conversationId,
                        finishReason = finishReason,
                        usage = meta.usage?.let { Usage(it.credits) }
                    )
                }
                is ChatStreamEvent.Error -> {
                    invokeOnMain(callbacks.onError, event.exception)
                    throw event.exception
                }
                is ChatStreamEvent.Start -> {
                    if (callbacks.onStart != null) {
                        withContext(Dispatchers.Main) { callbacks.onStart?.invoke() }
                    }
                }
                is ChatStreamEvent.ToolOutputAvailable -> {
                    val toolName = toolCalls.firstOrNull { it.toolCallId == event.toolCallId }?.toolName ?: ""
                    invokeOnMain(callbacks.onToolResult,
                        ToolResultInfo(event.toolCallId, toolName, event.output)
                    )
                }
                else -> { /* StepStart, StepFinish, ToolInputStart, ToolInputDelta */ }
            }
        }

        val parts = mutableListOf<Part>()
        textParts.forEach { sb ->
            if (sb.isNotEmpty()) parts.add(Part.Text(text = sb.toString()))
        }
        parts.addAll(toolCalls)

        val finalMetadata = metadata ?: ResponseMetadata(finishReason = finishReason)

        return ChatResponse(
            id = finalMetadata.messageId ?: "",
            role = "assistant",
            parts = parts,
            metadata = finalMetadata
        )
    }

    // -- Internal: SSE Flow --

    private fun createSseFlow(path: String, body: String): Flow<ChatStreamEvent> = callbackFlow {
        val request = api.buildPostRequest(path, body)
        val factory = EventSources.createFactory(sseClient)

        val listener = object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                val event = SseParser.parse(type, data)
                if (event != null) {
                    trySend(event)
                    if (event is ChatStreamEvent.Error) {
                        close()
                    }
                } else if (data == "[DONE]") {
                    close()
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

    // -- Internal: Pagination --

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

    // -- Internal: JSON Helpers --

    private fun jsonElementToMap(element: JsonElement): Map<String, Any?> {
        if (element !is JsonObject) return emptyMap()
        return element.entries.associate { (k, v) -> k to jsonElementToAny(v) }
    }

    private fun jsonElementToAny(element: JsonElement): Any? = when (element) {
        is JsonNull -> null
        is JsonPrimitive -> when {
            element.isString -> element.content
            element.booleanOrNull != null -> element.boolean
            element.longOrNull != null -> element.long
            element.doubleOrNull != null -> element.double
            else -> element.content
        }
        is JsonArray -> element.map { jsonElementToAny(it) }
        is JsonObject -> element.entries.associate { (k, v) -> k to jsonElementToAny(v) }
    }

    private fun anyToJsonElement(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is String -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Map<*, *> -> JsonObject(value.entries.associate { (k, v) -> k.toString() to anyToJsonElement(v) })
        is List<*> -> JsonArray(value.map { anyToJsonElement(it) })
        is JsonElement -> value
        else -> JsonPrimitive(value.toString())
    }
}
