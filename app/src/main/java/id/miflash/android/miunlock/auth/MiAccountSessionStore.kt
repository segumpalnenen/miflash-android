package id.miflash.android.miunlock.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class MiAccountSessionStore(context: Context) {

    private val prefs: SharedPreferences by lazy {
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun save(credentials: MiAccountCredentials) {
        prefs.edit()
            .putString(KEY_DEVICE_ID, credentials.deviceId)
            .putString(KEY_PASS_TOKEN, credentials.passToken)
            .putString(KEY_USER_ID, credentials.userId)
            .apply()
    }

    fun load(): MiAccountCredentials? {
        val deviceId = prefs.getString(KEY_DEVICE_ID, null)
        val passToken = prefs.getString(KEY_PASS_TOKEN, null)
        val userId = prefs.getString(KEY_USER_ID, null)
        if (deviceId.isNullOrEmpty() || passToken.isNullOrEmpty() || userId.isNullOrEmpty()) return null
        return MiAccountCredentials(deviceId, passToken, userId)
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS_NAME = "mi_account_session"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_PASS_TOKEN = "pass_token"
        private const val KEY_USER_ID = "user_id"
    }
}
