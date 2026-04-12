package com.chatbase.sdk.e2e

import com.chatbase.sdk.ChatbaseClient
import com.chatbase.sdk.ChatbaseConfig
import com.chatbase.sdk.internal.AnonymousIdProvider
import com.chatbase.sdk.internal.ChatbaseClientImpl
import kotlinx.coroutines.delay
import org.junit.BeforeClass

abstract class BaseE2eTest {
    companion object {
        // E2E tests require these to be set in the test code or via test properties
        val agentId: String = "YOUR_AGENT_ID"
        val baseUrl: String = "https://www.chatbase.co/api/sdk/agents"
        val testDeviceId: String = "e2e-test-device-${System.currentTimeMillis()}"

        @JvmStatic
        @BeforeClass
        fun checkEnv() {
            require(agentId != "YOUR_AGENT_ID") { "Set agentId in BaseE2eTest before running E2E tests" }
        }

        val client: ChatbaseClient by lazy {
            val config = ChatbaseConfig(
                agentId = agentId,
                baseUrl = baseUrl
            )
            ChatbaseClientImpl(config, AnonymousIdProvider { testDeviceId })
        }

        suspend fun waitForPropagation() = delay(2000)
    }
}
