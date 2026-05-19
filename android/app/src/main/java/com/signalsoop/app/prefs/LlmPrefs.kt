package com.signalsoop.app.prefs

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.signalsoop.app.llm.LiteRtModelPreset

/** Encrypted storage for on-device model path and preset (no scan data). */
class LlmPrefs(context: Context) {
    companion object {
        private const val FILENAME = "signal_scoop_llm_prefs.xml"
        private const val KEY_MODEL_PATH = "local_model_absolute_path"
        private const val KEY_HF_TOKEN = "hf_token_optional"
        private const val KEY_PRESET = "llm_preset"
    }

    private val shared =
        EncryptedSharedPreferences.create(
            FILENAME,
            MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )

    var localModelPath: String
        get() = shared.getString(KEY_MODEL_PATH, "") ?: ""
        set(value) = shared.edit().putString(KEY_MODEL_PATH, value).apply()

    var hfTokenOptional: String
        get() = shared.getString(KEY_HF_TOKEN, "") ?: ""
        set(value) = shared.edit().putString(KEY_HF_TOKEN, value).apply()

    var presetOrdinal: Int
        get() = shared.getInt(KEY_PRESET, LiteRtModelPreset.DEFAULT.ordinal)
        set(value) = shared.edit().putInt(KEY_PRESET, value).apply()
}
