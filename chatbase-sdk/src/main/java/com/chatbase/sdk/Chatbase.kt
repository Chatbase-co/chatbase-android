package com.chatbase.sdk

import android.content.Context
import com.chatbase.sdk.internal.AndroidIdProvider
import com.chatbase.sdk.internal.ChatbaseClientImpl

object Chatbase {

    @JvmStatic
    fun create(context: Context, agentId: String): ChatbaseClient {
        return create(context) {
            this.agentId = agentId
        }
    }

    @JvmStatic
    fun create(context: Context, block: ChatbaseConfig.Builder.() -> Unit): ChatbaseClient {
        val config = ChatbaseConfig.Builder().apply(block).build()
        val idProvider = AndroidIdProvider(context)
        return ChatbaseClientImpl(config, idProvider)
    }
}
