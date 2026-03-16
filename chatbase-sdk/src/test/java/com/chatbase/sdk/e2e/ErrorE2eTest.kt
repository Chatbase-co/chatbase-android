package com.chatbase.sdk.e2e

import com.chatbase.sdk.Chatbase
import com.chatbase.sdk.exception.ApiException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ErrorE2eTest : BaseE2eTest() {

    @Test
    fun testInvalidApiKey() = runBlocking {
        val badClient = Chatbase.client {
            this.apiKey = "invalid-api-key-12345"
            this.baseUrl = BaseE2eTest.baseUrl
        }
        try {
            badClient.generateResult(agentId = agentId, message = "Hello")
            fail("Should have thrown ApiException")
        } catch (e: ApiException) {
            assertTrue("Should be auth error", e.isAuthError || e.httpStatus == 403)
        } finally {
            badClient.close()
        }
    }

    @Test
    fun testAgentNotFound() = runBlocking {
        try {
            client.generateResult(agentId = "nonexistent-agent-id-xyz", message = "Hello")
            fail("Should have thrown ApiException")
        } catch (e: ApiException) {
            assertTrue("Should be not found", e.isNotFound)
        }
    }

    @Test
    fun testInvalidConversationId() = runBlocking {
        try {
            client.getConversation(agentId = agentId, conversationId = "nonexistent-conv-id-xyz")
            fail("Should have thrown ApiException")
        } catch (e: ApiException) {
            assertTrue("Should be not found", e.isNotFound)
        }
    }
}
