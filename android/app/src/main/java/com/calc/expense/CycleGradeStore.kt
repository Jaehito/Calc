package com.calc.expense

import android.content.Context
import java.time.LocalDate

/**
 * «지난 주기 결산»을 한 번만 보여주기 위한 기억. 마지막으로 확인한 주기의 시작일(월급날)을
 * 담는다 — 앱을 열 때 이 값과 지금 주기의 시작일이 다르면 주기가 바뀐 것이다.
 */
object CycleGradeStore {

    private const val FILE = "cycle_grade"
    private const val KEY_LAST_SEEN_START = "lastSeenStart"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun lastSeenStart(context: Context): LocalDate? =
        prefs(context).getString(KEY_LAST_SEEN_START, null)?.let {
            try {
                LocalDate.parse(it)
            } catch (_: Exception) {
                null
            }
        }

    fun setLastSeenStart(context: Context, start: LocalDate) {
        prefs(context).edit().putString(KEY_LAST_SEEN_START, start.toString()).apply()
    }
}
