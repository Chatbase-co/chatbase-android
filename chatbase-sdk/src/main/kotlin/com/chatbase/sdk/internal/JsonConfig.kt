package com.chatbase.sdk.internal

import kotlinx.serialization.json.Json

internal val chatbaseJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    classDiscriminator = "type"
    encodeDefaults = true
    explicitNulls = false
}
