package com.calc.expense

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

data class Settings(
    val token: String = "",
    val databaseId: String = "",
    val nameProp: String = "이름",
    val priceProp: String = "금액",
    val dateProp: String = "날짜",
) {
    val isComplete: Boolean
        get() = token.isNotBlank() && databaseId.isNotBlank() &&
            nameProp.isNotBlank() && priceProp.isNotBlank() && dateProp.isNotBlank()
}

object SettingsStore {

    private const val TAG = "SettingsStore"
    private const val SECURE_FILE = "expense_secure"
    private const val PLAIN_FILE = "expense_plain"

    /** 기기 키스토어가 말썽이면 평문 저장으로 내려앉는다. UI에서 이 값을 표시해 알린다. */
    @Volatile var usingEncryption: Boolean = true
        private set

    private var cached: SharedPreferences? = null

    private fun prefs(context: Context): SharedPreferences {
        cached?.let { return it }
        val app = context.applicationContext
        val p = try {
            val masterKey = MasterKey.Builder(app)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                app,
                SECURE_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            ).also { usingEncryption = true }
        } catch (e: Exception) {
            Log.w(TAG, "암호화 저장소 초기화 실패, 평문으로 대체", e)
            usingEncryption = false
            app.getSharedPreferences(PLAIN_FILE, Context.MODE_PRIVATE)
        }
        cached = p
        return p
    }

    fun load(context: Context): Settings {
        val p = prefs(context)
        val d = Settings()
        return Settings(
            token = p.getString("token", d.token) ?: d.token,
            databaseId = p.getString("databaseId", d.databaseId) ?: d.databaseId,
            nameProp = p.getString("nameProp", d.nameProp) ?: d.nameProp,
            priceProp = p.getString("priceProp", d.priceProp) ?: d.priceProp,
            dateProp = p.getString("dateProp", d.dateProp) ?: d.dateProp,
        )
    }

    fun save(context: Context, s: Settings) {
        prefs(context).edit()
            .putString("token", s.token.trim())
            .putString("databaseId", NotionIds.normalize(s.databaseId))
            .putString("nameProp", s.nameProp.trim())
            .putString("priceProp", s.priceProp.trim())
            .putString("dateProp", s.dateProp.trim())
            .apply()
    }
}
