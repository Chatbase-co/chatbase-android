package com.chatbase.sdk.e2e

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class HealthE2eTest : BaseE2eTest() {

    @Test
    fun testHealthEndpoint() = runBlocking {
        val health = client.health()
        assertEquals("ok", health.status)
        assertTrue("Timestamp should be positive", health.timestamp > 0)
    }
}
