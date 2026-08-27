package com.calc.expense

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ToneTest {

    /** 하루치 30,000 + 곳간 23,400 − 오늘 12,400 = 41,000 남음. */
    private val remaining = LedgerSnapshot(
        purse = Purse.PERSONAL,
        label = "개인",
        dailyRate = 30_000L,
        vault = 23_400L,
        todaySpent = 12_400L,
        cycleSpent = 387_600L,
        monthlyBudget = 930_000L,
        targetDay = LocalDate.of(2026, 9, 24),
        daysLeft = 22,
    )

    @Test
    fun `돈이 남아 있으면 초록이다`() {
        assertEquals(Tone.REMAINING, Tone.of(ok = true, snapshot = remaining))
    }

    @Test
    fun `지출이 더 크면 빨강이다`() {
        val over = remaining.copy(vault = 0L, todaySpent = 34_200L)

        assertEquals(-4_200L, over.available)
        assertEquals(Tone.OVER, Tone.of(ok = true, snapshot = over))
    }

    @Test
    fun `딱 맞게 썼으면 아직 넘긴 것이 아니다`() {
        val exact = remaining.copy(vault = 0L, todaySpent = 30_000L)

        assertEquals(0L, exact.available)
        assertEquals(Tone.REMAINING, Tone.of(ok = true, snapshot = exact))
    }

    @Test
    fun `기록이 안 됐으면 예산과 무관하게 실패다`() {
        // 남아 있는 상태라도 Notion 에 안 들어간 건을 두고 남았다고 말하면 거짓말이다
        assertEquals(Tone.FAILED, Tone.of(ok = false, snapshot = remaining))
        assertEquals(Tone.FAILED, Tone.of(ok = false, snapshot = null))
    }

    @Test
    fun `예산이 없으면 판단하지 않는다`() {
        assertEquals(Tone.NEUTRAL, Tone.of(ok = true, snapshot = null))
    }
}
