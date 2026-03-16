package com.chatbase.sdk.internal

import com.chatbase.sdk.ChatbaseConfig
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

internal object HttpClientFactory {

    fun create(config: ChatbaseConfig): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(config.apiKey))
            .connectTimeout(config.connectTimeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(config.readTimeoutMs, TimeUnit.MILLISECONDS)
            .build()
    }

    fun createForSse(config: ChatbaseConfig): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(config.apiKey))
            .connectTimeout(config.connectTimeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(5, TimeUnit.MINUTES)
            .build()
    }
}
