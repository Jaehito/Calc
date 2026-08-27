package com.calc.expense

/** 화면과 알림이 필요로 하는 오늘의 숫자 한 벌. */
data class LedgerSnapshot(
    val dailyRate: Long,
    val vault: Long,
    val todaySpent: Long,
    val monthSpent: Long,
    val monthlyBudget: Long,
) {
    /** 오늘 쓸 수 있는 돈. 오늘 안에서는 음수가 될 수 있고 그건 사실이므로 감추지 않는다. */
    val available: Long
        get() = dailyRate + vault - todaySpent
}
