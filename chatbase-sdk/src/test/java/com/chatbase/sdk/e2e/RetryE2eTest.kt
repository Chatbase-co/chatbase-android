package com.chatbase.sdk.e2e

import com.chatbase.sdk.streaming.ChatStreamEvent
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class RetryE2eTest : BaseE2eTest() {

    @Test
    fun testRetryNonStreaming() = runBlocking {
        val chatResponse = client.generateResult(agentId = agentId, message = "Hello for retry test.")
        val conversationId = chatResponse.metadata.conversationId
        val messageId = chatResponse.id
        waitForPropagation()

        val retryResponse = client.retryResult(
            agentId = agentId,
            conversationId = conversationId,
            messageId = messageId
        )
        assertNotNull(retryResponse.id)
        assertTrue("Should have at least one part", retryResponse.parts.isNotEmpty())
    }

    @Test
    fun testRetryStreaming() = runBlocking {
        val chatResponse = client.generateResult(agentId = agentId, message = "Hello for streaming retry test.")
        val conversationId = chatResponse.metadata.conversationId
        val messageId = chatResponse.id
        waitForPropagation()

        val events = client.retryStream(
            agentId = agentId,
            conversationId = conversationId,
            messageId = messageId
        ).toList()

        val textDeltas = events.filterIsInstance<ChatStreamEvent.TextDelta>()
        assertTrue("Should have at least one TextDelta", textDeltas.isNotEmpty())
    }
}
