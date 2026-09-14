package com.chatbase.sdk.internal

import com.chatbase.sdk.ChatbaseClient
import com.chatbase.sdk.createTestClient
import kotlinx.coroutines.*
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Cancellation of the streaming entry points ([ChatbaseClient.sendMessage] / [ChatbaseClient.retry]).
 * Cancelling the calling coroutine must stop the stream and the tool loop, and [ChatbaseClient.close]
 * must stop an in-flight stream too.
 */
class ChatbaseClientCancellationTest {

    private lateinit var server: MockWebServer
    private lateinit var client: ChatbaseClient
    private val mainThread = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(mainThread)
        server = MockWebServer()
        server.start()
        client = createTestClient(server)
    }

    @After
    fun tearDown() {
        client.close()
        server.shutdown()
        Dispatchers.resetMain()
        mainThread.close()
    }

    // ── Caller cancellation ──────────────────────────────────────────

    @Test
    fun `cancelling the caller stops the stream promptly`() = runBlocking {
        server.enqueue(slowTextStream(deltas = 50))

        val job = launch(Dispatchers.Default) { client.sendMessage("hi") }
        server.takeRequest(5, TimeUnit.SECONDS)
        delay(500)

        val t0 = System.nanoTime()
        job.cancelAndJoin()
        val ms = (System.nanoTime() - t0) / 1_000_000
        assertTrue("cancelAndJoin() took ${ms}ms — the stream kept running after its caller was cancelled", ms < 1_000)
    }

    @Test
    fun `client-side tool does not run after the caller was cancelled`() = runBlocking {
        val events = listOf(START, textStart("t1")) +
            (1..20).map { textDelta("t1", "$it,") } +
            """{"type":"tool-input-available","toolCallId":"tc-1","toolName":"place_order","input":{"sku":"A1"}}""" +
            """{"type":"finish","finishReason":"tool-calls","messageMetadata":{"conversationId":"conv-1"}}"""
        server.enqueue(sseResponse(events).throttleBody(128, 100, TimeUnit.MILLISECONDS))
        server.enqueue(MockResponse().setBody("""{"data":{"success":true}}"""))                 // tool-result
        server.enqueue(sseResponse(listOf("""{"type":"finish","finishReason":"stop"}"""))) // continuation

        val toolRan = AtomicBoolean(false)
        client.tool("place_order") { toolRan.set(true); mapOf("ok" to true) }

        val job = launch(Dispatchers.Default) { client.sendMessage("buy it") }
        server.takeRequest(5, TimeUnit.SECONDS)
        delay(200)
        job.cancel() // e.g. the user leaves the screen and viewModelScope is cancelled

        delay(3_000) // long enough for the throttled stream and tool loop to have completed
        assertFalse("tool handler 'place_order' ran after its caller was cancelled", toolRan.get())
        assertEquals("no follow-up requests after cancellation", 1, server.requestCount)
    }

    // ── close() ──────────────────────────────────────────────────────

    @Test
    fun `close cancels an in-flight stream`() = runBlocking {
        server.enqueue(slowTextStream(deltas = 50))

        val result = async(Dispatchers.Default) { runCatching { client.sendMessage("hi") } }
        server.takeRequest(5, TimeUnit.SECONDS)
        delay(300)

        val t0 = System.nanoTime()
        client.close()
        val outcome = result.await()
        val ms = (System.nanoTime() - t0) / 1_000_000

        assertTrue("sendMessage returned ${ms}ms after close()", ms < 1_000)
        assertTrue("expected CancellationException, got ${outcome.exceptionOrNull()}",
            outcome.exceptionOrNull() is CancellationException)
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private companion object {
        const val START = """{"type":"start"}"""
        fun textStart(id: String) = """{"type":"text-start","id":"$id"}"""
        fun textDelta(id: String, delta: String) = """{"type":"text-delta","id":"$id","delta":"$delta"}"""

        fun sseResponse(events: List<String>): MockResponse {
            val body = buildString {
                events.forEach { append("data: ").append(it).append("\n\n") }
                append("data: [DONE]\n\n")
            }
            return MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody(body)
        }

        /** A text stream that trickles in over several seconds. */
        fun slowTextStream(deltas: Int): MockResponse {
            val events = listOf(START, textStart("t1")) +
                (1..deltas).map { textDelta("t1", "$it,") } +
                """{"type":"finish","finishReason":"stop"}"""
            return sseResponse(events).throttleBody(64, 200, TimeUnit.MILLISECONDS)
        }
    }
}
