package com.chatbase.sdk

import com.chatbase.sdk.internal.ChatbaseClientImpl

object Chatbase {

    fun client(apiKey: String): ChatbaseClient {
        return client {
            this.apiKey = apiKey
        }
    }

    fun client(block: ChatbaseConfig.Builder.() -> Unit): ChatbaseClient {
        val config = ChatbaseConfig.Builder().apply(block).build()
        return ChatbaseClientImpl(config)
    }
}
