# Chatbase Android SDK

The official Android SDK for [Chatbase](https://www.chatbase.co). Build conversational AI experiences with streaming responses, conversation history, client-side tool execution, and user identity management.

> See the [demo app](https://github.com/Chatbase-co/android-demo) for a full working example built with Jetpack Compose.

## Installation

```kotlin
dependencies {
    implementation("com.chatbase:chatbase-sdk:0.0.1-alpha01")
}
```

## Getting Started

### Create a client

The simplest way to get started — just pass your agent ID:

```kotlin
val client = Chatbase.create(context, "your-agent-id")
```

Or configure timeouts and a custom base URL:

```kotlin
val client = Chatbase.create(context) {
    agentId = "your-agent-id"
    baseUrl = "https://www.chatbase.co/api/sdk/agents"  // default
    connectTimeoutMs = 10_000                            // default
    readTimeoutMs = 30_000                               // default
}
```

When you're done, release resources:

```kotlin
client.close()
```

### Send a message

`sendMessage` streams the response and handles the tool-call loop automatically. Use callbacks to react in real time:

```kotlin
val response = client.sendMessage("Hello!") {
    onTextDelta { delta -> print(delta) }
    onFinish { response -> println("\nDone: ${response.id}") }
    onError { error -> println("Error: ${error.message}") }
}
```

### Continue a conversation

Every response includes a `conversationId`. Pass it back to continue the thread:

```kotlin
val first = client.sendMessage("My name is Alice.")
val conversationId = first.metadata.conversationId

val second = client.sendMessage(
    message = "What is my name?",
    conversationId = conversationId
)
```

Or call `newConversation()` to start fresh:

```kotlin
client.newConversation()
```

## Client-Side Tools

Register tools that run locally when the agent invokes them. The SDK calls your handler, sends the result back to the agent, and continues streaming — all within a single `sendMessage` call.

```kotlin
client.tool("get_spell_damage") { input ->
    val spell = input["spell"] as String
    val damage = (5000..10000).random()
    mapOf("spell" to spell, "damage" to damage)
}
```

Tool handlers are `suspend` functions, so they can show UI and wait for user input:

```kotlin
client.tool("get_spell_damage") { _ ->
    // Suspend until the user picks a spell from a dialog
    val deferred = CompletableDeferred<String>()
    spellPickerRequest.value = deferred
    val spell = deferred.await()

    val damage = (5000..10000).random()
    mapOf("spell" to spell, "damage" to damage)
}
```

Track tool execution via callbacks:

```kotlin
client.sendMessage("Cast a spell!") {
    onToolCall { tool -> println("Calling ${tool.toolName}") }
    onToolResult { result -> println("Got: ${result.outputAsString()}") }
}
```

## Reactive State Holders

For Jetpack Compose apps, the SDK provides state holders that manage messages, loading, pagination, and errors as `StateFlow` — ready to collect in your UI.

### ConversationState

Manages a single conversation. Handles sending, streaming, history loading, and retry:

```kotlin
class ChatViewModel(
    client: ChatbaseClient,
    initialConversationId: String?
) : ViewModel() {

    private val conversation = ConversationState(client)
    val state: StateFlow<ConversationState.State> = conversation.state

    init {
        if (initialConversationId != null) {
            viewModelScope.launch {
                conversation.loadHistory(initialConversationId, limit = 20)
            }
        }
    }

    fun sendMessage(text: String) {
        viewModelScope.launch { conversation.sendMessage(text) }
    }

    fun retryMessage(messageId: String) {
        viewModelScope.launch { conversation.retry(messageId) }
    }

    fun loadMoreHistory() {
        viewModelScope.launch { conversation.loadMoreHistory() }
    }

    override fun onCleared() { conversation.close() }
}
```

In your Composable:

```kotlin
val state by viewModel.state.collectAsStateWithLifecycle()

// state.messages        — List<UiMessage>, display-ready
// state.isSending       — true while a message is in flight
// state.isLoadingHistory
// state.hasMoreHistory
// state.conversationId
// state.error           — ChatbaseException?
```

Each `UiMessage` has a `content` that is either `UiMessageContent.Text` or `UiMessageContent.ToolCall`, with fields like `isStreaming`, `isError`, and `messageId` for retry.

### ConversationListState

Manages a paginated list of conversations:

```kotlin
val conversationList = ConversationListState(client)

// Load first page
viewModelScope.launch { conversationList.load(limit = 20) }

// Load next page
viewModelScope.launch { conversationList.loadMore() }
```

```kotlin
val state by conversationList.state.collectAsStateWithLifecycle()

// state.conversations — List<Conversation>
// state.isLoading
// state.hasMore
// state.error
```

## User Identity

Identify users with a JWT token to scope conversations to a specific user:

```kotlin
client.identify(token)

client.isIdentified    // true
client.currentUserId   // user ID decoded from the token
client.deviceId        // auto-generated device ID
```

Clear identity on logout:

```kotlin
client.logout()
```

## Low-Level Streaming

If you need raw streaming events instead of the managed state holders, use `sendMessageStream`. Note that tool calls are **not** handled automatically in this mode:

```kotlin
client.sendMessageStream("Hello").collect { event ->
    when (event) {
        is ChatStreamEvent.TextDelta -> print(event.delta)
        is ChatStreamEvent.ToolInputAvailable -> { /* handle tool call manually */ }
        is ChatStreamEvent.Finish -> println("\nDone")
        is ChatStreamEvent.Error -> println("Error: ${event.exception.message}")
        else -> {}
    }
}
```

## Retry

Retry a failed assistant message:

```kotlin
// By IDs
val retried = client.retry(conversationId, messageId) {
    onTextDelta { delta -> print(delta) }
}

// From a response object
val retried = client.retry(response)

// Via ConversationState (removes the failed message and re-streams)
conversation.retry(messageId)
```

## Conversations & History

Access conversations and messages directly when not using the state holders:

```kotlin
// List conversations
val page = client.listConversations(limit = 20)
page.data.forEach { println("${it.id} — ${it.title}") }

// Paginate
if (page.canLoadMore) {
    val nextPage = page.loadMore()
}

// List messages in a conversation
val messages = client.listMessages(conversationId, limit = 50)
messages.data.forEach { msg ->
    println("${msg.role}: ${msg.parts}")
}
```

## Error Handling

All SDK exceptions extend `ChatbaseException`:

- **`ApiException`** — HTTP errors (`httpStatus`, `errorCode`, `isRateLimited`, `isNotFound`, `isCreditsExhausted`)
- **`NetworkException`** — connectivity and timeout errors

```kotlin
try {
    client.sendMessage("Hello")
} catch (e: ApiException) {
    when {
        e.isRateLimited -> { /* back off */ }
        e.isCreditsExhausted -> { /* notify user */ }
    }
} catch (e: NetworkException) {
    // retry or show offline state
}
```

In streaming flows, errors arrive as events:

```kotlin
client.sendMessageStream("Hello").collect { event ->
    if (event is ChatStreamEvent.Error) {
        println("Stream error: ${event.exception.message}")
    }
}
```

## Requirements

- Android API 24+ (Android 7.0)
- Java 11+
- Kotlin Coroutines
