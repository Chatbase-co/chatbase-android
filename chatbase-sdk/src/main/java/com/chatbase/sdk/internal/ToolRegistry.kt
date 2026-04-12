package com.chatbase.sdk.internal

import java.util.concurrent.ConcurrentHashMap

internal typealias ToolHandler = suspend (input: Map<String, Any?>) -> Any

internal class ToolRegistry {

    private val tools = ConcurrentHashMap<String, ToolHandler>()

    fun register(name: String, handler: ToolHandler) {
        tools[name] = handler
    }

    fun remove(name: String) {
        tools.remove(name)
    }

    fun get(name: String): ToolHandler? = tools[name]

    fun has(name: String): Boolean = tools.containsKey(name)
}
