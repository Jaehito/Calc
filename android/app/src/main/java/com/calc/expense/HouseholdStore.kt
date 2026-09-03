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

    fun householdId(context: Context): String? =
        prefs(context).getString(KEY_HOUSEHOLD_ID, null)

    fun setHouseholdId(context: Context, id: String?) {
        val editor = prefs(context).edit()
        if (id == null) editor.remove(KEY_HOUSEHOLD_ID) else editor.putString(KEY_HOUSEHOLD_ID, id)
        editor.apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
