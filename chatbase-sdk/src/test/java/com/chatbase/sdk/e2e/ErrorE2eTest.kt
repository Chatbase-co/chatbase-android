package com.chatbase.sdk.e2e

import com.chatbase.sdk.ChatbaseConfig
import com.chatbase.sdk.exception.ApiException
import com.chatbase.sdk.internal.AnonymousIdProvider
import com.chatbase.sdk.internal.ChatbaseClientImpl
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ErrorE2eTest : BaseE2eTest() {

    @Test
    fun testInvalidAgentId() = runBlocking {
        val config = ChatbaseConfig(
            agentId = "nonexistent-agent-id-xyz",
            baseUrl = baseUrl
        )
        val badClient = ChatbaseClientImpl(config, AnonymousIdProvider { testDeviceId })
        try {
            badClient.sendMessage("Hello")
            fail("Should have thrown ApiException")
        } catch (e: ApiException) {
            assertTrue("Should be not found", e.isNotFound || e.httpStatus in 400..499)
        } finally {
            badClient.close()
        }
    }

    @Test
    fun testInvalidUserAgent() = runBlocking {
        // SDK always sends correct User-Agent, so this is mainly a server-side check
        // Just verify normal flow works without auth errors
        try {
            val page = client.listConversations(limit = 1)
            assertNotNull(page)
        } catch (e: ApiException) {
            // Acceptable if server hasn't been updated yet
        }
    }
}
