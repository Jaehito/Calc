package com.calc.expense

/** 곳간 하나의 설정. Notion DB 와 월 예산은 곳간마다 따로 둔다. */
data class PurseSettings(
    val databaseId: String = "",
    val monthlyBudget: Long = 0L,
) {
    /** DB 가 연결돼 있으면 이 곳간으로 기록할 수 있다. */
    val isLinked: Boolean
        get() = databaseId.isNotBlank()

    /** 예산까지 있어야 곳간이 작동한다. DB 만 있으면 기록은 되고 곳간 숫자는 안 나온다. */
    val isActive: Boolean
        get() = isLinked && monthlyBudget > 0L
}
