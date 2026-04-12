package com.chatbase.sdk.e2e

import com.chatbase.sdk.model.Part
import com.chatbase.sdk.streaming.ChatStreamEvent
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ChatE2eTest : BaseE2eTest() {

    @Test
    fun testSendMessage() = runBlocking {
        var receivedText = ""
        val response = client.sendMessage("Hello, say hi back in one word.") {
            onTextDelta { text -> receivedText += text }
        }
        assertNotNull(response.id)
        assertNotNull(response.metadata.conversationId)
        assertTrue("Should have at least one text part", response.parts.filterIsInstance<Part.Text>().isNotEmpty())
        assertTrue("Should have received streaming text", receivedText.isNotBlank())
    }

    @Test
    fun testSendMessageStream() = runBlocking {
        val events = client.sendMessageStream("Say hello in one word.").toList()
        val textDeltas = events.filterIsInstance<ChatStreamEvent.TextDelta>()
        assertTrue("Should have at least one TextDelta", textDeltas.isNotEmpty())
        val finishEvents = events.filterIsInstance<ChatStreamEvent.Finish>()
        assertTrue("Should have a Finish event", finishEvents.isNotEmpty())
    }

    @Test
    fun testConversationContinuity() = runBlocking {
        val first = client.sendMessage("My name is TestUser123.")
        val conversationId = first.metadata.conversationId
        waitForPropagation()

        val second = client.sendMessage(
            message = "What is my name?",
            conversationId = conversationId
        )
        assertEquals(conversationId, second.metadata.conversationId)
    }

    @Test
    fun testConversationStateTracking() = runBlocking {
        client.newConversation()
        assertNull(client.currentConversationId)

        client.sendMessage("Hello")
        assertNotNull("conversationId should be tracked after sendMessage", client.currentConversationId)
    }
}
