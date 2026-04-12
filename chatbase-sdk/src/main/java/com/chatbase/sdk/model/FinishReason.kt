package com.chatbase.sdk.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class FinishReason {
    @SerialName("stop") STOP,
    @SerialName("error") ERROR,
    @SerialName("tool-calls") TOOL_CALLS,
    @SerialName("unknown") UNKNOWN
}
