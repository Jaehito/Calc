package com.calc.expense

/** 지출이 예산 대비 어땠는지의 다섯 단계. S 가 가장 좋다. */
enum class Grade { S, A, B, C, D }

/**
 * 지출을 등급으로 매긴다. 예산 대비 비율로만 판단한다 — 최근 며칠 성적이나 곳간 잔액은
 * 보지 않는다. «이 기간에 얼마나 썼나» 딱 하나만 채점한다.
 *
 * 정수 연산만 쓴다(부동소수점 반올림 오차를 피하려고) — spent*100 과 budget*기준선을 비교한다.
 *
 * Android 에 의존하지 않아 단위 테스트로 고정한다.
 */
object Grading {

    /** 등급 상한(예산 대비 %). 이 이하로 썼으면 그 등급. */
    const val S_MAX_PERCENT = 50L
    const val A_MAX_PERCENT = 80L
    const val B_MAX_PERCENT = 100L
    const val C_MAX_PERCENT = 130L

    /** 예산이 0 이하면 매길 수 없다(null) — 그 곳간은 채점 대상이 아니다. */
    fun of(spent: Long, budget: Long): Grade? {
        if (budget <= 0L) return null
        // 이미 지운(unrecord) 뒤 순수출이 음수가 되는 경우를 대비해 0으로 바닥을 둔다.
        val amount: Long = maxOf(0L, spent)
        return when {
            amount * 100L <= budget * S_MAX_PERCENT -> Grade.S
            amount * 100L <= budget * A_MAX_PERCENT -> Grade.A
            amount * 100L <= budget * B_MAX_PERCENT -> Grade.B
            amount * 100L <= budget * C_MAX_PERCENT -> Grade.C
            else -> Grade.D
        }
    }
}
