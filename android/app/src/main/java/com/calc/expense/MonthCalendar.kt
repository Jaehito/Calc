package com.calc.expense

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/** 달력 한 칸. [day] 가 null 이면 그 달 앞뒤를 채우는 빈칸이다. */
data class CalendarCell(
    val day: LocalDate?,
    val spent: Long = 0L,
    /** 그날 하루치를 넘겼는지. 하루치를 정하지 않았으면 늘 false. */
    val over: Boolean = false,
    val isToday: Boolean = false,
    val isFuture: Boolean = false,
)

/**
 * 통계 달력의 칸들. 한 줄이 한 주(7칸)이고, 1일 앞과 말일 뒤는 빈칸으로 채운다.
 *
 * Android 에 의존하지 않아 단위 테스트로 고정한다.
 */
object MonthCalendar {

    /**
     * @param totals 그 달 날짜별 지출 합계([SpendingCache.totals] 와 같은 모양)
     * @param dailyTarget 그날의 하루치. 0 이면 넘김을 따지지 않는다
     * @param firstDay 한 주의 첫 요일(한국어·영어는 일요일, 스페인어는 월요일)
     */
    fun cells(
        month: YearMonth,
        totals: Map<LocalDate, Long>,
        dailyTarget: (LocalDate) -> Long,
        today: LocalDate,
        firstDay: DayOfWeek,
    ): List<CalendarCell> {
        val out = ArrayList<CalendarCell>(42)
        val lead: Int = (month.atDay(1).dayOfWeek.value - firstDay.value + 7) % 7
        repeat(lead) { out.add(CalendarCell(day = null)) }
        for (d in 1..month.lengthOfMonth()) {
            val day: LocalDate = month.atDay(d)
            val spent: Long = totals[day] ?: 0L
            val target: Long = dailyTarget(day)
            out.add(
                CalendarCell(
                    day = day,
                    spent = spent,
                    over = target > 0L && spent > target,
                    isToday = day == today,
                    isFuture = day.isAfter(today),
                ),
            )
        }
        while (out.size % 7 != 0) out.add(CalendarCell(day = null))
        return out
    }

    /** 요일 머리줄 순서. [firstDay] 부터 7개. */
    fun weekdays(firstDay: DayOfWeek): List<DayOfWeek> = (0L until 7L).map { firstDay.plus(it) }
}
