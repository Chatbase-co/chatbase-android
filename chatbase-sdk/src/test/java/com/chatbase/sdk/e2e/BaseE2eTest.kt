package com.chatbase.sdk.e2e

import com.chatbase.sdk.Chatbase
import com.chatbase.sdk.ChatbaseClient
import kotlinx.coroutines.delay
import org.junit.BeforeClass

abstract class BaseE2eTest {
    companion object {
        val apiKey: String = System.getenv("CHATBASE_API_KEY") ?: ""
        val agentId: String = System.getenv("CHATBASE_AGENT_ID") ?: ""
        val baseUrl: String = System.getenv("CHATBASE_BASE_URL") ?: ""

        @JvmStatic
        @BeforeClass
        fun checkEnv() {
            require(apiKey.isNotBlank()) { "CHATBASE_API_KEY environment variable must be set" }
            require(agentId.isNotBlank()) { "CHATBASE_AGENT_ID environment variable must be set" }
            require(baseUrl.isNotBlank()) { "CHATBASE_BASE_URL environment variable must be set" }
        }

        val client: ChatbaseClient by lazy {
            Chatbase.client {
                this.apiKey = this@Companion.apiKey
                this.baseUrl = this@Companion.baseUrl
            }
        }

        /** Brief delay to allow the API to propagate conversation/message data. */
        suspend fun waitForPropagation() = delay(2000)
    }
}
