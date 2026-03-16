package com.chatbase.sdk.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class FinishReason {
    @SerialName("stop") STOP,
    @SerialName("error") ERROR
}
