package com.calc.expense

/** Notion 연결 정보와 곳간 예산. 저장 방식과 분리해 두어 Android 없이도 다룰 수 있다. */
data class Settings(
    val token: String = "",
    val databaseId: String = "",
    val nameProp: String = "이름",
    val priceProp: String = "금액",
    val dateProp: String = "날짜",
    /** 이 곳간의 월 예산. 0 이면 아직 정하지 않은 것이고, 그러면 곳간이 작동하지 않는다. */
    val monthlyBudget: Long = 0L,
) {
    val isComplete: Boolean
        get() = token.isNotBlank() && databaseId.isNotBlank() &&
            nameProp.isNotBlank() && priceProp.isNotBlank() && dateProp.isNotBlank()

    val hasBudget: Boolean
        get() = monthlyBudget > 0L
}
