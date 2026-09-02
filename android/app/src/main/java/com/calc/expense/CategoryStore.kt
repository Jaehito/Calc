package com.calc.expense

import android.content.Context

/**
 * 사용자의 카테고리 칩 목록을 로컬에 담는다. 비밀이 아니라 평문 SharedPreferences 로 충분하다.
 *
 * 저장한 게 없으면 [Categories.DEFAULT] 를 준다 — 처음 쓰는 사람도 바로 칩이 보인다.
 */
object CategoryStore {

    private const val FILE = "category_prefs"
    private const val KEY = "list"

    /** 이름에 쓰이지 않는 제어문자(Unit Separator)로 항목을 잇는다. */
    private const val SEP = ""

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun load(context: Context): List<String> {
        val raw: String? = prefs(context).getString(KEY, null)
        if (raw.isNullOrEmpty()) return Categories.DEFAULT
        val list: List<String> = raw.split(SEP).filter { it.isNotBlank() }
        return list.ifEmpty { Categories.DEFAULT }
    }

    fun save(context: Context, list: List<String>) {
        prefs(context).edit().putString(KEY, list.joinToString(SEP)).apply()
    }
}
