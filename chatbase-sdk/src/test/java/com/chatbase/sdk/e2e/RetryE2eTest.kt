package com.chatbase.sdk.e2e

import com.chatbase.sdk.streaming.ChatStreamEvent
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class RetryE2eTest : BaseE2eTest() {

    @Test
    fun testRetryWithCallbacks() = runBlocking {
        val chatResponse = client.sendMessage("Hello for retry test.")
        val conversationId = chatResponse.metadata.conversationId!!
        val messageId = chatResponse.id
        waitForPropagation()

        var receivedText = ""
        val retryResponse = client.retry(conversationId, messageId) {
            onTextDelta { text -> receivedText += text }
        }
        assertNotNull(retryResponse.id)
        assertTrue("Should have at least one part", retryResponse.parts.isNotEmpty())
    }

    @Test
    fun testRetryStream() = runBlocking {
        val chatResponse = client.sendMessage("Hello for streaming retry test.")
        val conversationId = chatResponse.metadata.conversationId!!
        val messageId = chatResponse.id
        waitForPropagation()

        val events = client.retryStream(conversationId, messageId).toList()
        val textDeltas = events.filterIsInstance<ChatStreamEvent.TextDelta>()
        assertTrue("Should have at least one TextDelta", textDeltas.isNotEmpty())
    }
}
