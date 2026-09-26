package com.calc.expense

import android.content.Context

/**
 * **결제 알림을 읽을지 말지** 하나만 담는다. 지출 데이터가 아니라 기기 설정이라
 * 암호화 저장소를 쓰지 않는다.
 *
 * 예전에는 «적었어?» 리마인더의 상태도 함께 들고 있었다 — 대기 중인 결제 시각, 마지막 기록
 * 시각, 오늘 보낸 횟수. 그 알림이 결제 배너로 바뀌면서 전부 쓸 데가 없어졌다. 배너는 결제를
 * 본 그 순간 뜨므로 «언제 물어볼지»를 기억할 이유가 없다.
 *
 * 계정이 바뀌어도 지우지 않는다. 이건 «이 폰에서 알림을 읽게 해 뒀나»이지 그 사람의 데이터가
 * 아니다 — 계정을 바꿨다고 알림 접근을 다시 켜게 만들 이유가 없다([AccountScope] 와 같은 판단).
 */
object ReminderState {

    private const val FILE = "expense_reminder"
    private const val KEY_ENABLED = "enabled"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, on).apply()
    }
}
