package com.calc.expense

import java.time.LocalDate

/** 곳간 하나의 오늘 숫자 한 벌. */
data class LedgerSnapshot(
    val purse: Purse,
    val dailyRate: Long,
    val vault: Long,
    val todaySpent: Long,
    /** 이번 주기(월급날→다음 월급날) 누적 지출. */
    val cycleSpent: Long,
    val monthlyBudget: Long,
    /** 다음 월급날 전날 — 사용자가 "목표일"로 보는 날. */
    val targetDay: LocalDate,
    /** 오늘을 포함해 목표일까지 남은 날 수. */
    val daysLeft: Int,
) {
    /** 오늘 쓸 수 있는 돈. 오늘 안에서는 음수가 될 수 있고 그건 사실이므로 감추지 않는다. */
    val available: Long
        get() = dailyRate + vault - todaySpent

    /** 목표일까지 쓸 수 있는 돈. 곳간과 무관하게 예산에서 이번 주기 지출을 뺀 값이다. */
    val untilTarget: Long
        get() = monthlyBudget - cycleSpent

    /** 남은 날에 고르게 나눴을 때의 하루 몫. 지금 페이스가 되는지 보는 숫자다. */
    val perDayLeft: Long
        get() = if (daysLeft > 0) untilTarget / daysLeft else untilTarget
}
