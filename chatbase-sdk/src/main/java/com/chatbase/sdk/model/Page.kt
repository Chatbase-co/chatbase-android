package com.chatbase.sdk.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class Page<T>(
    val data: List<T>,
    val cursor: String? = null,
    val hasMore: Boolean,
    val total: Int
) {
    @Transient
    var getNextPage: (suspend () -> Page<T>?)? = null
        internal set

    /** Whether more data can be loaded via [loadMore]. */
    val canLoadMore: Boolean get() = hasMore && getNextPage != null

    /**
     * Loads the next page and returns a new [Page] with the combined data.
     * Returns `null` if there is no next page.
     */
    suspend fun loadMore(): Page<T>? {
        val next = getNextPage?.invoke() ?: return null
        return Page(
            data = data + next.data,
            cursor = next.cursor,
            hasMore = next.hasMore,
            total = next.total
        ).also {
            it.getNextPage = next.getNextPage
        }
    }
}
