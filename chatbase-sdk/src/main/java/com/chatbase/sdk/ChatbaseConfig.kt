package com.chatbase.sdk

data class ChatbaseConfig(
    val apiKey: String,
    val baseUrl: String = "https://www.chatbase.co/api/v2",
    val connectTimeoutMs: Long = 10_000,
    val readTimeoutMs: Long = 30_000
) {
    class Builder {
        var apiKey: String = ""
        var baseUrl: String = "https://www.chatbase.co/api/v2"
        var connectTimeoutMs: Long = 10_000
        var readTimeoutMs: Long = 30_000

        fun build(): ChatbaseConfig {
            require(apiKey.isNotBlank()) { "apiKey must not be blank" }
            return ChatbaseConfig(
                apiKey = apiKey,
                baseUrl = baseUrl,
                connectTimeoutMs = connectTimeoutMs,
                readTimeoutMs = readTimeoutMs
            )
        }
    }
}
