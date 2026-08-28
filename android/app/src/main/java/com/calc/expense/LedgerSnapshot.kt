package com.calc.expense

import java.time.LocalDate

/** 곳간 하나의 오늘 숫자 한 벌. */
data class LedgerSnapshot(
    val purse: Purse,
    /** 사용자가 정한 곳간 이름. 화면과 알림에 이대로 나온다. */
    val label: String,
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
    /**
     * 지난 주기 «이맘때»(같은 날짜 수)와 비교한 이번 주기 지출 차이.
     * 양수면 더 썼고 음수면 덜 썼다. 비교할 지난 주기 기록이 없으면 null.
     */
    val vsLastCycle: Long? = null,
) {
    /** 오늘 쓸 수 있는 돈. 오늘 안에서는 음수가 될 수 있고 그건 사실이므로 감추지 않는다. */
    val available: Long
        get() = dailyRate + vault - todaySpent

    /** 오늘 쓸 수 있는 돈을 넘겼는지. 색을 정하는 유일한 기준이다. */
    val isOver: Boolean
        get() = available < 0L

    /** 목표일까지 쓸 수 있는 돈. 곳간과 무관하게 예산에서 이번 주기 지출을 뺀 값이다. */
    val untilTarget: Long
        get() = monthlyBudget - cycleSpent

    /** 남은 날에 고르게 나눴을 때의 하루 몫. 지금 페이스가 되는지 보는 숫자다. */
    val perDayLeft: Long
        get() = if (daysLeft > 0) untilTarget / daysLeft else untilTarget
}
