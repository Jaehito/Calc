package com.calc.expense

import android.content.Context

/**
 * 카테고리 기억 저장소. 규칙은 [CategoryMemories], 직렬화는 [CategoryMemoryCodec] 이 하고
 * 여기는 SharedPreferences 입출력만 한다.
 *
 * 폰 안에만 둔다 — 저장소에 올려 봐야 다시 읽을 곳이 없고, «내가 무엇을 어디에 넣는가»는
 * 내 기기의 습관이다. 계정이 바뀌면 [AccountScope] 가 지운다.
 */
object CategoryMemoryStore {

    private const val FILE = "category_memory"
    private const val KEY = "map"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun load(context: Context): Map<String, String> =
        CategoryMemoryCodec.decode(prefs(context).getString(KEY, null))

    /** 기록이 저장된 뒤에 부른다. 실패한 기록으로 배우면 안 된다. */
    fun remember(context: Context, name: String, category: String) {
        if (name.isBlank() || category.isBlank()) return
        val next: Map<String, String> = CategoryMemories.put(load(context), name, category)
        prefs(context).edit().putString(KEY, CategoryMemoryCodec.encode(next)).apply()
    }

    /** 기억해 둔 카테고리. 없으면 null — 그러면 부른 쪽이 낱말 규칙으로 넘어간다. */
    fun recall(context: Context, name: String, categories: List<String>): String? =
        CategoryMemories.lookup(load(context), name, categories)

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }
}
