package com.chatbase.sdk.e2e

import com.chatbase.sdk.Chatbase
import com.chatbase.sdk.ChatbaseClient
import kotlinx.coroutines.delay
import org.junit.Assume
import org.junit.BeforeClass

abstract class BaseE2eTest {
    companion object {
        val apiKey: String = System.getenv("CHATBASE_API_KEY") ?: ""
        val agentId: String = System.getenv("CHATBASE_AGENT_ID") ?: ""

        @JvmStatic
        @BeforeClass
        fun checkEnv() {
            Assume.assumeTrue("CHATBASE_API_KEY not set, skipping E2E", apiKey.isNotBlank())
            Assume.assumeTrue("CHATBASE_AGENT_ID not set, skipping E2E", agentId.isNotBlank())
        }

        val client: ChatbaseClient by lazy { Chatbase.client(apiKey) }

        /** Brief delay to allow the API to propagate conversation/message data. */
        suspend fun waitForPropagation() = delay(2000)
    }
}
