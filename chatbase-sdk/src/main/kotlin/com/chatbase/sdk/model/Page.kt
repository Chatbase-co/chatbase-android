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
}
