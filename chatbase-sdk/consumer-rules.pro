# Chatbase SDK consumer ProGuard rules

# Keep kotlinx.serialization classes
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep Chatbase SDK model classes (serializable)
-keep,includedescriptorclasses class com.chatbase.sdk.model.** { *; }
-keepclassmembers class com.chatbase.sdk.model.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.chatbase.sdk.streaming.** { *; }

# Keep public SDK API
-keep class com.chatbase.sdk.Chatbase { *; }
-keep class com.chatbase.sdk.ChatbaseClient { *; }
-keep class com.chatbase.sdk.ChatbaseConfig { *; }
-keep class com.chatbase.sdk.ChatbaseConfig$Builder { *; }
-keep class com.chatbase.sdk.StreamCallbacks { *; }
-keep class com.chatbase.sdk.ToolCallInfo { *; }
-keep class com.chatbase.sdk.ToolResultInfo { *; }
-keep class com.chatbase.sdk.exception.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
