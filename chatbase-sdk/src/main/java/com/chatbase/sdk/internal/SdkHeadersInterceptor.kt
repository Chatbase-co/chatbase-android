package com.chatbase.sdk.internal

import okhttp3.Interceptor
import okhttp3.Response

internal class SdkHeadersInterceptor(
    private val identityManager: IdentityManager
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val builder = chain.request().newBuilder()
            .header("User-Agent", SDK_USER_AGENT)
            .header("X-Device-Id", identityManager.deviceId)

        identityManager.jwtToken?.let { token ->
            builder.header("X-User-Token", token)
        }

        return chain.proceed(builder.build())
    }
}
