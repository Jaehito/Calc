package com.calc.expense

/**
 * 곳간 하나의 설정. 사용자가 정하는 것만 담는다 — 이름과 예산.
 *
 * 저장소가 Firestore 로 바뀌면서 «DB 를 연결했나»는 사용자가 타이핑하는 값이 아니게 됐다.
 * 그 판정은 [PurseAccess] 가 한다(개인은 로그인, 공용은 가정에 묶였는지).
 */
data class PurseSettings(
    val monthlyBudget: Long = 0L,
    /** 사용자가 정한 이름. 비어 있으면 [Purse.defaultLabel] 을 쓴다. */
    val name: String = "",
) {
    /** 예산이 있어야 곳간 숫자가 나온다. 없으면 기록만 되고 «오늘 쓸 수 있는 돈»은 «—» 다. */
    val hasBudget: Boolean
        get() = monthlyBudget > 0L
}
