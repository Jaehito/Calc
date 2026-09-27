package com.calc.expense

import android.content.Context
import android.content.res.Resources

/**
 * 언어 선택. null 이면 폰 언어를 따른다.
 *
 * 계정과 무관한 기기 설정이라 [SettingsStore] 와 다른 파일에 둔다 — 로그아웃으로 설정이 지워져도
 * 화면 언어가 한국어로 돌아가면 안 된다.
 */
object LanguageStore {

    private const val FILE = "expense_language"
    private const val KEY = "language"

    fun load(context: Context): Lang? =
        Lang.ofCode(context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, null))

    fun save(context: Context, lang: Lang?) {
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString(KEY, lang?.code)
            .apply()
        apply(context)
    }

    /** 저장된 선택(없으면 폰 언어)을 [L10n] 에 넣는다. */
    fun apply(context: Context) {
        L10n.lang = load(context) ?: Lang.fromSystem(Resources.getSystem().configuration.locales[0]?.language)
    }
}
