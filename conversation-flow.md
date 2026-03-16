# Full Conversation Flow Walkthrough

A realistic end-to-end scenario: fetch an existing conversation's history, then send two streaming messages — all while maintaining local app state.

---

## Setup

```kotlin
import com.chatbase.sdk.Chatbase
import com.chatbase.sdk.model.Part
import com.chatbase.sdk.model.Role
import com.chatbase.sdk.streaming.ChatStreamEvent
import kotlinx.coroutines.runBlocking

// 1. Create the client
val client = Chatbase.client(apiKey = "your-api-key")

// 2. Define local app state — a simple mutable list of UI messages
data class UiMessage(val role: String, var text: String)

val messages = mutableListOf<UiMessage>()

// IDs for the conversation we're working with
val agentId = "your-agent-id"
val conversationId = "existing-conversation-id"
```

`messages` is the single source of truth for what the UI displays. Every SDK interaction updates this list.

---

## Step 1 — Load Existing Conversation

Fetch the conversation and populate local state from its history.

```kotlin
runBlocking {
    // Fetch the conversation (includes message history)
    val conversation = client.getConversation(agentId, conversationId)

    // Map server messages into local state
    conversation.messages?.forEach { message ->
        val text = message.parts
            .filterIsInstance<Part.Text>()
            .joinToString("") { it.text }

        messages.add(UiMessage(
            role = when (message.role) {
                Role.USER      -> "user"
                Role.ASSISTANT -> "assistant"
            },
            text = text
        ))
    }
}
```

### What `getConversation` returns

```
Conversation(
    id = "existing-conversation-id",
    status = "active",
    createdAt = 1717000000.0,
    updatedAt = 1717000060.0,
    messages = [
        Message(id="msg_1", role=USER,      parts=[Part.Text("What is Kotlin?")]),
        Message(id="msg_2", role=ASSISTANT,  parts=[Part.Text("Kotlin is a modern...")])
    ]
)
```

### Local state after Step 1

```
messages = [
    UiMessage(role="user",      text="What is Kotlin?"),
    UiMessage(role="assistant",  text="Kotlin is a modern...")
]
```

---

## Step 2 — First Streaming Message

Add a new user question and stream the assistant's reply, updating local state as deltas arrive.

```kotlin
runBlocking {
    // Optimistic UI — add the user message immediately
    messages.add(UiMessage(role = "user", text = "How does it handle null safety?"))

    // Add a placeholder for the assistant's response
    val assistantMessage = UiMessage(role = "assistant", text = "")
    messages.add(assistantMessage)

    // Start the stream, continuing the same conversation
    val stream = client.stream(
        agentId = agentId,
        message = "How does it handle null safety?",
        conversationId = conversationId
    )

    // Collect events and update local state in real time
    stream.collect { event ->
        when (event) {
            is ChatStreamEvent.TextDelta -> {
                // Append each chunk — this is the live typing effect
                assistantMessage.text += event.text
                // → UI re-renders here in a real app
            }

            is ChatStreamEvent.Metadata -> {
                // Response metadata arrived (conversationId, etc.)
                // Usually a no-op for UI, but available if needed
            }

            is ChatStreamEvent.Done -> {
                // Stream finished — finalize with the full response
                val response = event.response
                println("Message ID: ${response.id}")
                println("Conversation ID: ${response.metadata.conversationId}")
                println("Finish reason: ${response.metadata.finishReason}")
                println("Credits used: ${response.metadata.usage.credits}")
            }

            is ChatStreamEvent.Error -> {
                // Handle the error — update the placeholder with an error message
                assistantMessage.text = "Error: ${event.exception.message}"
            }
        }
    }
}
```

### How local state evolves during the stream

