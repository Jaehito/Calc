package com.calc.expense

import android.content.Context

/**
 * 이름 기억 저장소. 규칙은 [NameMemories], 직렬화는 [NameMemoryCodec] 이 하고
 * 여기는 SharedPreferences 입출력만 한다.
 *
 * 폰 안에만 둔다 — 「내가 이 가게를 뭐라고 부르는가」는 내 기기의 습관이다
 * ([CategoryMemoryStore] 와 같은 판단). 계정이 바뀌면 [AccountScope] 가 지운다.
 */
object NameMemoryStore {

    private const val FILE = "name_memory"
    private const val KEY = "map"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun load(context: Context): Map<String, String> =
        NameMemoryCodec.decode(prefs(context).getString(KEY, null))

    /** 기록이 저장된 뒤에 부른다. 실패한 기록으로 배우면 안 된다. */
    fun remember(context: Context, from: String, to: String) {
        if (from.isBlank() || to.isBlank()) return
        val next: Map<String, String> = NameMemories.put(load(context), from, to)
        prefs(context).edit().putString(KEY, NameMemoryCodec.encode(next)).apply()
    }

    /** 알림이 읽은 이름을 대신할 이름. 없으면 null — 그러면 읽은 그대로 쓴다. */
    fun recall(context: Context, parsed: String): String? =
        NameMemories.lookup(load(context), parsed)

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }
}
