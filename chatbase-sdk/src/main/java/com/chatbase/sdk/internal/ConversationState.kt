package com.chatbase.sdk.internal

import java.util.concurrent.atomic.AtomicReference

internal class ConversationState {

    private val currentId = AtomicReference<String?>(null)

    var conversationId: String?
        get() = currentId.get()
        set(value) = currentId.set(value)

    fun clear() {
        currentId.set(null)
    }
}
