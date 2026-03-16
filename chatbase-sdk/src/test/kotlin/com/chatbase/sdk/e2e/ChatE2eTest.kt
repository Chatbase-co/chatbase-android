package com.chatbase.sdk.e2e

import com.chatbase.sdk.model.FinishReason
import com.chatbase.sdk.model.Part
import com.chatbase.sdk.streaming.ChatStreamEvent
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ChatE2eTest : BaseE2eTest() {

    @Test
    fun testNonStreamingChat() = runBlocking {
        val response = client.generateResult(agentId = agentId, message = "Hello, say hi back in one word.")
        assertNotNull(response.id)
        assertNotNull(response.metadata.conversationId)
        assertEquals(FinishReason.STOP, response.metadata.finishReason)
        assertTrue("Should have at least one part", response.parts.isNotEmpty())
        val textPart = response.parts.filterIsInstance<Part.Text>().firstOrNull()
        assertNotNull("Should have a text part", textPart)
        assertTrue("Text should not be empty", textPart!!.text.isNotBlank())
    }

    @Test
    fun testStreamingChat() = runBlocking {
        val events = client.stream(agentId = agentId, message = "Say hello in one word.").toList()
        val textDeltas = events.filterIsInstance<ChatStreamEvent.TextDelta>()
        assertTrue("Should have at least one TextDelta", textDeltas.isNotEmpty())
        val doneEvents = events.filterIsInstance<ChatStreamEvent.Done>()
        assertTrue("Should have a Done event", doneEvents.isNotEmpty())
    }

    @Test
    fun testGenerateText() = runBlocking {
        val text = client.generateText(agentId = agentId, message = "Say hello in one word.")
        assertTrue("Generated text should not be empty", text.isNotBlank())
    }

    @Test
    fun testStreamText() = runBlocking {
        val chunks = client.streamText(agentId = agentId, message = "Say hello in one word.").toList()
        assertTrue("Should have at least one text chunk", chunks.isNotEmpty())
        val fullText = chunks.joinToString("")
        assertTrue("Full text should not be empty", fullText.isNotBlank())
    }

    @Test
    fun testConversationContinuity() = runBlocking {
        val first = client.generateResult(agentId = agentId, message = "My name is TestUser123.")
        val conversationId = first.metadata.conversationId
        waitForPropagation()

        val second = client.generateResult(
            agentId = agentId,
            message = "What is my name?",
            conversationId = conversationId
        )
        assertEquals(conversationId, second.metadata.conversationId)
    }

    @Test
    fun testChatWithUserId() = runBlocking {
        val response = client.generateResult(
            agentId = agentId,
            message = "Hello",
            userId = "sdk-test-user"
        )
        assertEquals("sdk-test-user", response.metadata.userId)
    }
}
