package com.chatbase.sdk.internal

import java.util.concurrent.atomic.AtomicReference

internal class IdentityManager(
    private val anonymousIdProvider: AnonymousIdProvider
) {
    val deviceId: String get() = anonymousIdProvider.get()

    private data class Identity(val token: String, val userId: String)

    private val identity = AtomicReference<Identity?>(null)

    val jwtToken: String? get() = identity.get()?.token
    val currentUserId: String? get() = identity.get()?.userId
    val isIdentified: Boolean get() = identity.get() != null

    fun setIdentified(token: String, userId: String) {
        identity.set(Identity(token, userId))
    }

    fun clearIdentity() {
        identity.set(null)
    }
}
