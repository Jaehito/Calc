package com.calc.expense

import android.content.Context
import java.time.LocalDate

/**
 * «어제 등급» 팝업을 하루에 한 번만 보여주기 위한 기억. 마지막으로 보여준 **채점 대상 날짜**를
 * 담는다 — 앱을 열 때 이 값이 어제와 다르면 아직 안 보여준 것이다.
 *
 * [CycleGradeStore] 와 같은 모양이다. 다만 저장하는 값이 «주기 시작일»이 아니라 «채점한 날»이다.
 */
object DailyGradeStore {

    private const val FILE = "daily_grade"
    private const val KEY_LAST_SHOWN_DAY = "lastShownDay"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun lastShownDay(context: Context): LocalDate? =
        prefs(context).getString(KEY_LAST_SHOWN_DAY, null)?.let {
            try {
                LocalDate.parse(it)
            } catch (_: Exception) {
                null
            }
        }

    fun setLastShownDay(context: Context, day: LocalDate) {
        prefs(context).edit().putString(KEY_LAST_SHOWN_DAY, day.toString()).apply()
    }

    /** 계정이 바뀌면 «어느 날까지 봤나»도 남의 기억이다. */
    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }
}
