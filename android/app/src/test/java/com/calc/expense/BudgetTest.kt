package com.calc.expense

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class BudgetTest {

    /** 8월 = 31일. 930,000 / 31 = 30,000 으로 딱 떨어져 계산을 눈으로 좇기 쉽다. */
    private val budget = 930_000L
    private val aug = YearMonth.of(2026, 8)

    private fun spent(vararg pairs: Pair<LocalDate, Long>): (LocalDate) -> Long {
        val map = pairs.toMap()
        return { day -> map[day] ?: 0L }
    }

    @Test
    fun `하루치는 월 예산을 그 달 일수로 나눈 값이다`() {
        assertEquals(30_000L, Budget.baseRate(budget, aug))
        // 2월은 28일이라 같은 예산이어도 하루치가 커진다
        assertEquals(33_214L, Budget.baseRate(930_000L, YearMonth.of(2026, 2)))
    }

    @Test
    fun `시작할 때는 곳간이 비어 있고 과거를 소급하지 않는다`() {
        val s = Budget.start(budget, LocalDate.of(2026, 8, 27))
        assertEquals(0L, s.vault)
        assertEquals(30_000L, s.dailyRate)
        assertEquals(LocalDate.of(2026, 8, 26), s.settledThrough)
    }

    @Test
    fun `아낀 만큼 곳간에 쌓이고 오늘 쓸 수 있는 돈이 늘어난다`() {
        // 8/25 에 시작 → 8/25 20,000 / 8/26 15,000 을 쓰고 8/27 을 맞는다
        val start = Budget.start(budget, LocalDate.of(2026, 8, 25))
        val settled = Budget.settle(
            start,
            LocalDate.of(2026, 8, 27),
            spent(
                LocalDate.of(2026, 8, 25) to 20_000L,
                LocalDate.of(2026, 8, 26) to 15_000L,
            ),
        )

        // (30,000-20,000) + (30,000-15,000) = 25,000
        assertEquals(25_000L, settled.vault)
        assertEquals(30_000L, settled.dailyRate)
        // 오늘 아직 안 썼으면 하루치 + 곳간
        assertEquals(55_000L, Budget.available(settled, 0L))
        // 커피 4,500 을 기록하면 그만큼 줄어든다
        assertEquals(50_500L, Budget.available(settled, 4_500L))
    }

    @Test
    fun `초과분은 곳간이 먼저 흡수한다`() {
        val start = Budget.start(budget, LocalDate.of(2026, 8, 25))
        val settled = Budget.settle(
            start,
            LocalDate.of(2026, 8, 27),
            spent(
                LocalDate.of(2026, 8, 25) to 10_000L, // +20,000 → 곳간 20,000
                LocalDate.of(2026, 8, 26) to 45_000L, // -15,000 → 곳간 5,000
            ),
        )

        assertEquals(5_000L, settled.vault)
        // 곳간이 다 흡수했으므로 하루치는 그대로다
        assertEquals(30_000L, settled.dailyRate)
    }

    @Test
    fun `곳간을 넘어선 초과분만 남은 날에 분산된다`() {
        // 8/26 에 시작해 곳간 23,400 을 만들어 둔 상태를 흉내내는 대신 직접 구성한다
        val state = BudgetState(
            monthlyBudget = budget,
            dailyRate = 30_000L,
            vault = 23_000L,
            settledThrough = LocalDate.of(2026, 8, 26),
        )

        // 8/27 에 90,000 을 쓰고 8/28 을 맞는다
        val settled = Budget.settle(
            state,
            LocalDate.of(2026, 8, 28),
            spent(LocalDate.of(2026, 8, 27) to 90_000L),
        )

        // 23,000 + (30,000 - 90,000) = -37,000 → 곳간 0, 모자란 37,000 을 8/28~8/31 (4일) 에 분산
        assertEquals(0L, settled.vault)
        assertEquals(30_000L - 9_250L, settled.dailyRate)
        // 다음 날 숫자가 음수로 남지 않는 것이 이 설계의 핵심이다
        assertEquals(20_750L, Budget.available(settled, 0L))
    }

    @Test
    fun `달이 바뀌면 하루치가 리셋되고 곳간에 상한이 걸린다`() {
        // 8/31 까지 정산된 상태에서 곳간이 상한을 넘게 쌓여 있다
        val state = BudgetState(
            monthlyBudget = budget,
            dailyRate = 22_000L, // 8월에 초과가 있어 내려가 있던 하루치
            vault = 400_000L,
            settledThrough = LocalDate.of(2026, 8, 31),
        )

        // 9월 = 30일 → 하루치 31,000, 상한 155,000
        val settled = Budget.settle(state, LocalDate.of(2026, 9, 1), spent())

        assertEquals(31_000L, settled.dailyRate)
        assertEquals(31_000L * Budget.VAULT_CAP_DAYS, settled.vault)
        assertEquals(155_000L, settled.vault)
    }

    @Test
    fun `상한보다 적게 쌓였으면 그대로 넘어간다`() {
        val state = BudgetState(
            monthlyBudget = budget,
            dailyRate = 30_000L,
            vault = 42_000L,
            settledThrough = LocalDate.of(2026, 8, 31),
        )

        val settled = Budget.settle(state, LocalDate.of(2026, 9, 1), spent())

        assertEquals(42_000L, settled.vault)
        assertEquals(31_000L, settled.dailyRate)
    }

    @Test
    fun `며칠 앱을 안 열어도 한 번에 정산된다`() {
        val start = Budget.start(budget, LocalDate.of(2026, 8, 20))
        val settled = Budget.settle(
            start,
            LocalDate.of(2026, 8, 25),
            spent(
                LocalDate.of(2026, 8, 20) to 30_000L, // ±0
                LocalDate.of(2026, 8, 21) to 10_000L, // +20,000
                LocalDate.of(2026, 8, 22) to 0L,      // +30,000  (기록 없는 날)
                LocalDate.of(2026, 8, 23) to 50_000L, // -20,000
                LocalDate.of(2026, 8, 24) to 20_000L, // +10,000
            ),
        )

        assertEquals(40_000L, settled.vault)
        assertEquals(LocalDate.of(2026, 8, 24), settled.settledThrough)
    }

    @Test
    fun `같은 날 두 번 정산해도 결과가 변하지 않는다`() {
        val start = Budget.start(budget, LocalDate.of(2026, 8, 25))
        val once = Budget.settle(
            start,
            LocalDate.of(2026, 8, 27),
            spent(LocalDate.of(2026, 8, 25) to 20_000L, LocalDate.of(2026, 8, 26) to 15_000L),
        )
        val twice = Budget.settle(once, LocalDate.of(2026, 8, 27)) { 0L }

        assertEquals(once, twice)
    }

    @Test
    fun `그 달 마지막 날의 초과분은 다음 달로 넘어가지 않는다`() {
        val state = BudgetState(
            monthlyBudget = budget,
            dailyRate = 30_000L,
            vault = 0L,
            settledThrough = LocalDate.of(2026, 8, 30),
        )

        // 8/31 에 100,000 초과 → 분산할 남은 날이 없다
        val settled = Budget.settle(
            state,
            LocalDate.of(2026, 9, 1),
            spent(LocalDate.of(2026, 8, 31) to 130_000L),
        )

        assertEquals(0L, settled.vault)
        assertEquals(31_000L, settled.dailyRate) // 9월 하루치로 온전히 리셋
    }

    @Test
    fun `예산을 바꾸면 하루치만 다시 계산되고 곳간은 유지된다`() {
        val state = BudgetState(
            monthlyBudget = budget,
            dailyRate = 30_000L,
            vault = 51_000L,
            settledThrough = LocalDate.of(2026, 8, 26),
        )

        val changed = Budget.updateBudget(state, 620_000L, LocalDate.of(2026, 8, 27))

        assertEquals(20_000L, changed.dailyRate)
        assertEquals(51_000L, changed.vault)
    }
}
