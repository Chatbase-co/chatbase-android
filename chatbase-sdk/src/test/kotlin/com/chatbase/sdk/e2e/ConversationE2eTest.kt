package com.chatbase.sdk.e2e

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ConversationE2eTest : BaseE2eTest() {

    @Test
    fun testListConversations() = runBlocking {
        val page = client.listConversations(agentId = agentId, limit = 5)
        assertNotNull(page.data)
        assertTrue("Total should be non-negative", page.total >= 0)
    }

    @Test
    fun testListConversationsGetNextPage() = runBlocking {
        val firstPage = client.listConversations(agentId = agentId, limit = 1)
        assertNotNull(firstPage.data)
        if (firstPage.hasMore && firstPage.getNextPage != null) {
            val secondPage = firstPage.getNextPage!!.invoke()
            assertNotNull("Second page should not be null", secondPage)
            assertNotNull(secondPage!!.data)
        }
    }

    @Test
    fun testGetConversation() = runBlocking {
        val chatResponse = client.generateResult(agentId = agentId, message = "Hello for get conversation test.")
        val conversationId = chatResponse.metadata.conversationId
        waitForPropagation()

        val conversation = client.getConversation(agentId = agentId, conversationId = conversationId)
        assertEquals(conversationId, conversation.id)
        assertNotNull("Messages should be present", conversation.messages)
        assertTrue("Should have at least 2 messages", conversation.messages!!.size >= 2)
    }

    @Test
    fun testListMessages() = runBlocking {
        val chatResponse = client.generateResult(agentId = agentId, message = "Hello for list messages test.")
        val conversationId = chatResponse.metadata.conversationId
        waitForPropagation()

        val page = client.listMessages(agentId = agentId, conversationId = conversationId)
        assertNotNull(page.data)
        assertTrue("Should have messages", page.data.isNotEmpty())
    }

    @Test
    fun testListMessagesGetNextPage() = runBlocking {
        val chatResponse = client.generateResult(agentId = agentId, message = "Hello for list messages getNextPage test.")
        val conversationId = chatResponse.metadata.conversationId
        waitForPropagation()

        val page = client.listMessages(agentId = agentId, conversationId = conversationId, limit = 1)
        assertNotNull(page.data)
        if (page.hasMore && page.getNextPage != null) {
            val nextPage = page.getNextPage!!.invoke()
            assertNotNull("Next page should not be null", nextPage)
            assertNotNull(nextPage!!.data)
        }
    }

    @Test
    fun testListUserConversations() = runBlocking {
        val userId = "sdk-e2e-conv-user"
        client.generateResult(agentId = agentId, message = "Hello for user conversations test.", userId = userId)
        waitForPropagation()

        val page = client.listUserConversations(agentId = agentId, userId = userId)
        assertNotNull(page.data)
        assertTrue("Should have at least one conversation", page.data.isNotEmpty())
    }
}
