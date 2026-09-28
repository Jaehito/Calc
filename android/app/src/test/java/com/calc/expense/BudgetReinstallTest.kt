package com.calc.expense

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 재설치로 곳간 앵커가 사라졌는데 지출은 저장소에서 돌아온 경우.
 *
 * 실측: 월급날 15일, 이번 주기 예산의 대부분을 이미 썼고 남은 돈 약 6.6만 원·2주 남았는데,
 * 다시 설치하자 «오늘 6만 원» 이 떴다 — 오늘부터 예산 전액으로 다시 시작했기 때문이다.
 */
class BudgetReinstallTest {

    private val payDay = 15
    private val budget = 840_000L
    /** 9/15 ~ 10/14, 30일 주기. */
    private val today: LocalDate = LocalDate.of(2026, 9, 28)

    /** 9/15 ~ 9/27 동안 하루 59,538 씩, 합계 774,000 쯤 쓴 기록. */
    private val history: Map<LocalDate, Long> =
        (0L until 13L).associate { LocalDate.of(2026, 9, 15).plusDays(it) to 59_538L }

    private fun spentOn(day: LocalDate): Long = history[day] ?: 0L

    private val firstSpent: LocalDate = LocalDate.of(2026, 9, 15)

    @Test
    fun `앵커가 없어도 이번 주기 기록이 있으면 첫 기록 날부터 다시 접는다`() {
        val r = Budget.reckon(null, budget, today, payDay, ::spentOn, firstSpentDay = firstSpent)

        val left: Long = budget - history.values.sum()
        val daysLeft: Int = Payday.cycleOf(today, payDay).daysLeftFrom(today)
        val available: Long = Budget.available(r.today, todaySpent = 0L)

        // 남은 돈을 남은 날로 나눈 정도여야 한다. 예산 전액을 남은 날로 나눈 값(수만 원)이 아니다.
        assertEquals(LocalDate.of(2026, 9, 14), r.anchor.settledThrough)
        assertTrue("오늘 $available 원은 남은 $left 원 / $daysLeft 일보다 많을 수 없다", available <= left / daysLeft + 1)
        assertTrue(available >= 0L)
    }

    @Test
    fun `재설치 직후 오늘 기준으로 먼저 저장된 앵커도 기록을 받은 뒤 바로잡는다`() {
        // 기록을 받아 오기 전에 한 번 열려 오늘 기준 앵커가 저장된 상태.
        val premature: BudgetState = Budget.start(budget, today, payDay)

        val r = Budget.reckon(premature, budget, today, payDay, ::spentOn, firstSpentDay = firstSpent)

        assertEquals(LocalDate.of(2026, 9, 14), r.anchor.settledThrough)
        val left: Long = budget - history.values.sum()
        val daysLeft: Int = Payday.cycleOf(today, payDay).daysLeftFrom(today)
        assertTrue(Budget.available(r.today, 0L) <= left / daysLeft + 1)
    }

    @Test
    fun `이번 주기 기록이 없으면 예전처럼 오늘부터 시작한다`() {
        val r = Budget.reckon(null, budget, today, payDay, { 0L }, firstSpentDay = null)

        assertEquals(today.minusDays(1), r.anchor.settledThrough)
        assertEquals(budget / Payday.cycleOf(today, payDay).daysLeftFrom(today), r.today.dailyRate)
    }

    @Test
    fun `주기 첫날부터 쓰던 앵커는 건드리지 않는다`() {
        val anchor = BudgetState(
            monthlyBudget = budget,
            dailyRate = 28_000L,
            vault = 12_000L,
            settledThrough = LocalDate.of(2026, 9, 14),
        )

        val start: BudgetState = Budget.startingAnchor(anchor, budget, today, payDay, firstSpent)

        assertEquals(anchor, start)
    }

    @Test
    fun `지난 주기의 기록이나 오늘 기록은 첫 기록 날로 치지 않는다`() {
        val anchor: BudgetState = Budget.start(budget, today, payDay)

        assertEquals(anchor, Budget.startingAnchor(anchor, budget, today, payDay, LocalDate.of(2026, 9, 10)))
        assertEquals(anchor, Budget.startingAnchor(anchor, budget, today, payDay, today))
    }
}
