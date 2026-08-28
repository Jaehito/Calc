package com.calc.expense

import android.content.Context

/**
 * 챌린지 로컬 기억. 참가 중인 방 id 와 내 표시 이름만 담는다.
 *
 * 비밀이 아니라(노션 토큰과 달리) 평문 SharedPreferences 로 충분하다. 방의 실제 데이터는
 * Firestore 에 있고 여기엔 "어느 방에 있나"만 둔다.
 */
object ChallengeStore {

    private const val FILE = "challenge_prefs"
    private const val KEY_ID = "challengeId"
    private const val KEY_NAME = "myName"

    /** 표시 이름 상한. 순위 줄에 들어가므로 너무 길면 잘린다. */
    const val MAX_NAME_LENGTH = 12

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** 참가 중인 방 id. 없으면 null. */
    fun challengeId(context: Context): String? =
        prefs(context).getString(KEY_ID, null)?.ifBlank { null }

    fun setChallengeId(context: Context, id: String?) {
        val edit = prefs(context).edit()
        if (id.isNullOrBlank()) edit.remove(KEY_ID) else edit.putString(KEY_ID, id)
        edit.apply()
    }

    /** 내 표시 이름. 아직 정하지 않았으면 빈 문자열. */
    fun myName(context: Context): String =
        prefs(context).getString(KEY_NAME, "").orEmpty()

    fun setMyName(context: Context, name: String) {
        prefs(context).edit()
            .putString(KEY_NAME, name.trim().take(MAX_NAME_LENGTH))
            .apply()
    }
}
