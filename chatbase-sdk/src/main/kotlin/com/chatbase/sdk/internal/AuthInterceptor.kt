package com.chatbase.sdk.internal

import okhttp3.Interceptor
import okhttp3.Response

internal class AuthInterceptor(private val apiKey: String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header("Authorization", "Bearer $apiKey")
            .build()
        return chain.proceed(request)
    }
}
