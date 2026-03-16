package com.chatbase.sdk.e2e

import com.chatbase.sdk.model.Feedback
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class FeedbackE2eTest : BaseE2eTest() {

    @Test
    fun testSetPositiveFeedback() = runBlocking {
        val chatResponse = client.generateResult(agentId = agentId, message = "Hello for positive feedback test.")
        val conversationId = chatResponse.metadata.conversationId
        val messageId = chatResponse.id
        waitForPropagation()

        val updated = client.updateFeedback(
            agentId = agentId,
            conversationId = conversationId,
            messageId = messageId,
            feedback = Feedback.POSITIVE
        )
        assertEquals(Feedback.POSITIVE, updated.feedback)
    }

    @Test
    fun testSetNegativeFeedback() = runBlocking {
        val chatResponse = client.generateResult(agentId = agentId, message = "Hello for negative feedback test.")
        val conversationId = chatResponse.metadata.conversationId
        val messageId = chatResponse.id
        waitForPropagation()

        val updated = client.updateFeedback(
            agentId = agentId,
            conversationId = conversationId,
            messageId = messageId,
            feedback = Feedback.NEGATIVE
        )
        assertEquals(Feedback.NEGATIVE, updated.feedback)
    }

    @Test
    fun testClearFeedback() = runBlocking {
        val chatResponse = client.generateResult(agentId = agentId, message = "Hello for clear feedback test.")
        val conversationId = chatResponse.metadata.conversationId
        val messageId = chatResponse.id
        waitForPropagation()

        client.updateFeedback(
            agentId = agentId,
            conversationId = conversationId,
            messageId = messageId,
            feedback = Feedback.POSITIVE
        )

        val cleared = client.updateFeedback(
            agentId = agentId,
            conversationId = conversationId,
            messageId = messageId,
            feedback = null
        )
        assertNull(cleared.feedback)
    }
}
