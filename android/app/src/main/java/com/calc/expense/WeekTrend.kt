package com.calc.expense

/**
 * 통계 «이번 주» 카드가 보여줄 것. 판정만 하고 그리지 않는다.
 *
 * @param left 목표까지 남은 돈. 넘겼으면 음수다
 * @param percent 목표 대비 사용률(반올림). 예산이 없으면 0
 * @param vsPrev 지난 7일과의 차이. 양수면 더 썼다. **견줄 기록이 없으면 null**
 */
data class WeekTrend(
    val spent: Long,
    val budget: Long,
    val hasBudget: Boolean,
    val left: Long,
    val percent: Int,
    val vsPrev: Long?,
)

/**
 * 주간 추이 판정.
 *
 * **목표가 1순위, 지난주는 보조다.** 예전에는 «지난 7일 대비»만 보여줬는데, 지난주 기록이
 * 없으면 0원 쓴 주로 취급돼 설치 첫 주에는 무조건 «이전 7일보다 더 썼어요»가 빨갛게 떴다.
 * 견줄 기록이 없는 것과 0원을 쓴 것은 다르다.
 *
 * 그 규칙은 이 앱에 이미 있다 — [Ledger.vsLastCycle] 은 지난 주기에 기록이 없으면 비교
 * 자체를 하지 않는다(null). 통계 화면만 그 규칙을 따르지 않아 여기서 맞춘다.
 *
 * 목표는 지난주가 없어도 언제나 있으므로, 목표 대비를 주로 삼으면 첫 주에도 화면이 비지 않는다.
 *
 * Android 에 의존하지 않아 단위 테스트로 고정한다.
 */
object WeekTrends {

    /**
     * @param spent 최근 7일 지출 합계
     * @param budget 최근 7일 목표 (하루치 합 × 7). 0 이하면 예산 미설정
     * @param prevSpent 그 이전 7일 지출 합계. **0 이면 «기록 없음»으로 보아 비교하지 않는다**
     */
    fun of(spent: Long, budget: Long, prevSpent: Long): WeekTrend {
        val hasBudget: Boolean = budget > 0L
        return WeekTrend(
            spent = spent,
            budget = budget,
            hasBudget = hasBudget,
            left = budget - spent,
            percent = if (hasBudget) Math.round(spent * 100.0 / budget).toInt() else 0,
            // Ledger.vsLastCycle 과 같은 규칙 — 견줄 기록이 없으면 비교하지 않는다.
            vsPrev = if (prevSpent > 0L) spent - prevSpent else null,
        )
    }
}
