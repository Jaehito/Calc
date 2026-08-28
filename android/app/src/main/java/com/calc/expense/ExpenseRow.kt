package com.calc.expense

import java.time.LocalDate

/** 지출 한 줄. 노션(또는 나중에 다른 DB) 한 행을 화면이 쓰기 좋은 형태로 옮긴 것. */
data class ExpenseRow(
    /** 노션 페이지 id. 나중에 이 줄만 지우거나 고칠 때 쓴다(지금은 표시만). */
    val id: String,
    val name: String,
    val amount: Long,
    val date: LocalDate,
    /** 카테고리 이름. 없으면 빈 문자열. */
    val category: String,
)

/** 하루치 묶음 — 그 날의 합계와 그 날의 줄들. */
data class DayGroup(
    val date: LocalDate,
    val total: Long,
    val rows: List<ExpenseRow>,
)

/**
 * 내역 화면이 쓰는 순수 묶기·합계. 저장소(노션/DB)와 무관하다 — 어디서 읽어 왔든 [ExpenseRow]
 * 목록만 받으면 된다. 그래서 나중에 저장소를 바꿔도 이 로직과 화면은 그대로 살아남는다.
 */
object ExpenseHistoryGrouping {

    /** 최신 날짜부터, 같은 날 안에서는 큰 금액부터. 날짜별 합계를 함께 담는다. */
    fun groupByDay(rows: List<ExpenseRow>): List<DayGroup> {
        val byDay = LinkedHashMap<LocalDate, MutableList<ExpenseRow>>()
        for (row in rows) byDay.getOrPut(row.date) { ArrayList() }.add(row)

        return byDay.entries
            .sortedByDescending { it.key }
            .map { (day, list) ->
                DayGroup(
                    date = day,
                    total = list.sumOf { it.amount },
                    rows = list.sortedByDescending { it.amount },
                )
            }
    }

    /** 전체 합계 — 내역 상단 «이 달 얼마»에 쓴다. */
    fun total(rows: List<ExpenseRow>): Long = rows.sumOf { it.amount }
}
