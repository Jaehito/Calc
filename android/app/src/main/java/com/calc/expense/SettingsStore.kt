package com.calc.expense

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object SettingsStore {

    private const val TAG = "SettingsStore"
    private const val SECURE_FILE = "expense_secure"
    private const val PLAIN_FILE = "expense_plain"

    /** 곳간이 하나였던 시절의 예산 키. 개인 곳간으로 옮겨 준다. */
    private const val LEGACY_MONTHLY_BUDGET = "monthlyBudget"

    /**
     * 노션을 쓰던 시절의 키들. 읽지 않고, 저장할 때 지운다.
     *
     * 남겨 두면 «내 토큰이 아직 폰에 있나»가 애매해진다 — 안 쓰는 비밀은 지우는 것이 맞다.
     */
    private val LEGACY_NOTION_KEYS = listOf(
        "token", "nameProp", "priceProp", "dateProp", "purseProp", "categoryProp", "databaseId",
        "${Purse.PERSONAL.key}.databaseId", "${Purse.SHARED.key}.databaseId",
    )

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
            payDay = Payday.normalize(p.getInt("payDay", d.payDay)),
            personal = loadPurse(p, Purse.PERSONAL),
            shared = loadPurse(p, Purse.SHARED),
        )
    }

    private fun loadPurse(p: SharedPreferences, purse: Purse): PurseSettings {
        // 곳간이 하나였을 때 저장한 예산은 개인 곳간으로 읽는다. 다시 입력하지 않아도 되게.
        val legacyBudget: Long =
            if (purse == Purse.PERSONAL) p.getLong(LEGACY_MONTHLY_BUDGET, 0L) else 0L

        return PurseSettings(
            monthlyBudget = p.getLong("${purse.key}.monthlyBudget", legacyBudget),
            name = p.getString("${purse.key}.name", "").orEmpty(),
        )
    }

    fun save(context: Context, s: Settings) {
        val edit = prefs(context).edit()
            .putInt("payDay", Payday.normalize(s.payDay))

        for (purse in Purse.entries) {
            val p = s.of(purse)
            edit.putLong("${purse.key}.monthlyBudget", if (p.monthlyBudget > 0L) p.monthlyBudget else 0L)
                .putString("${purse.key}.name", p.name.trim().take(Purse.MAX_NAME_LENGTH))
        }

        // 옮겨 담았으니 옛 키는 지운다. 남겨두면 다음 로드에서 되살아난다.
        edit.remove(LEGACY_MONTHLY_BUDGET)
        for (key in LEGACY_NOTION_KEYS) edit.remove(key)
        edit.apply()
    }

    /** 계정이 바뀔 때. 예산·곳간 이름·월급날은 그 사람의 것이라 남기지 않는다. */
    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }
}
