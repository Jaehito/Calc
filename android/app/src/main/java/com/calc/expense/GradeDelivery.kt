package com.calc.expense

/** 채점을 어떤 기간으로 했나. 전달 규칙이 기간마다 다르다. */
enum class GradePeriod { DAILY, WEEKLY, CYCLE }

/**
 * 채점 결과를 사용자에게 **보낼지** 판정한다. 채점 자체는 [SpendingGrading] 가 하고,
 * 여기는 그 결과를 내밀지 말지만 정한다.
 *
 * **주기(월간)는 무조건 보내고, 일간·주간은 [MIN_SHARED] 이상일 때만 보낸다.**
 *
 * 진짜 평가는 주기 결산 하나다 — 예산이 한 주기 단위로 리셋되므로 성패가 갈리는 것도 거기다.
 * 일간·주간 등급은 그 사이의 격려일 뿐인데, 나쁜 등급까지 매일 들이밀면 격려가 아니라 잔소리가
 * 된다. 게다가 하루는 판단 단위로 너무 짧다 — 오래 쓸 물건을 산 날은 하루치를 크게 넘기지만
 * 그게 낭비라는 뜻은 아니다. 그런 날 «D» 를 보내면 억까가 되고, 앱을 안 열게 만든다.
 *
 * 그래서 좋을 때만 말한다. 나쁜 날은 조용히 넘어가되 [Budget] 의 곳간·하루치 계산은 그대로
 * 돌아가므로, 숫자를 숨기는 것이 아니라 **채점만 삼가는** 것이다. 사실은 홈·통계에 늘 있다.
 *
 * Android 에 의존하지 않아 단위 테스트로 고정한다.
 */
object GradeDelivery {

    /** 일간·주간에서 이 등급 이상만 보낸다. */
    val MIN_SHARED: Grade = Grade.B

    /**
     * [grade] 를 [period] 채점으로 내밀어도 되는가.
     *
     * 채점이 안 된 경우([SpendingGrade.NoRecord]·[SpendingGrade.NoBudget])는 어느 기간이든
     * 보내지 않는다 — 등급이 없으니 할 말도 없다. 예전에 일간 알림이 «오늘은 기록이 없어요»
     * 를 보내던 자리인데, 그건 채점이 아니라 잔소리다.
     */
    fun shouldSend(period: GradePeriod, grade: SpendingGrade): Boolean {
        if (grade !is SpendingGrade.Graded) return false
        return when (period) {
            GradePeriod.CYCLE -> true
            GradePeriod.DAILY, GradePeriod.WEEKLY -> isGoodEnough(grade.grade)
        }
    }

    /** [MIN_SHARED] 이상인가. [Grade] 는 좋은 것부터 선언돼 있어 ordinal 이 작을수록 좋다. */
    fun isGoodEnough(grade: Grade): Boolean = grade.ordinal <= MIN_SHARED.ordinal
}
