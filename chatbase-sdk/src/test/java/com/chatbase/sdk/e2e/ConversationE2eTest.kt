package com.chatbase.sdk.e2e

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ConversationE2eTest : BaseE2eTest() {

    @Test
    fun testListConversations() = runBlocking {
        val page = client.listConversations(limit = 5)
        assertNotNull(page.data)
        assertTrue("Total should be non-negative", page.total >= 0)
    }

    @Test
    fun testListConversationsGetNextPage() = runBlocking {
        val firstPage = client.listConversations(limit = 1)
        if (firstPage.hasMore && firstPage.getNextPage != null) {
            val secondPage = firstPage.getNextPage!!.invoke()
            assertNotNull("Second page should not be null", secondPage)
            assertNotNull(secondPage!!.data)
        }
    }

    @Test
    fun testListMessages() = runBlocking {
        val chatResponse = client.sendMessage("Hello for list messages test.")
        val conversationId = chatResponse.metadata.conversationId!!
        waitForPropagation()

        val page = client.listMessages(conversationId)
        assertNotNull(page.data)
        assertTrue("Should have messages", page.data.isNotEmpty())
    }

    @Test
    fun testListMessagesGetNextPage() = runBlocking {
        val chatResponse = client.sendMessage("Hello for list messages getNextPage test.")
        val conversationId = chatResponse.metadata.conversationId!!
        waitForPropagation()

        val page = client.listMessages(conversationId, limit = 1)
        if (page.hasMore && page.getNextPage != null) {
            val nextPage = page.getNextPage!!.invoke()
            assertNotNull("Next page should not be null", nextPage)
        }
    }
}
