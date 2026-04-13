package com.chatbase.sdk

import com.chatbase.sdk.exception.ChatbaseException
import com.chatbase.sdk.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.Closeable
import java.util.UUID

/**
 * Per-conversation reactive state holder.
 *
 * Wraps a [ChatbaseClient] and exposes an observable [StateFlow] of [State]
 * containing the message list, streaming progress, pagination, and errors.
 * Handles text delta accumulation, tool call card management, placeholder
 * lifecycle, and history pagination internally.
 *
 * This is a plain Kotlin class with no Android/Lifecycle dependencies.
 * Wrap it in a ViewModel, Compose `remember`, or any other lifecycle scope.
 *
 * ```kotlin
 * class ChatViewModel(client: ChatbaseClient, conversationId: String?) : ViewModel() {
 *     private val conversation = ConversationState(client)
 *     val state = conversation.state
 *
 *     fun send(text: String) { viewModelScope.launch { conversation.sendMessage(text) } }
 *     override fun onCleared() { conversation.close() }
 * }
 * ```
 */
class ConversationState(
    private val client: ChatbaseClient
) : Closeable {

    data class State(
        val messages: List<UiMessage> = emptyList(),
        val isSending: Boolean = false,
        val isLoadingHistory: Boolean = false,
        val hasMoreHistory: Boolean = false,
        val conversationId: String? = null,
        val error: ChatbaseException? = null
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var currentTurnIds = mutableListOf<String>()
    private var historyPage: Page<Message>? = null

    /**
     * Sets the conversation ID. Use when navigating to an existing conversation.
     */
    fun setConversationId(id: String?) {
        _state.update { it.copy(conversationId = id) }
    }

    /**
     * Clears the current error.
     */
    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    /**
     * Loads message history for a conversation.
     * Messages are returned in chronological order (oldest first).
     */
    suspend fun loadHistory(conversationId: String, limit: Int = 20) {
        _state.update { it.copy(isLoadingHistory = true, conversationId = conversationId) }
        try {
            val page = client.listMessages(conversationId, limit = limit)
            historyPage = page
            _state.update {
                it.copy(
                    messages = page.data.reversed().toUiMessages(conversationId),
                    isLoadingHistory = false,
                    hasMoreHistory = page.canLoadMore
                )
            }
        } catch (e: Exception) {
            _state.update {
                it.copy(
                    isLoadingHistory = false,
                    error = wrapException(e)
                )
            }
        }
    }

    /**
     * Loads the next page of history and prepends it to the current messages.
     */
    suspend fun loadMoreHistory() {
        val page = historyPage ?: return
        if (!page.canLoadMore || _state.value.isLoadingHistory) return
        val conversationId = _state.value.conversationId ?: return

        _state.update { it.copy(isLoadingHistory = true) }
        try {
            val updated = page.loadMore() ?: return
            historyPage = updated
            _state.update {
                it.copy(
                    messages = updated.data.reversed().toUiMessages(conversationId) +
                            it.messages.filter { msg -> msg.messageId == null },
                    isLoadingHistory = false,
                    hasMoreHistory = updated.canLoadMore
                )
            }
        } catch (e: Exception) {
            _state.update {
                it.copy(
                    isLoadingHistory = false,
                    error = wrapException(e)
                )
            }
        }
    }

    /**
     * Sends a message and streams the response.
     *
     * Creates a user message and an assistant streaming placeholder,
     * accumulates text deltas, manages tool call cards, and finalizes
     * the message list when the response completes.
     */
    suspend fun sendMessage(text: String) {
        if (text.isBlank()) return

        val userMsg = UiMessage(role = Role.USER, content = UiMessageContent.Text(text))
        val placeholderId = UUID.randomUUID().toString()
        val assistantPlaceholder = UiMessage(
            id = placeholderId,
            role = Role.ASSISTANT,
            content = UiMessageContent.Text(""),
            isStreaming = true
        )

        currentTurnIds = mutableListOf(placeholderId)

        _state.update {
            it.copy(
                messages = it.messages + userMsg + assistantPlaceholder,
                isSending = true,
                error = null
            )
        }

        try {
            val response = client.sendMessage(
                message = text,
                conversationId = _state.value.conversationId
            ) {
                onTextDelta { delta ->
                    appendTextToLastStreaming(delta)
                }
                onToolCall { toolCall ->
                    // Stop streaming on the current text placeholder
                    _state.update { s ->
                        s.copy(messages = s.messages.map { msg ->
                            if (msg.isStreaming && msg.content is UiMessageContent.Text) {
                                msg.copy(isStreaming = false)
                            } else msg
                        })
                    }
                    val toolId = UUID.randomUUID().toString()
                    currentTurnIds.add(toolId)
                    _state.update { s ->
                        s.copy(messages = s.messages + UiMessage(
                            id = toolId,
                            role = Role.ASSISTANT,
                            content = UiMessageContent.ToolCall(
                                toolCallId = toolCall.toolCallId,
                                toolName = toolCall.toolName,
                                input = toolCall.inputAsMap().toString(),
                                isExecuting = true
                            ),
                            isStreaming = false
                        ))
                    }
                }
                onToolResult { result ->
                    _state.update { s ->
                        s.copy(messages = s.messages.map { msg ->
                            val content = msg.content
                            if (content is UiMessageContent.ToolCall && content.toolCallId == result.toolCallId) {
                                msg.copy(content = content.copy(
                                    output = result.outputAsString(),
                                    isExecuting = false
                                ))
                            } else msg
                        })
                    }
                    // Create continuation placeholder for text after tool result
                    val contId = UUID.randomUUID().toString()
                    currentTurnIds.add(contId)
                    _state.update { s ->
                        s.copy(messages = s.messages + UiMessage(
                            id = contId,
                            role = Role.ASSISTANT,
                            content = UiMessageContent.Text(""),
                            isStreaming = true
                        ))
                    }
                }
                onError { error ->
                    markLastStreamingAsError(error.message ?: "Unknown error")
                }
            }

            finalizeResponse(response)
        } catch (e: Exception) {
            markLastTurnAsError(e.message ?: "Unknown error")
        }
    }

    /**
     * Retries a failed assistant message.
     *
     * Removes the original message and all subsequent messages from the same turn,
     * then streams a new response.
     */
    suspend fun retry(messageId: String) {
        val convId = _state.value.messages
            .firstOrNull { it.messageId == messageId }
            ?.conversationId ?: return

        val firstIdx = _state.value.messages.indexOfFirst { it.messageId == messageId }
        if (firstIdx < 0) return

        val placeholderId = UUID.randomUUID().toString()
        currentTurnIds = mutableListOf(placeholderId)

        _state.update { s ->
            s.copy(
                messages = s.messages.subList(0, firstIdx) + UiMessage(
                    id = placeholderId,
                    role = Role.ASSISTANT,
                    content = UiMessageContent.Text(""),
                    isStreaming = true
                ),
                isSending = true,
                error = null
            )
        }

        try {
            val response = client.retry(convId, messageId) {
                onTextDelta { delta -> appendTextToLastStreaming(delta) }
                onError { error ->
                    markLastStreamingAsError(error.message ?: "Unknown error")
                }
            }

            finalizeResponse(response)
        } catch (e: Exception) {
            markLastTurnAsError("Retry failed: ${e.message}")
        }
    }

    override fun close() {
        // No-op — the ChatbaseClient lifecycle is owned by the consumer
    }

    // -- Internal helpers --

    private fun appendTextToLastStreaming(delta: String) {
        _state.update { s ->
            val msgs = s.messages.toMutableList()
            val lastIdx = msgs.indexOfLast { it.content is UiMessageContent.Text && it.isStreaming }
            if (lastIdx >= 0) {
                val msg = msgs[lastIdx]
                val text = (msg.content as UiMessageContent.Text).text
                msgs[lastIdx] = msg.copy(content = UiMessageContent.Text(text + delta))
            }
            s.copy(messages = msgs)
        }
    }

    private fun markLastStreamingAsError(errorMessage: String) {
        _state.update { s ->
            val msgs = s.messages.toMutableList()
            val lastIdx = msgs.indexOfLast { it.role == Role.ASSISTANT }
            if (lastIdx >= 0) {
                msgs[lastIdx] = msgs[lastIdx].copy(
                    isStreaming = false,
                    isError = true,
                    content = UiMessageContent.Text("Error: $errorMessage")
                )
            }
            s.copy(messages = msgs)
        }
    }

    private fun markLastTurnAsError(errorMessage: String) {
        _state.update { s ->
            val msgs = s.messages.toMutableList()
            val lastIdx = msgs.indexOfLast { it.id in currentTurnIds }
            if (lastIdx >= 0) {
                msgs[lastIdx] = msgs[lastIdx].copy(
                    isStreaming = false,
                    isError = true,
                    content = UiMessageContent.Text("Error: $errorMessage")
                )
            }
            s.copy(messages = msgs, isSending = false)
        }
    }

    private fun finalizeResponse(response: ChatResponse) {
        val convId = response.metadata.conversationId
        val msgId = response.id
        _state.update { s ->
            val msgs = s.messages.map { msg ->
                if (msg.id in currentTurnIds) {
                    msg.copy(messageId = msgId, conversationId = convId, isStreaming = false)
                } else msg
            }.filter { msg ->
                val content = msg.content
                !(content is UiMessageContent.Text && content.text.isEmpty() && !msg.isStreaming && !msg.isError)
            }
            s.copy(
                messages = msgs,
                isSending = false,
                conversationId = convId ?: s.conversationId
            )
        }
    }

    private fun wrapException(e: Exception): ChatbaseException {
        return if (e is ChatbaseException) e
        else ChatbaseException(e.message ?: "Unknown error", e)
    }
}
