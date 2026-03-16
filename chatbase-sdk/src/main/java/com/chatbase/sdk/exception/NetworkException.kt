package com.chatbase.sdk.exception

class NetworkException(
    message: String,
    cause: Throwable? = null
) : ChatbaseException(message, cause)
