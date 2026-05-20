package com.signalsoop.app.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Encrypted prefs for device identifiers and security toggles. */
class SecurePrefs(context: Context) {
    private val masterKey =
        MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

    private val prefs =
        EncryptedSharedPreferences.create(
            context.applicationContext,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )

    var meshDeviceId: String?
        get() = prefs.getString(KEY_MESH_ID, null)
        set(value) {
            prefs.edit().putString(KEY_MESH_ID, value).apply()
        }

    companion object {
        private const val PREFS_NAME = "signal_scoop_secure"
        private const val KEY_MESH_ID = "mesh_device_id"
    }
}
