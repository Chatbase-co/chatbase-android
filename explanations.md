# Chatbase Kotlin SDK — Codebase Walkthrough

## What Is This SDK?

This is a **Kotlin client library** for the [Chatbase API](https://www.chatbase.co/api/v2). It lets you programmatically chat with Chatbase AI agents, manage conversations, and handle streaming responses. It uses:

- **OkHttp** for HTTP requests
- **OkHttp SSE** for Server-Sent Events (streaming)
- **Kotlin Coroutines + Flow** for async operations
- **Kotlinx Serialization** for JSON

---

## Project Structure

```
sdk/
├── chatbase-sdk/src/main/java/com/chatbase/sdk/
│   ├── Chatbase.kt                  ← Entry point (factory)
│   ├── ChatbaseClient.kt            ← Public interface (all methods you call)
│   ├── ChatbaseConfig.kt            ← Configuration (API key, timeouts)
│   ├── model/
│   │   ├── ChatResponse.kt          ← Response from chat/retry
│   │   ├── Part.kt                  ← Text, ToolCall, ToolResult (sealed interface)
│   │   ├── Message.kt               ← Conversation message
│   │   ├── Conversation.kt          ← Conversation object
│   │   ├── Page.kt                  ← Paginated list wrapper
│   │   ├── Role.kt                  ← USER / ASSISTANT enum
│   │   ├── Feedback.kt              ← POSITIVE / NEGATIVE enum
│   │   ├── FinishReason.kt          ← STOP / ERROR enum
│   │   └── HealthResponse.kt        ← Health check result
│   ├── streaming/
│   │   └── ChatStreamEvent.kt       ← 4 event types: TextDelta, Metadata, Done, Error
│   ├── extension/                     ← (removed — replaced by built-in methods)
│   ├── exception/
│   │   ├── ChatbaseException.kt     ← Base exception
│   │   ├── ApiException.kt          ← HTTP/API errors (status code, error code)
│   │   └── NetworkException.kt      ← Connection failures
│   └── internal/                     ← You don't touch these as a consumer
│       ├── ChatbaseClientImpl.kt     ← Implements ChatbaseClient
│       ├── ApiExecutor.kt           ← Builds & executes HTTP requests
│       ├── HttpClientFactory.kt     ← Creates OkHttp clients
│       ├── AuthInterceptor.kt       ← Adds "Authorization: Bearer <key>" header
│       ├── SseParser.kt             ← Parses SSE events into ChatStreamEvent
│       ├── JsonConfig.kt            ← Kotlinx JSON config
│       ├── RequestModels.kt         ← Internal request DTOs
│       └── CallAwait.kt             ← Bridges OkHttp callbacks to coroutines
```

---

## How to Use the SDK (Consumer Examples)

### 1. Create a Client

```kotlin
import com.chatbase.sdk.Chatbase

// Simple — just an API key
val client = Chatbase.client(apiKey = "your-api-key")

// Custom config
val client = Chatbase.client {
    apiKey = "your-api-key"
    connectTimeoutMs = 15_000
    readTimeoutMs = 60_000
}
```

Behind the scenes: `Chatbase.client()` builds a `ChatbaseConfig`, then creates a `ChatbaseClientImpl` which sets up two OkHttp clients — one for regular requests (30s timeout) and one for SSE streaming (5 min timeout).

### 2. Non-Streaming Chat (Simple Request/Response)

```kotlin
import kotlinx.coroutines.runBlocking

runBlocking {
    // Full response with metadata
    val response = client.generateResult(
        agentId = "your-agent-id",
        message = "Hello, what can you help me with?"
    )

    // response.id            → message ID
    // response.role          → "assistant"
    // response.parts         → list of Part (Text, ToolCall, ToolResult)
    // response.metadata      → conversationId, finishReason, usage, etc.

    val text = response.parts.filterIsInstance<Part.Text>().first().text
    println(text)

    // Or just get the text directly
    val text2 = client.generateText(agentId = "your-agent-id", message = "Hello!")
    println(text2)
}
```

Behind the scenes, `generateResult()` sends `stream: false` in the request body. For streaming, `stream()` sends `stream: true`. The SDK sets this automatically based on which method you call.

### 3. Streaming Chat (the main focus)

```kotlin
import com.chatbase.sdk.streaming.ChatStreamEvent
import kotlinx.coroutines.runBlocking

runBlocking {
    val stream = client.stream(
        agentId = "your-agent-id",
        message = "Tell me a story"
    )

    // Option A: Handle each event type manually
    stream.collect { event ->
        when (event) {
            is ChatStreamEvent.TextDelta -> print(event.text)  // incremental text
            is ChatStreamEvent.Metadata  -> { /* response metadata arrived */ }
            is ChatStreamEvent.Done      -> println("\n--- Done! ---")
            is ChatStreamEvent.Error     -> println("Error: ${event.exception.message}")
        }
    }
}
```

### 4. Streaming Text Only

```kotlin
runBlocking {
    // Get only text chunks as a Flow<String> — no event wrappers
    client.streamText(agentId = "your-agent-id", message = "Hello").collect { chunk ->
        print(chunk)  // prints each piece as it arrives
    }
}
```

### 5. Continue a Conversation

```kotlin
runBlocking {
    // First message — creates a new conversation
    val first = client.generateResult(agentId = agentId, message = "My name is Alex")
    val conversationId = first.metadata.conversationId

    // Second message — continues the same conversation
    val second = client.generateResult(
        agentId = agentId,
        message = "What's my name?",
        conversationId = conversationId   // pass the conversation ID
    )
    // The agent remembers context from the first message
}
```

### 6. Don't Forget to Close

```kotlin
client.close()  // shuts down HTTP clients and connection pools
```

---

## Full Data Flow: Streaming Request/Response

Here's exactly what happens when you call `client.stream(agentId, "Hello")`:

```
YOUR CODE                              SDK INTERNALS                         CHATBASE API
─────────                              ─────────────                         ────────────

1. client.stream(agentId, msg)
         │
         ▼
2. ChatbaseClientImpl.stream()
   - Serializes request body:
     {"message":"Hello","stream":true}
         │
         ▼
3. createSseFlow() — callbackFlow { }
   - ApiExecutor.buildPostRequest()     → POST /agents/{id}/chat
     builds an OkHttp Request               Content-Type: application/json
   - AuthInterceptor adds header:           Authorization: Bearer <api-key>
     "Authorization: Bearer <key>"          Body: {"message":"Hello","stream":true}
         │
         ▼
4. EventSources.createFactory(sseClient)
   .newEventSource(request, listener)   ──────────────────────────────────→  HTTP POST
                                                                             (SSE connection opens)
         │
         │                              ←── SSE event: text-delta ──────────
         ▼                                  data: {"delta":"Once"}
5. EventSourceListener.onEvent()
   - SseParser.parse("text-delta", data)
   - Returns ChatStreamEvent.TextDelta("Once")
   - trySend(event) → emits to Flow
         │
         │                              ←── SSE event: text-delta ──────────
         ▼                                  data: {"delta":" upon"}
6. Same as step 5, emits TextDelta(" upon")
         │
         │                              ... more text-delta events ...
         │
         │                              ←── SSE event: finish ─────────────
         ▼                                  data: {"finishReason":"stop",
7. SseParser.parse("finish", data)              "messageMetadata":{...}}
   - Builds ChatResponse with metadata
   - Returns ChatStreamEvent.Done(response)
   - trySend(event) → emits to Flow
   - close() → ends the callbackFlow
         │
         ▼
8. YOUR CODE receives events via .collect { }
   - TextDelta("Once")
   - TextDelta(" upon")
   - TextDelta(" a")
   - TextDelta(" time")
   - Done(ChatResponse(...))
```

### Key Components in the Flow

| Step | File | What Happens |
|------|------|-------------|
| 1-2 | `ChatbaseClientImpl.kt:52-63` | Serializes request with `stream: true` |
| 3 | `ChatbaseClientImpl.kt:181-217` | Creates a `callbackFlow` that bridges OkHttp SSE callbacks to Kotlin Flow |
| 4 | `HttpClientFactory.kt:17-23` | Uses the SSE OkHttpClient (5 min read timeout) |
| 4 | `AuthInterceptor.kt` | Injects `Authorization: Bearer <key>` header |
| 5-7 | `SseParser.kt:10-28` | Parses raw SSE `type` + `data` into typed `ChatStreamEvent` |
| 8 | Your code | You `.collect {}` the Flow and handle each event |

### SSE Event Types from the Server

| Server Event Type | Parsed To | Contains |
|---|---|---|
| `text-delta` | `ChatStreamEvent.TextDelta` | Incremental text chunk |
| `finish` | `ChatStreamEvent.Done` | Full `ChatResponse` with metadata (messageId, conversationId, usage) |
| `error` | `ChatStreamEvent.Error` | Error code + message wrapped in `ChatbaseException` |
| `[DONE]` sentinel | `null` (ignored) | Signals end of stream |

### Error Handling During Streaming

Three failure paths, all emit `ChatStreamEvent.Error`:

1. **HTTP error** (e.g., 401, 429) — `onFailure()` parses the response body into `ApiException`
2. **Network failure** (timeout, DNS) — `onFailure()` wraps in `NetworkException`
3. **Malformed SSE data** — `SseParser.parse()` catches parse exceptions

```kotlin
stream.collect { event ->
    when (event) {
        is ChatStreamEvent.Error -> {
            when (val ex = event.exception) {
                is ApiException -> println("API error ${ex.httpStatus}: ${ex.message}")
                is NetworkException -> println("Network error: ${ex.message}")
                else -> println("Error: ${ex.message}")
            }
        }
        // ...
    }
}
```

---

## Key Kotlin Concepts Used

If you're new to Kotlin, here are the main language features this SDK uses:

| Concept | Where | What It Does |
|---------|-------|-------------|
| `suspend fun` | Most methods in `ChatbaseClient` | Marks functions as async — must be called from a coroutine |
| `Flow<T>` | `stream()`, `retryStream()` | A cold async stream — values are emitted over time |
| `callbackFlow` | `createSseFlow()` | Bridges callback-based APIs (OkHttp SSE) into Flow |
| `sealed interface` | `ChatStreamEvent`, `Part` | A closed set of subtypes — like an enum but each can hold different data |
| `data class` | All models | Auto-generates equals/hashCode/toString/copy |
| `object` | `Chatbase`, `SseParser` | Singleton — one instance, called directly like `Chatbase.client()` |
| `runBlocking` | Tests | Runs a coroutine, blocking the current thread until done |
| `filterIsInstance<T>()` | Extension functions | Filters a Flow/List to only items of type T |

---

## Other API Methods (Quick Reference)

```kotlin
// Health check
val health = client.health()  // HealthResponse(status, timestamp)

// List conversations (paginated)
val page = client.listConversations(agentId, limit = 10)
// page.data → List<Conversation>
// page.cursor → String? (pass to next call for next page)
// page.hasMore → Boolean

// Get a single conversation
val convo = client.getConversation(agentId, conversationId)

// List messages in a conversation
val messages = client.listMessages(agentId, conversationId)

// Retry a failed message
val retried = client.retryResult(agentId, conversationId, messageId)

// Retry with streaming
val retryStream = client.retryStream(agentId, conversationId, messageId)

// Just get text
val text = client.generateText(agentId, "Hello")
val retryText = client.retryText(agentId, conversationId, messageId)

// Give feedback on a message
client.updateFeedback(agentId, conversationId, messageId, Feedback.POSITIVE)
```

---

## Deep Dive: Error Handling

### Exception Hierarchy

```
RuntimeException
  └── ChatbaseException            ← Base (catch-all for any SDK error)
        ├── ApiException            ← HTTP/API errors (server returned an error)
        └── NetworkException        ← Connection failures (no response at all)
```

### ApiException — Server Returned an Error

Defined in `exception/ApiException.kt`:

```kotlin
class ApiException(
    val httpStatus: Int,           // e.g., 401, 404, 429
    val errorCode: String,         // e.g., "UNAUTHORIZED", "LIMIT_EXCEEDED"
    val errorMessage: String,      // human-readable message
    val details: Map<String, String>? = null
)

// Built-in convenience checks:
exception.isAuthError        // httpStatus == 401
exception.isNotFound         // httpStatus == 404
exception.isRateLimited      // httpStatus == 429
exception.isCreditsExhausted // httpStatus == 402
```

### Non-Streaming vs Streaming Error Handling

**Non-streaming** — errors throw exceptions:
```kotlin
try {
    val response = client.generateResult(agentId, "Hello")
} catch (e: ApiException) {
    when {
        e.isAuthError        -> println("Bad API key!")
        e.isRateLimited      -> println("Too many requests")
        e.isCreditsExhausted -> println("No credits left")
        e.isNotFound         -> println("Agent not found")
    }
} catch (e: NetworkException) {
    println("Connection failed: ${e.message}")
}
```

**Streaming** — errors arrive as events (no exceptions thrown):
```kotlin
client.stream(agentId, "Hello").collect { event ->
    when (event) {
        is ChatStreamEvent.Error -> {
            when (val ex = event.exception) {
                is ApiException    -> println("API: ${ex.errorCode}")
                is NetworkException -> println("Network: ${ex.message}")
            }
        }
        // ...
    }
}
```

---

## Deep Dive: Pagination

### The Page Model (`model/Page.kt`)

Every list method returns a `Page<T>` with a built-in `getNextPage` convenience:

```kotlin
data class Page<T>(
    val data: List<T>,          // items on this page
    val cursor: String? = null,  // pass to next call for next page
    val hasMore: Boolean,        // true if more pages exist
    val total: Int               // total count across all pages
) {
    var getNextPage: (suspend () -> Page<T>?)? = null   // set by the SDK
}
```

### Using `getNextPage()`

The SDK attaches a `getNextPage` lambda to each page it returns. Call it when you're ready for the next page — the SDK does not auto-paginate.

```kotlin
// Fetch the first page
val page = client.listConversations(agentId, limit = 10)
page.data.forEach { println("${it.id}: ${it.title}") }

// When ready for more:
val nextPage = page.getNextPage?.invoke()
nextPage?.data?.forEach { println("${it.id}: ${it.title}") }
```

### Looping Through All Pages

```kotlin
var current: Page<Conversation>? = client.listConversations(agentId, limit = 10)
while (current != null) {
    current.data.forEach { println("${it.id}: ${it.title}") }
    current = current.getNextPage?.invoke()
}
```

`getNextPage` is `null` when there are no more pages (i.e., `hasMore` is `false` or `cursor` is `null`).

---

## Deep Dive: Tool Calls

### The Part Sealed Interface (`model/Part.kt`)

```kotlin
sealed interface Part {
    data class Text(val text: String) : Part
    data class ToolCall(
        val toolCallId: String,     // unique ID
        val toolName: String,       // e.g., "web_search"
        val input: JsonElement?     // tool arguments as raw JSON
    ) : Part
    data class ToolResult(
        val toolCallId: String,     // matches the ToolCall's ID
        val toolName: String,
        val output: JsonElement?    // tool output as raw JSON
    ) : Part
}
```

### Reading Tool Calls in Responses

```kotlin
val response = client.generateResult(agentId, "What's the weather in Tokyo?")

for (part in response.parts) {
    when (part) {
        is Part.Text       -> println("Agent: ${part.text}")
        is Part.ToolCall   -> println("Called ${part.toolName}(${part.input})")
        is Part.ToolResult -> println("Result: ${part.output}")
    }
}
```

A typical response with tool use:
```
parts = [
    ToolCall(id="tc_1", toolName="web_search", input={"query":"Tokyo weather"}),
    ToolResult(id="tc_1", toolName="web_search", output={"temp":"22°C"}),
    Text(text="The weather in Tokyo is 22°C and sunny.")
]
```

Tool calls are executed server-side by the Chatbase agent — as a consumer, you just read the results.

---

## Deep Dive: Internal Implementation

These files live under `internal/` — you don't use them directly, but understanding them helps you debug and extend the SDK.

### 1. `ChatbaseClientImpl.kt` — The Implementation

Implements every method in `ChatbaseClient`. Pattern for each method:
- Build a request body (using internal `RequestModels`)
- Call `apiExecutor.executeRequest<ResponseType>(...)` for non-streaming
- Call `createSseFlow(request)` for streaming
- Return the result or Flow

### 2. `ApiExecutor.kt` — HTTP Request Builder & Runner

- `buildPostRequest(path, body)` — creates an OkHttp `Request` with JSON body
- `executeRequest<T>(client, request)` — executes the request, deserializes the response, throws `ApiException` or `NetworkException` on failure
- Uses `CallAwait.kt` to suspend until the response arrives

### 3. `HttpClientFactory.kt` — OkHttp Client Setup

Creates two `OkHttpClient` instances:
- **Standard client** — 30s connect, 30s read timeout (for chat, list, etc.)
- **SSE client** — 30s connect, 5 min read timeout (for streaming)

Both use `AuthInterceptor` to add the API key header.

### 4. `AuthInterceptor.kt` — Authentication

An OkHttp `Interceptor` that adds `Authorization: Bearer <apiKey>` to every request. Simple but critical.

### 5. `SseParser.kt` — SSE Event Parser

Parses raw SSE events (type + data strings) into typed `ChatStreamEvent` objects:
- `"text-delta"` → `ChatStreamEvent.TextDelta`
- `"finish"` → `ChatStreamEvent.Done` (with full `ChatResponse`)
- `"error"` → `ChatStreamEvent.Error`
- `"[DONE]"` → `null` (ignored, stream ends)

### 6. `JsonConfig.kt` — Serialization Setup

Configures `kotlinx.serialization.json.Json` with:
- `ignoreUnknownKeys = true` (forward-compatible — new API fields won't break the SDK)
- `isLenient = true` (tolerates minor JSON formatting issues)

### 7. `RequestModels.kt` — Internal DTOs

`@Serializable` data classes for request bodies:
- `ChatRequest(message, stream, conversationId)`
- `RetryRequest(stream)`
- `FeedbackRequest(rating)`

These are internal — consumers never see or create them.

### 8. `CallAwait.kt` — Coroutine Bridge

An extension function `Call.await()` that converts OkHttp's callback-based `Call.enqueue()` into a `suspend` function using `suspendCancellableCoroutine`. This is what makes `apiExecutor.executeRequest()` a suspend function instead of blocking.
