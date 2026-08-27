package com.calc.expense

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.LocalDate

/**
 * 앵커(저장되는 값)와 오늘 상태(매번 다시 접는 값)의 분리를 고정한다.
 * 이 분리가 깨지면 Notion 재동기화로 고친 내역이 곳간에 반영되지 않는다.
 */
class BudgetReckonTest {

    private val budget = 930_000L // 8월 31일 → 하루치 30,000 / 9월 30일 → 31,000

    private fun spent(vararg pairs: Pair<LocalDate, Long>): (LocalDate) -> Long {
        val map = pairs.toMap()
        return { day -> map[day] ?: 0L }
    }

    @Test
    fun `저장된 앵커가 없으면 오늘부터 시작한다`() {
        val r = Budget.reckon(null, budget, LocalDate.of(2026, 8, 27), spent())

        assertEquals(0L, r.anchor.vault)
        assertEquals(LocalDate.of(2026, 8, 26), r.anchor.settledThrough)
        assertEquals(30_000L, r.today.dailyRate)
        assertEquals(0L, r.today.vault)
    }

    @Test
    fun `앵커는 그대로 두고 오늘 상태만 캐시에서 다시 접는다`() {
        val anchor = BudgetState(
            monthlyBudget = budget,
            dailyRate = 30_000L,
            vault = 0L,
            settledThrough = LocalDate.of(2026, 7, 31),
        )
        val today = LocalDate.of(2026, 8, 5)

        val before = Budget.reckon(
            anchor, budget, today,
            spent(LocalDate.of(2026, 8, 1) to 30_000L),
        )
        // Notion 재동기화로 8/1 지출이 10,000 으로 고쳐졌다
        val after = Budget.reckon(
            anchor, budget, today,
            spent(LocalDate.of(2026, 8, 1) to 10_000L),
        )

        // 앵커는 움직이지 않는다
        assertEquals(before.anchor, after.anchor)
        // 오늘 곳간은 고쳐진 값을 따라간다 — 이게 핵심이다
        assertNotEquals(before.today.vault, after.today.vault)
        assertEquals(90_000L, before.today.vault)  // 8/2~8/4 사흘치
        assertEquals(110_000L, after.today.vault)  // + 8/1 에 아낀 20,000
    }

    @Test
    fun `달이 바뀌면 앵커가 이번 달 기준으로 굳는다`() {
        val anchor = BudgetState(
            monthlyBudget = budget,
            dailyRate = 30_000L,
            vault = 20_000L,
            settledThrough = LocalDate.of(2026, 8, 20),
        )

        val r = Budget.reckon(
            anchor, budget, LocalDate.of(2026, 9, 3),
            spent(LocalDate.of(2026, 8, 25) to 60_000L),
        )

        // 8/21~8/31 을 정산하고 8월 말에서 멈춘다
        assertEquals(LocalDate.of(2026, 8, 31), r.anchor.settledThrough)
        // 9월 하루치로 리셋
        assertEquals(31_000L, r.today.dailyRate)
    }

    @Test
    fun `앵커가 굳은 뒤에는 지난 달 캐시가 없어도 결과가 같다`() {
        val anchor = BudgetState(
            monthlyBudget = budget,
            dailyRate = 30_000L,
            vault = 45_000L,
            settledThrough = LocalDate.of(2026, 8, 31),
        )
        val today = LocalDate.of(2026, 9, 3)

        val withAugust = Budget.reckon(
            anchor, budget, today,
            spent(LocalDate.of(2026, 8, 15) to 999_999L, LocalDate.of(2026, 9, 1) to 10_000L),
        )
        val withoutAugust = Budget.reckon(
            anchor, budget, today,
            spent(LocalDate.of(2026, 9, 1) to 10_000L),
        )

        // 8월 캐시가 사라져도 곳간이 흔들리지 않는다
        assertEquals(withAugust.today, withoutAugust.today)
        assertEquals(anchor, withAugust.anchor)
    }

    @Test
    fun `예산을 바꾸면 앵커의 하루치가 갱신된다`() {
        val anchor = BudgetState(
            monthlyBudget = budget,
            dailyRate = 30_000L,
            vault = 12_000L,
            settledThrough = LocalDate.of(2026, 8, 26),
        )

        val r = Budget.reckon(anchor, 620_000L, LocalDate.of(2026, 8, 27), spent())

        assertEquals(620_000L, r.anchor.monthlyBudget)
        assertEquals(20_000L, r.anchor.dailyRate)
        assertEquals(12_000L, r.anchor.vault) // 이미 쌓인 곳간은 건드리지 않는다
    }

    @Test
    fun `같은 입력을 두 번 넣어도 앵커가 흔들리지 않는다`() {
        val anchor = BudgetState(
            monthlyBudget = budget,
            dailyRate = 30_000L,
            vault = 0L,
            settledThrough = LocalDate.of(2026, 7, 31),
        )
        val cache = spent(LocalDate.of(2026, 8, 1) to 10_000L)
        val today = LocalDate.of(2026, 8, 5)

        val once = Budget.reckon(anchor, budget, today, cache)
        val twice = Budget.reckon(once.anchor, budget, today, cache)

        assertEquals(once.anchor, twice.anchor)
        assertEquals(once.today, twice.today)
    }
}
