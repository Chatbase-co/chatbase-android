package com.chatbase.sdk

import com.chatbase.sdk.exception.ChatbaseException
import com.chatbase.sdk.model.Conversation
import com.chatbase.sdk.model.Page
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Reactive state holder for a paginated list of conversations.
 *
 * Wraps [ChatbaseClient.listConversations] and exposes an observable [StateFlow]
 * containing the conversation list, loading state, and pagination info.
 *
 * ```kotlin
 * class AppViewModel(client: ChatbaseClient) : ViewModel() {
 *     private val conversations = ConversationListState(client)
 *     val state = conversations.state
 *
 *     fun load() { viewModelScope.launch { conversations.load() } }
 *     fun loadMore() { viewModelScope.launch { conversations.loadMore() } }
 * }
 * ```
 */
class ConversationListState(
    private val client: ChatbaseClient
) {

    data class State(
        val conversations: List<Conversation> = emptyList(),
        val isLoading: Boolean = false,
        val hasMore: Boolean = false,
        val error: ChatbaseException? = null
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var currentPage: Page<Conversation>? = null

    /**
     * Loads the first page of conversations. Replaces any existing data.
     */
    suspend fun load(limit: Int = 20) {
        _state.update { it.copy(isLoading = true, error = null) }
        try {
            val page = client.listConversations(limit = limit)
            currentPage = page
            _state.update {
                it.copy(
                    conversations = page.data,
                    hasMore = page.canLoadMore,
                    isLoading = false
                )
            }
        } catch (e: Exception) {
            _state.update {
                it.copy(
                    isLoading = false,
                    error = wrapException(e)
                )
            }
        }
    }

    /**
     * Loads the next page of conversations and appends to the existing list.
     */
    suspend fun loadMore() {
        val page = currentPage ?: return
        if (!page.canLoadMore || _state.value.isLoading) return

        _state.update { it.copy(isLoading = true) }
        try {
            val updated = page.loadMore() ?: return
            currentPage = updated
            _state.update {
                it.copy(
                    conversations = updated.data,
                    hasMore = updated.canLoadMore,
                    isLoading = false
                )
            }
        } catch (e: Exception) {
            _state.update { it.copy(isLoading = false, error = wrapException(e)) }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    private fun wrapException(e: Exception): ChatbaseException {
        return if (e is ChatbaseException) e
        else ChatbaseException(e.message ?: "Unknown error", e)
    }
}
