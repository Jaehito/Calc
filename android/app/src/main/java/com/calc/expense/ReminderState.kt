package com.calc.expense

import android.content.Context
import java.time.LocalDate

/**
 * 결제 리마인더의 상태. 지출 데이터가 아니라 앱 상태라 암호화 저장소를 쓰지 않는다.
 *
 * 담는 것: 기능 켬/끔, 대기 중인 결제 감지 시각, 마지막 기록 시각, 오늘 보낸 리마인더 수.
 * 시각은 epoch millis 로 둔다 — 순수 규칙([ReminderPolicy])은 비교만 하므로 형식은 중요치 않다.
 */
object ReminderState {

    private const val FILE = "expense_reminder"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_PENDING_AT = "pendingAt"
    private const val KEY_LAST_RECORD_AT = "lastRecordAt"
    private const val KEY_COUNT_DATE = "countDate"
    private const val KEY_COUNT = "count"

    /** 결제 감지가 오래 밀려 있으면(예: 워커가 못 돈 채로) 새 결제를 다시 예약하도록 푸는 한도. */
    private const val STALE_PENDING_MS = 60L * 60L * 1000L

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, on).apply()
    }

    /** 대기 중인 결제 감지가 있는지 (오래된 것은 없는 것으로 본다). */
    fun hasFreshPending(context: Context, now: Long): Boolean {
        val at: Long = prefs(context).getLong(KEY_PENDING_AT, 0L)
        return at > 0L && now - at < STALE_PENDING_MS
    }

    fun pendingAt(context: Context): Long = prefs(context).getLong(KEY_PENDING_AT, 0L)

    fun setPending(context: Context, at: Long) {
        prefs(context).edit().putLong(KEY_PENDING_AT, at).apply()
    }

    fun clearPending(context: Context) {
        prefs(context).edit().putLong(KEY_PENDING_AT, 0L).apply()
    }

    fun lastRecordAt(context: Context): Long = prefs(context).getLong(KEY_LAST_RECORD_AT, 0L)

    /** 기록에 성공하면 부른다. 결제 감지 이후 기록이 있으면 리마인더를 보내지 않는다. */
    fun markRecorded(context: Context, at: Long) {
        prefs(context).edit().putLong(KEY_LAST_RECORD_AT, at).apply()
    }

    /** 오늘 보낸 리마인더 수. 날짜가 바뀌면 0 부터 다시 센다. */
    fun todayCount(context: Context, today: LocalDate): Int {
        val p = prefs(context)
        return if (p.getString(KEY_COUNT_DATE, "") == today.toString()) p.getInt(KEY_COUNT, 0) else 0
    }

    fun incrementCount(context: Context, today: LocalDate) {
        val p = prefs(context)
        val count: Int = if (p.getString(KEY_COUNT_DATE, "") == today.toString()) p.getInt(KEY_COUNT, 0) else 0
        p.edit().putString(KEY_COUNT_DATE, today.toString()).putInt(KEY_COUNT, count + 1).apply()
    }
}
