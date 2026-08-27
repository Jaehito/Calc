package com.calc.expense

import android.content.Context
import android.content.SharedPreferences
import java.time.LocalDate
import java.time.YearMonth

/**
 * 날짜별 지출 합계의 로컬 사본.
 *
 * 잠금화면에서 기록할 때 Notion 을 한 번 더 왕복할 수 없어서 존재한다. 브로드캐스트 수명이
 * 약 10초라 쓰기 한 번이면 이미 빠듯하다. 그래서 기록이 성공하면 여기에 바로 더해
 * "오늘 쓸 수 있는 돈"을 즉시 계산하고, Notion 은 앱을 열었을 때 다시 맞춘다.
 *
 * 진실의 출처는 언제나 Notion 이고 이 값은 파생 데이터다. 지워도 [replaceMonth] 로 복구된다.
 * 그래서 암호화 저장소를 쓰지 않는다 — 키스토어 실패라는 고장 지점을 하나 더 만들 이유가 없다.
 */
object SpendingCache {

    private const val FILE = "expense_cache"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private fun key(purse: Purse, month: YearMonth): String = "${purse.key}.$month"

    /** 그 곳간, 그 달의 날짜별 합계. 없으면 빈 맵. */
    fun totals(context: Context, purse: Purse, month: YearMonth): Map<LocalDate, Long> =
        MonthTotals.decode(prefs(context).getString(key(purse, month), null))

    /** 그 곳간, 그 날의 지출 합계. 기록이 없으면 0. */
    fun spentOn(context: Context, purse: Purse, day: LocalDate): Long =
        totals(context, purse, YearMonth.from(day))[day] ?: 0L

    /** 기록 한 건을 더한다. Notion 쓰기가 성공한 뒤에만 부른다. */
    fun add(context: Context, purse: Purse, day: LocalDate, amount: Long) {
        val month = YearMonth.from(day)
        val updated = LinkedHashMap(totals(context, purse, month))
        updated[day] = (updated[day] ?: 0L) + amount
        prefs(context).edit().putString(key(purse, month), MonthTotals.encode(updated)).apply()
    }

    /** Notion 조회 결과로 그 달을 통째로 교체한다. 다른 기기에서 고친 것도 이때 반영된다. */
    fun replaceMonth(context: Context, purse: Purse, month: YearMonth, totals: Map<LocalDate, Long>) {
        prefs(context).edit().putString(key(purse, month), MonthTotals.encode(totals)).apply()
    }
}
