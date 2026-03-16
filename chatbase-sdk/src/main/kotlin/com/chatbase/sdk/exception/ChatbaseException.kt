package com.chatbase.sdk.exception

open class ChatbaseException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)
