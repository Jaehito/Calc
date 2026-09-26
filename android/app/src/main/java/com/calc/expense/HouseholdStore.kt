package com.calc.expense

import android.content.Context

/**
 * 공용 곳간을 배우자와 묶는 «가정» id 의 로컬 사본.
 *
 * Firestore 의 `users/{uid}.householdId` 가 SSOT 다 — 이건 매 기록마다 그걸 왕복하지
 * 않으려는 캐시일 뿐이다. 재설치로 이 캐시가 비어도 [HouseholdRepository.currentHouseholdId]
 * 로 다시 채울 수 있다(uid 는 구글 로그인으로 재설치에도 유지되므로).
 */
object HouseholdStore {

    private const val PREFS = "household_store"
    private const val KEY_HOUSEHOLD_ID = "household_id"
    private const val KEY_CODE = "code"
    private const val KEY_MISSING = "settings_missing"

    fun householdId(context: Context): String? =
        prefs(context).getString(KEY_HOUSEHOLD_ID, null)

    fun setHouseholdId(context: Context, id: String?) {
        val editor = prefs(context).edit()
        if (id == null) editor.remove(KEY_HOUSEHOLD_ID) else editor.putString(KEY_HOUSEHOLD_ID, id)
        // 가정이 바뀌면 코드와 «가정 문서에 설정이 있다»는 기억도 앞 가정 것이다.
        if (id != householdId(context)) editor.remove(KEY_CODE).remove(KEY_MISSING)
        editor.apply()
    }

    /** 가정 코드 사본. 설정 화면에서 다시 보내 주려고 둔다. */
    fun code(context: Context): String? = prefs(context).getString(KEY_CODE, null)

    fun setCode(context: Context, code: String?) {
        val editor = prefs(context).edit()
        if (code == null) editor.remove(KEY_CODE) else editor.putString(KEY_CODE, code)
        editor.apply()
    }

    /**
     * 가정 문서를 읽어 봤더니 같이 쓰는 설정이 **없었다**. 이때만 이 폰이 먼저 채운다
     * ([HouseholdSettings.shouldPush]). 한 번도 못 읽었으면(오프라인 등) false 다 — 모르는 채로
     * 올리면 상대가 이미 올린 값을 덮을 수 있다.
     */
    fun isSettingsMissing(context: Context): Boolean = prefs(context).getBoolean(KEY_MISSING, false)

    fun setSettingsMissing(context: Context, missing: Boolean) {
        prefs(context).edit().putBoolean(KEY_MISSING, missing).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
