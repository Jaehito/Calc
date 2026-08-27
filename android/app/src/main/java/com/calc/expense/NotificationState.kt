package com.calc.expense

import android.content.Context

/**
 * 상시 알림을 사용자가 켜 뒀는지.
 *
 * 안드로이드 13 부터 `setOngoing(true)` 로는 스와이프도 «지우기» 도 막지 못한다.
 * 그래서 지워지면 다시 띄우는데, 그러려면 "실수로 지운 것"과 "앱에서 끈 것"을 구분해야 한다.
 * 이 플래그가 그 구분이다 — 켜져 있으면 되살리고, 꺼져 있으면 그대로 둔다.
 *
 * 지출 데이터가 아니라 앱 상태이므로 암호화 저장소를 쓰지 않는다.
 */
object NotificationState {

    private const val FILE = "expense_notification"
    private const val KEY_ON = "on"

    fun isOn(context: Context): Boolean =
        context.applicationContext
            .getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getBoolean(KEY_ON, false)

    fun setOn(context: Context, on: Boolean) {
        context.applicationContext
            .getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ON, on)
            .apply()
    }
}
