package com.chatbase.sdk

import com.chatbase.sdk.model.*
import com.chatbase.sdk.streaming.ChatStreamEvent
import kotlinx.coroutines.flow.Flow
import java.io.Closeable

interface ChatbaseClient : Closeable {

    suspend fun health(): HealthResponse

    suspend fun generateResult(
        agentId: String,
        message: String,
        conversationId: String? = null,
        userId: String? = null
    ): ChatResponse

    suspend fun generateText(
        agentId: String,
        message: String,
        conversationId: String? = null,
        userId: String? = null
    ): String

    fun stream(
        agentId: String,
        message: String,
        conversationId: String? = null,
        userId: String? = null
    ): Flow<ChatStreamEvent>

    fun streamText(
        agentId: String,
        message: String,
        conversationId: String? = null,
        userId: String? = null
    ): Flow<String>

    suspend fun retryResult(
        agentId: String,
        conversationId: String,
        messageId: String
    ): ChatResponse

    suspend fun retryText(
        agentId: String,
        conversationId: String,
        messageId: String
    ): String

    fun retryStream(
        agentId: String,
        conversationId: String,
        messageId: String
    ): Flow<ChatStreamEvent>

    fun retryStreamText(
        agentId: String,
        conversationId: String,
        messageId: String
    ): Flow<String>

    suspend fun listConversations(
        agentId: String,
        cursor: String? = null,
        limit: Int? = null
    ): Page<Conversation>

    suspend fun getConversation(
        agentId: String,
        conversationId: String
    ): Conversation

    suspend fun listMessages(
        agentId: String,
        conversationId: String,
        cursor: String? = null,
        limit: Int? = null
    ): Page<Message>

    suspend fun listUserConversations(
        agentId: String,
        userId: String,
        cursor: String? = null,
        limit: Int? = null
    ): Page<Conversation>

    suspend fun updateFeedback(
        agentId: String,
        conversationId: String,
        messageId: String,
        feedback: Feedback?
    ): Message
}
