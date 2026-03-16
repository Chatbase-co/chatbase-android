package com.chatbase.sdk.streaming

import com.chatbase.sdk.exception.ChatbaseException
import com.chatbase.sdk.model.ChatResponse
import com.chatbase.sdk.model.ResponseMetadata

sealed interface ChatStreamEvent {
    data class TextDelta(val text: String) : ChatStreamEvent
    data class Metadata(val metadata: ResponseMetadata) : ChatStreamEvent
    data class Done(val response: ChatResponse) : ChatStreamEvent
    data class Error(val exception: ChatbaseException) : ChatStreamEvent
}
