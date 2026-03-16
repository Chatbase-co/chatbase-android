package com.chatbase.sdk.exception

class ApiException(
    val httpStatus: Int,
    val errorCode: String,
    val errorMessage: String,
    val details: Map<String, String>? = null
) : ChatbaseException("HTTP $httpStatus $errorCode: $errorMessage") {

    val isRateLimited: Boolean get() = httpStatus == 429
    val isAuthError: Boolean get() = httpStatus == 401
    val isNotFound: Boolean get() = httpStatus == 404
    val isCreditsExhausted: Boolean get() = httpStatus == 402
}
