package com.calc.expense

import android.content.Context

/**
 * 3단계 읽기 전환 스위치. 기본은 꺼짐(지금처럼 노션만 읽음).
 *
 * 백필·이중 쓰기와 달리 이건 실패해도 조용히 넘어갈 수 없다 — 켜진 상태에서 Firestore
 * 데이터가 비어 있으면(백필 전이거나 규칙이 아직 안 걸렸으면) 홈 화면 «오늘 쓸 수 있는 돈»이
 * 실제보다 많아 보일 수 있다. 그래서 자동으로 켜지 않고, 사용자가 데이터를 확인한 뒤
 * 설정에서 직접 켜야 한다.
 */
object FirestoreReadMode {

    private const val PREFS = "firestore_read_mode"
    private const val KEY_ENABLED = "enabled"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
    }
}
