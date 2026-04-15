package com.chatbase.sdk

data class ChatbaseConfig(
    val agentId: String,
    val baseUrl: String = "https://www.chatbase.co",
    val connectTimeoutMs: Long = 10_000,
    val readTimeoutMs: Long = 30_000
) {
    class Builder {
        var agentId: String = ""
        var baseUrl: String = "https://www.chatbase.co"
        var connectTimeoutMs: Long = 10_000
        var readTimeoutMs: Long = 30_000

        fun build(): ChatbaseConfig {
            require(agentId.isNotBlank()) { "agentId must not be blank" }
            return ChatbaseConfig(
                agentId = agentId,
                baseUrl = baseUrl,
                connectTimeoutMs = connectTimeoutMs,
                readTimeoutMs = readTimeoutMs
            )
        }
    }
}
