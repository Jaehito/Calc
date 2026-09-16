package com.calc.expense

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 리포트의 계산. 여기서 틀리면 사용자가 «이대로 할래요»를 눌렀을 때 다음 달 예산이 조용히 어긋난다.
 */
class CycleReportTest {

    private val cycle: BudgetCycle =
        BudgetCycle(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 1))

    private fun candidate(name: String, amount: Long) =
        FixedCostCandidate(name = name, amount = amount, cycles = 2, fromRecord = false)

    private fun report(
        spent: Long = 900_000L,
        budget: Long = 1_000_000L,
        prevSpent: Long = 0L,
        candidates: List<FixedCostCandidate> = emptyList(),
        plan: FixedCostPlan = FixedCostPlan(),
    ) = CycleReport(
        cycle = cycle,
        spent = spent,
        budget = budget,
        prevSpent = prevSpent,
        categories = emptyList(),
        candidates = candidates,
        plan = plan,
    )

    @Test
    fun `앞 주기가 없으면 견주지 않는다`() {
        // 설치 첫 달에 «0원 쓴 달»과 견주면 언제나 «더 썼어요»가 된다.
        assertFalse(report(prevSpent = 0L).hasPrev)
        assertTrue(report(prevSpent = 800_000L).hasPrev)
    }

    @Test
    fun `앞 주기와의 차이를 낸다`() {
        assertEquals(100_000L, report(spent = 900_000L, prevSpent = 800_000L).diff)
        assertEquals(-50_000L, report(spent = 750_000L, prevSpent = 800_000L).diff)
    }

    @Test
    fun `예산에서 남은 돈을 낸다`() {
        assertEquals(100_000L, report(spent = 900_000L, budget = 1_000_000L).left)
        assertEquals(-200_000L, report(spent = 1_200_000L, budget = 1_000_000L).left)
    }

    @Test
    fun `고른 후보가 고정비에 더해진다`() {
        val plan = FixedCostPlan(monthlyIncome = 3_000_000L, items = listOf(FixedCostItem("월세", 550_000L)))
        val next: FixedCostPlan = report(plan = plan).planWith(listOf(candidate("KT 통신비", 55_000L)))

        assertEquals(listOf("월세", "KT 통신비"), next.items.map { it.name })
        assertEquals(605_000L, next.fixedTotal)
        assertEquals(3_000_000L, next.monthlyIncome)
    }

    @Test
    fun `이미 있는 이름은 덮어쓴다`() {
        // 보험료가 올라 다시 제안된 경우다. 두 줄이 되면 합계가 두 배가 되어
        // 챌린지 금액이 그만큼 잘못 줄어든다.
        val plan = FixedCostPlan(monthlyIncome = 3_000_000L, items = listOf(FixedCostItem("보험", 100_000L)))
        val next: FixedCostPlan = report(plan = plan).planWith(listOf(candidate("보험", 108_000L)))

        assertEquals(1, next.items.size)
        assertEquals(108_000L, next.fixedTotal)
    }

    @Test
    fun `띄어쓰기만 다른 이름도 같은 줄로 본다`() {
        val plan = FixedCostPlan(monthlyIncome = 3_000_000L, items = listOf(FixedCostItem("행복주택월세", 550_000L)))
        val next: FixedCostPlan = report(plan = plan).planWith(listOf(candidate("행복주택 월세", 560_000L)))

        assertEquals(1, next.items.size)
        assertEquals(560_000L, next.fixedTotal)
    }

    @Test
    fun `아무것도 안 고르면 계획이 그대로다`() {
        val plan = FixedCostPlan(monthlyIncome = 3_000_000L, items = listOf(FixedCostItem("월세", 550_000L)))

        assertEquals(plan, report(plan = plan).planWith(emptyList()))
    }

    @Test
    fun `고른 것까지 빼서 다음 챌린지 금액을 낸다`() {
        val plan = FixedCostPlan(monthlyIncome = 3_000_000L, items = listOf(FixedCostItem("월세", 550_000L)))
        val selected: List<FixedCostCandidate> = listOf(candidate("KT 통신비", 55_000L), candidate("보험", 62_000L))

        assertEquals(2_333_000L, report(plan = plan).recommendedWith(selected))
    }

    @Test
    fun `월급을 모르면 금액을 말하지 않는다`() {
        // 뺄 것을 찾아도 뺄 대상이 없으면 아무 말도 못 한다.
        val report: CycleReport = report(plan = FixedCostPlan(monthlyIncome = 0L))

        assertEquals(0L, report.recommendedWith(listOf(candidate("월세", 550_000L))))
    }

    @Test
    fun `고정비가 월급을 넘으면 금액을 말하지 않는다`() {
        // 음수를 예산에 넣으면 하루치가 음수가 되어 화면의 모든 숫자가 무너진다.
        val plan = FixedCostPlan(monthlyIncome = 1_000_000L, items = listOf(FixedCostItem("월세", 900_000L)))

        assertEquals(0L, report(plan = plan).recommendedWith(listOf(candidate("보험", 200_000L))))
    }

    @Test
    fun `제안한 것을 모두 받았을 때의 합계를 낸다`() {
        val candidates: List<FixedCostCandidate> = listOf(candidate("월세", 550_000L), candidate("보험", 62_000L))

        assertEquals(612_000L, CycleReports.candidateTotal(candidates))
    }

    @Test
    fun `찾은 게 없으면 화면이 알 수 있다`() {
        assertFalse(report().hasCandidates)
        assertTrue(report(candidates = listOf(candidate("월세", 550_000L))).hasCandidates)
    }
}
