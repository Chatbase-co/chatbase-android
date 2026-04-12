package com.chatbase.sdk

import com.chatbase.sdk.model.*
import com.chatbase.sdk.streaming.ChatStreamEvent
import kotlinx.coroutines.flow.Flow
import java.io.Closeable

interface ChatbaseClient : Closeable {

    // Identity
    val deviceId: String
    val isIdentified: Boolean
    val currentUserId: String?
    suspend fun identify(token: String)
    fun logout()

    // Conversation state
    val currentConversationId: String?
    fun newConversation()

    // Chat - DSL style (handles tool loop automatically)
    suspend fun sendMessage(
        message: String,
        conversationId: String? = null,
        callbacks: StreamCallbacks.() -> Unit = {}
    ): ChatResponse

    // Chat - Flow style (raw events, no tool loop)
    fun sendMessageStream(
        message: String,
        conversationId: String? = null
    ): Flow<ChatStreamEvent>

    // Retry
    suspend fun retry(
        conversationId: String,
        messageId: String,
        callbacks: StreamCallbacks.() -> Unit = {}
    ): ChatResponse

    fun retryStream(
        conversationId: String,
        messageId: String
    ): Flow<ChatStreamEvent>

    // Conversations
    suspend fun listConversations(
        cursor: String? = null,
        limit: Int? = null
    ): Page<Conversation>

    // Messages
    suspend fun listMessages(
        conversationId: String,
        cursor: String? = null,
        limit: Int? = null
    ): Page<Message>

    // Verify
    suspend fun verify(token: String)

    // Client-side tools
    fun tool(name: String, handler: suspend (input: Map<String, Any?>) -> Any)
    fun removeTool(name: String)

    // Lifecycle
    override fun close()
}
