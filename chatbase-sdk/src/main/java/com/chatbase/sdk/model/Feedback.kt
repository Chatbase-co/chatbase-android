package com.chatbase.sdk.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class Feedback {
    @SerialName("positive") POSITIVE,
    @SerialName("negative") NEGATIVE
}