```
After optimistic add:
  messages[2] = UiMessage(role="user",      text="How does it handle null safety?")
  messages[3] = UiMessage(role="assistant",  text="")

After TextDelta("Kotlin uses"):
  messages[3] = UiMessage(role="assistant",  text="Kotlin uses")

After TextDelta(" a type system"):
  messages[3] = UiMessage(role="assistant",  text="Kotlin uses a type system")

After TextDelta(" that distinguishes"):
  messages[3] = UiMessage(role="assistant",  text="Kotlin uses a type system that distinguishes")

  ... more deltas ...

After Done:
  messages[3] = UiMessage(role="assistant",  text="Kotlin uses a type system that distinguishes nullable and non-nullable types...")
```

### What `Done` delivers

```
ChatResponse(
    id = "msg_3",
    role = "assistant",
    parts = [Part.Text("Kotlin uses a type system that distinguishes...")],
    metadata = ResponseMetadata(
        userMessageId = "msg_user_2",
        conversationId = "existing-conversation-id",
        finishReason = STOP,
        usage = Usage(credits = 0.5)
    )
)
```

---

## Step 3 — Second Streaming Message (Conversation Continuity)

Same pattern — demonstrates that the conversation context is preserved across messages.

```kotlin
runBlocking {
    messages.add(UiMessage(role = "user", text = "Show me an example"))

    val assistantMessage = UiMessage(role = "assistant", text = "")
    messages.add(assistantMessage)

    client.stream(
        agentId = agentId,
        message = "Show me an example",
        conversationId = conversationId   // same conversation ID
    ).collect { event ->
        when (event) {
            is ChatStreamEvent.TextDelta -> {
                assistantMessage.text += event.text
            }
            is ChatStreamEvent.Done -> {
                println("Message ID: ${event.response.id}")
            }
            is ChatStreamEvent.Error -> {
                assistantMessage.text = "Error: ${event.exception.message}"
            }
            is ChatStreamEvent.Metadata -> { /* no-op */ }
        }
    }
}
```

### Local state after Step 3

The assistant knows the full conversation history — "What is Kotlin?", the null safety question, and now the example request — because we passed the same `conversationId` every time.

```
messages = [
    UiMessage(role="user",      text="What is Kotlin?"),                          // from history
    UiMessage(role="assistant",  text="Kotlin is a modern..."),                    // from history
    UiMessage(role="user",      text="How does it handle null safety?"),           // Step 2
    UiMessage(role="assistant",  text="Kotlin uses a type system that..."),        // Step 2 (streamed)
    UiMessage(role="user",      text="Show me an example"),                        // Step 3
    UiMessage(role="assistant",  text="Here's an example:\n\nfun greet(name:...") // Step 3 (streamed)
]
```

---

## Summary

| Step | SDK Call | Returns | Local State Change |
|------|----------|---------|-------------------|
| 1 | `client.getConversation(agentId, conversationId)` | `Conversation` with `messages: List<Message>?` | Populate `messages` from history |
| 2 | `client.stream(agentId, message, conversationId)` | `Flow<ChatStreamEvent>` | Add user + placeholder, grow placeholder via `TextDelta`, finalize on `Done` |
| 3 | `client.stream(agentId, message, conversationId)` | `Flow<ChatStreamEvent>` | Same pattern — conversation context preserved |

### Key types at a glance

| Type | Key Fields |
|------|-----------|
| `Conversation` | `id`, `status`, `messages: List<Message>?` |
| `Message` | `id`, `role: Role`, `parts: List<Part>` |
| `Part.Text` | `text: String` |
| `Role` | `USER`, `ASSISTANT` |
| `ChatStreamEvent.TextDelta` | `text: String` (incremental chunk) |
| `ChatStreamEvent.Done` | `response: ChatResponse` (final result) |
| `ChatStreamEvent.Error` | `exception: ChatbaseException` |
| `ChatResponse` | `id`, `role`, `parts`, `metadata: ResponseMetadata` |
| `ResponseMetadata` | `conversationId`, `userMessageId`, `finishReason`, `usage` |
