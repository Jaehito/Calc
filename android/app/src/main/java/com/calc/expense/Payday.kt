package com.calc.expense

import java.time.LocalDate
import java.time.YearMonth

/**
 * 예산 한 주기. [start] 부터 [endExclusive] 직전까지 — 월급날부터 다음 월급날 전날까지다.
 *
 * 길이가 28~31 일로 달마다 다르다. 그래서 하루치는 주기마다 다시 계산한다.
 */
data class BudgetCycle(val start: LocalDate, val endExclusive: LocalDate) {

    /** 이 주기의 날 수. */
    val length: Int
        get() = (endExclusive.toEpochDay() - start.toEpochDay()).toInt()

    /** 다음 월급날 전날 — 사용자가 "목표일"로 보는 날. */
    val lastDay: LocalDate
        get() = endExclusive.minusDays(1)

    fun contains(day: LocalDate): Boolean =
        !day.isBefore(start) && day.isBefore(endExclusive)

    /** [day] 를 포함해 이 주기에 남은 날 수. */
    fun daysLeftFrom(day: LocalDate): Int {
        if (day.isBefore(start)) return length
        val left: Long = endExclusive.toEpochDay() - day.toEpochDay()
        return if (left < 0L) 0 else left.toInt()
    }
}

/**
 * 월급날을 경계로 예산 주기를 자른다.
 *
 * 달력 1일이 아니라 월급날을 쓰는 이유는 실제 돈이 그렇게 들어오기 때문이다.
 * 25일에 받는데 1일에 예산이 리셋되면, 매달 24일까지는 지난달 돈으로 이번 달 예산을 쓰는 셈이 된다.
 */
object Payday {

    /** 달력 월과 같아지는 기본값. 예전 동작이 그대로 유지된다. */
    const val DEFAULT: Int = 1

    fun normalize(payDay: Int): Int = payDay.coerceIn(1, 31)

    /**
     * 그 달에 실제로 존재하는 월급날.
     * 31일로 지정해도 2월에는 28(29)일이 된다 — 없는 날짜를 만들지 않는다.
     */
    fun dayIn(year: Int, month: Int, payDay: Int): LocalDate {
        val ym: YearMonth = YearMonth.of(year, month)
        return ym.atDay(minOf(normalize(payDay), ym.lengthOfMonth()))
    }

    /** [day] 가 속한 주기. */
    fun cycleOf(day: LocalDate, payDay: Int): BudgetCycle {
        val p: Int = normalize(payDay)
        val thisMonth: LocalDate = dayIn(day.year, day.monthValue, p)

        return if (!day.isBefore(thisMonth)) {
            val next: LocalDate = day.plusMonths(1)
            BudgetCycle(thisMonth, dayIn(next.year, next.monthValue, p))
        } else {
            val prev: LocalDate = day.minusMonths(1)
            BudgetCycle(dayIn(prev.year, prev.monthValue, p), thisMonth)
        }
    }
}
