package com.chatbase.sdk.internal

import android.content.Context
import android.provider.Settings
import java.util.UUID

internal fun interface AnonymousIdProvider {
    fun get(): String
}

internal class AndroidIdProvider(context: Context) : AnonymousIdProvider {

    private val deviceId: String

    init {
        val appContext = context.applicationContext
        val androidId = Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ANDROID_ID)
        deviceId = if (!androidId.isNullOrBlank()) {
            androidId
        } else {
            val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.getString(KEY_FALLBACK_ID, null) ?: UUID.randomUUID().toString().also {
                prefs.edit().putString(KEY_FALLBACK_ID, it).apply()
            }
        }
    }

    override fun get(): String = deviceId

    companion object {
        private const val PREFS_NAME = "chatbase_sdk_prefs"
        private const val KEY_FALLBACK_ID = "chatbase_fallback_device_id"
    }
}
