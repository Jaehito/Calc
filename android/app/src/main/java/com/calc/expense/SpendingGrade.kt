package com.calc.expense

/**
 * 한 기간(하루·주·주기)의 채점 결과.
 *
 * [NoRecord] 는 그 기간에 기록이 하나도 없다는 뜻이다 — 지출 0원과는 다르다. 예산 계산은
 * 기록 없는 날을 지출 0원으로 본다([Budget.settle] — 의도된 것이다), 하지만 채점에서는
 * «안 썼다»와 «몰라서 못 쟀다»를 갈라야 한다. 후자는 회색으로 보여주고 평균에도 넣지 않는다.
 */
sealed class SpendingGrade {
    data class Graded(val grade: Grade, val spent: Long, val budget: Long) : SpendingGrade()
    object NoRecord : SpendingGrade()
    object NoBudget : SpendingGrade()
}

/**
 * [SpendingGrade] 를 만드는 규칙. 값을 어디서 모으는지는 모르고 판정만 한다 —
 * 그래서 Android 에 의존하지 않고 단위 테스트로 고정한다.
 */
object SpendingGrading {

    /**
     * @param recorded 이 기간에 기록이 하나라도 있었는가. 주·주기 채점처럼 항상 참인 값을
     *   넘겨도 된다 — 그러면 [SpendingGrade.NoRecord] 는 나오지 않는다.
     */
    fun of(recorded: Boolean, spent: Long, budget: Long): SpendingGrade {
        if (budget <= 0L) return SpendingGrade.NoBudget
        if (!recorded) return SpendingGrade.NoRecord
        val grade: Grade = Grading.of(spent, budget) ?: return SpendingGrade.NoBudget
        return SpendingGrade.Graded(grade, spent, budget)
    }
}
