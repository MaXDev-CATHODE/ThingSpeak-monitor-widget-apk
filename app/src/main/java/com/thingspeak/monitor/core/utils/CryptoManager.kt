package com.thingspeak.monitor.core.utils

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager for sensitive data encryption/decryption using Android Keystore.
 * Address P0 Security finding: Insecure API key storage.
 */
@Singleton
class CryptoManager @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context
) {
    private val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)

    private val sharedPrefs by lazy {
        EncryptedSharedPreferences.create(
            "secured_api_keys",
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /**
     * Store an API key securely.
     */
    fun saveApiKey(channelId: Long, apiKey: String?) {
        sharedPrefs.edit().putString("key_$channelId", apiKey).apply()
    }

    /**
     * Retrieve a secured API key.
     */
    fun getApiKey(channelId: Long): String? {
        return sharedPrefs.getString("key_$channelId", null)
    }

    /**
     * Remove a secured API key.
     */
    /**
     * Remove a secured API key.
     */
    fun removeApiKey(channelId: Long) {
        sharedPrefs.edit().remove("key_$channelId").apply()
    }

    /**
     * Clear all secured API keys.
     */
    fun clearAllApiKeys() {
        sharedPrefs.edit().clear().apply()
    }
}
