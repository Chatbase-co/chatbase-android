package com.chatbase.sdk.internal

import com.chatbase.sdk.ChatbaseConfig
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

internal object HttpClientFactory {

    fun create(config: ChatbaseConfig, identityManager: IdentityManager): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(SdkHeadersInterceptor(identityManager))
            .connectTimeout(config.connectTimeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(config.readTimeoutMs, TimeUnit.MILLISECONDS)
            .build()
    }

    fun createForSse(config: ChatbaseConfig, identityManager: IdentityManager): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(SdkHeadersInterceptor(identityManager))
            .connectTimeout(config.connectTimeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(5, TimeUnit.MINUTES)
            .build()
    }
}
