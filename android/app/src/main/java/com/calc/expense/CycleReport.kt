package com.calc.expense

/**
 * 한 주기가 끝났을 때의 리포트.
 *
 * 결산 팝업이 «얼마 썼고 등급이 뭔지»를 말한다면, 리포트는 그 다음 질문에 답한다 —
 * **«그래서 다음 달은 얼마로 잡아야 하나».** 그 답을 스스로 못 내는 이유가 고정비를
 * 모르는 것이라, 화면의 중심도 [candidates] 다.
 *
 * @param budget 그 주기에 잡았던 예산. 0 이면 예산 없이 지낸 주기다
 * @param prevSpent 그 앞 주기의 지출. 0 이면 견줄 앞 주기가 없다
 * @param plan 지금 저장돼 있는 고정비 계획. 선택한 후보는 여기에 더해진다
 */
data class CycleReport(
    val cycle: BudgetCycle,
    val spent: Long,
    val budget: Long,
    val prevSpent: Long,
    val categories: List<CategorySlice>,
    val candidates: List<FixedCostCandidate>,
    val plan: FixedCostPlan = FixedCostPlan(),
    val loading: Boolean = false,
    val error: String? = null,
) {
    /** 앞 주기보다 얼마나 더 썼나. 음수면 덜 썼다. */
    val diff: Long
        get() = spent - prevSpent

    /** 견줄 앞 주기가 있나. 설치 첫 달에 «0원 쓴 달»과 견주면 항상 «더 썼어요»가 된다. */
    val hasPrev: Boolean
        get() = prevSpent > 0L

    val hasBudget: Boolean
        get() = budget > 0L

    /** 예산 대비 남은 돈. 음수면 넘긴 것이다. */
    val left: Long
        get() = budget - spent

    /** 고정비를 찾아냈나. 없으면 화면이 «아직 못 찾았어요»를 말해야 한다. */
    val hasCandidates: Boolean
        get() = candidates.isNotEmpty()

    /** 고른 후보들을 지금 계획에 더한 새 계획. 저장은 부른 쪽이 한다. */
    fun planWith(selected: List<FixedCostCandidate>): FixedCostPlan =
        CycleReports.merge(plan, selected)

    /**
     * 고른 후보까지 반영한 다음 주기 챌린지 금액.
     * 월급을 모르면 0 이다 — 뺄 것이 있어도 뺄 대상이 없으면 아무 말도 못 한다.
     */
    fun recommendedWith(selected: List<FixedCostCandidate>): Long {
        val next: FixedCostPlan = planWith(selected)
        if (!next.canRecommend) return 0L
        return next.recommended
    }
}

/**
 * 리포트가 쓰는 순수 계산. Android 에 의존하지 않아 단위 테스트로 고정한다.
 */
object CycleReports {

    /**
     * 고른 후보를 고정비 계획에 더한다.
     *
     * 이미 같은 이름이 계획에 있으면 **덮어쓴다** — 보험료가 올라 다시 제안된 경우이고,
     * 그럴 때 두 줄이 되면 합계가 두 배가 되어 챌린지 금액이 그만큼 잘못 줄어든다.
     * 이름 비교는 [RecurringCosts.normalize] 를 쓴다(띄어쓰기만 무시).
     */
    fun merge(plan: FixedCostPlan, selected: List<FixedCostCandidate>): FixedCostPlan {
        if (selected.isEmpty()) return plan

        val byName = LinkedHashMap<String, FixedCostItem>()
        for (item in plan.items) byName[RecurringCosts.normalize(item.name)] = item
        for (candidate in selected) {
            val key: String = RecurringCosts.normalize(candidate.name)
            if (key.isEmpty()) continue
            byName[key] = FixedCostItem(name = candidate.name, amount = candidate.amount)
        }

        return FixedCostPlan(
            monthlyIncome = plan.monthlyIncome,
            items = FixedCosts.clean(byName.values.toList()),
        )
    }

    /** 제안한 것을 모두 받아들였을 때의 합계. 화면 맨 아래 «이만큼이 고정비예요»에 쓴다. */
    fun candidateTotal(candidates: List<FixedCostCandidate>): Long =
        candidates.sumOf { maxOf(0L, it.amount) }
}
