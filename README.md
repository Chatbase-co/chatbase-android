# Chatbase SDK for JVM & Android

The official Chatbase SDK for Kotlin/JVM and Android. Interact with Chatbase AI agents programmatically — send messages, stream responses, manage conversations, and more.

## Features

- Non-streaming and streaming chat (`generateResult`, `generateText`, `stream`, `streamText`)
- Conversation management — list, retrieve, and continue conversations
- User-scoped conversations via `userId`
- Message retry with full and text-only variants
- Message feedback (`positive` / `negative`)
- Cursor-based pagination with `getNextPage` helper
- Structured responses with typed `Part` objects (text, tool-call, tool-result)
- Kotlin coroutines and `Flow`-based streaming
- Configurable timeouts and base URL

## Requirements

- Java 11+
- Kotlin Coroutines

## Installation

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    implementation("com.chatbase:chatbase-sdk:0.1.0")
}
```

### Gradle (Groovy)

```groovy
dependencies {
    implementation 'com.chatbase:chatbase-sdk:0.1.0'
}
```

### Maven

```xml
<dependency>
    <groupId>com.chatbase</groupId>
    <artifactId>chatbase-sdk</artifactId>
    <version>0.1.0</version>
</dependency>
```

## Quick Start

```kotlin
import com.chatbase.sdk.Chatbase

val client = Chatbase.client("your-api-key")

// Generate a text response
val text = client.generateText(
    agentId = "your-agent-id",
    message = "Hello!"
)
println(text)

client.close()
```

## Chat

### Generate a full result

```kotlin
val response = client.generateResult(
    agentId = "your-agent-id",
    message = "What is Chatbase?"
)

println(response.id)
println(response.metadata.conversationId)
println(response.metadata.finishReason) // STOP or ERROR
response.parts.filterIsInstance<Part.Text>().forEach { println(it.text) }
```

### Generate text only

```kotlin
val text = client.generateText(
    agentId = "your-agent-id",
    message = "What is Chatbase?"
)
println(text)
```

### Stream events

```kotlin
client.stream(
    agentId = "your-agent-id",
    message = "Tell me about Chatbase"
).collect { event ->
    when (event) {
        is ChatStreamEvent.TextDelta -> print(event.text)
        is ChatStreamEvent.Metadata -> println("\nConversation: ${event.metadata.conversationId}")
        is ChatStreamEvent.Done -> println("\nDone: ${event.response.id}")
        is ChatStreamEvent.Error -> System.err.println("Error: ${event.exception.message}")
    }
}
```

### Stream text only

```kotlin
client.streamText(
    agentId = "your-agent-id",
    message = "Tell me about Chatbase"
).collect { chunk ->
    print(chunk)
}
```

## Conversations

### Continue a conversation

```kotlin
val first = client.generateResult(agentId = agentId, message = "My name is Alice.")
val conversationId = first.metadata.conversationId

val second = client.generateResult(
    agentId = agentId,
    message = "What is my name?",
    conversationId = conversationId
)
```

### List conversations

```kotlin
val page = client.listConversations(agentId = agentId, limit = 10)
page.data.forEach { println("${it.id} — ${it.title}") }
```

### Pagination

```kotlin
var page = client.listConversations(agentId = agentId, limit = 10)
while (true) {
    page.data.forEach { println(it.id) }
    page = page.getNextPage?.invoke() ?: break
}
```

### Get a conversation

```kotlin
val conversation = client.getConversation(agentId = agentId, conversationId = "conv-id")
```

### List messages in a conversation

```kotlin
val messages = client.listMessages(agentId = agentId, conversationId = "conv-id")
messages.data.forEach { println("${it.role}: ${it.parts}") }
```

### List conversations by user

```kotlin
val userConversations = client.listUserConversations(
    agentId = agentId,
    userId = "user-123"
)
```

## Retry

Retry the last assistant message in a conversation:

```kotlin
// Full result
val result = client.retryResult(agentId, conversationId, messageId)

// Text only
val text = client.retryText(agentId, conversationId, messageId)

// Stream events
client.retryStream(agentId, conversationId, messageId).collect { event -> ... }

// Stream text
client.retryStreamText(agentId, conversationId, messageId).collect { chunk -> print(chunk) }
```

## Feedback

Submit feedback on a message:

```kotlin
import com.chatbase.sdk.model.Feedback

// Add positive feedback
client.updateFeedback(agentId, conversationId, messageId, Feedback.POSITIVE)

// Add negative feedback
client.updateFeedback(agentId, conversationId, messageId, Feedback.NEGATIVE)

// Remove feedback
client.updateFeedback(agentId, conversationId, messageId, null)
```

## Configuration

```kotlin
val client = Chatbase.client {
    apiKey = "your-api-key"                           // required
    baseUrl = "https://www.chatbase.co/api/v2"        // default
    connectTimeoutMs = 10_000                         // default: 10 seconds
    readTimeoutMs = 30_000                            // default: 30 seconds
}
```

## Models

### ChatResponse

| Field      | Type               | Description                        |
|------------|--------------------|------------------------------------|
| `id`       | `String`           | Response message ID                |
| `role`     | `String`           | Always `"assistant"`               |
| `parts`    | `List<Part>`       | Response content parts             |
| `metadata` | `ResponseMetadata` | Conversation ID, usage, finish reason |

### Part (sealed interface)

| Variant        | Fields                                    |
|----------------|-------------------------------------------|
| `Part.Text`    | `text: String`                            |
| `Part.ToolCall`| `toolCallId`, `toolName`, `input`         |
| `Part.ToolResult`| `toolCallId`, `toolName`, `output`      |

### ChatStreamEvent (sealed interface)

| Variant     | Fields                          |
|-------------|---------------------------------|
| `TextDelta` | `text: String`                  |
| `Metadata`  | `metadata: ResponseMetadata`    |
| `Done`      | `response: ChatResponse`        |
| `Error`     | `exception: ChatbaseException`  |

### Page\<T\>

| Field        | Type                           | Description             |
|--------------|--------------------------------|-------------------------|
| `data`       | `List<T>`                      | Items in the page       |
| `cursor`     | `String?`                      | Cursor for next page    |
| `hasMore`    | `Boolean`                      | Whether more pages exist|
| `total`      | `Int`                          | Total number of items   |
| `getNextPage`| `(suspend () -> Page<T>?)?`    | Fetch next page helper  |

### Enums

- **`Role`**: `USER`, `ASSISTANT`
- **`FinishReason`**: `STOP`, `ERROR`
- **`Feedback`**: `POSITIVE`, `NEGATIVE`

## Error Handling

All SDK exceptions extend `ChatbaseException`:

- **`ApiException`** — HTTP error from the API
  - `httpStatus: Int`
  - `errorCode: String`
  - `errorMessage: String`
  - `isRateLimited`, `isAuthError`, `isNotFound`, `isCreditsExhausted`
- **`NetworkException`** — connectivity / timeout errors

### Non-streaming

```kotlin
try {
    val text = client.generateText(agentId, "Hello")
} catch (e: ApiException) {
    if (e.isRateLimited) { /* back off */ }
} catch (e: NetworkException) {
    // retry or surface to user
}
```

### Streaming

```kotlin
client.stream(agentId, "Hello").collect { event ->
    when (event) {
        is ChatStreamEvent.Error -> println("Stream error: ${event.exception.message}")
        else -> { /* handle normally */ }
    }
}
```

## License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.
