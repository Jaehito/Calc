package com.calc.expense

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 통계 «최근 7일» 판정. 설치 첫 주에 빈 지난주와 견주던 문제를 여기서 못 박는다.
 */
class WeekTrendTest {

    @Test
    fun `지난주 기록이 없으면 비교하지 않는다`() {
        // 설치 첫 주 — 예전에는 0원과 견줘 «168,000원 더 썼어요»가 빨갛게 떴다.
        val trend = WeekTrends.of(spent = 168_000L, budget = 210_000L, prevSpent = 0L)
        assertNull(trend.vsPrev)
    }

    @Test
    fun `지난주 기록이 있으면 차이를 낸다`() {
        val trend = WeekTrends.of(spent = 168_000L, budget = 210_000L, prevSpent = 180_000L)
        assertEquals(-12_000L, trend.vsPrev)
    }

    @Test
    fun `목표는 지난주가 없어도 언제나 있다`() {
        val trend = WeekTrends.of(spent = 168_000L, budget = 210_000L, prevSpent = 0L)
        assertTrue(trend.hasBudget)
        assertEquals(42_000L, trend.left)
        assertEquals(80, trend.percent)
    }

    @Test
    fun `목표를 넘기면 남은 돈이 음수다`() {
        val trend = WeekTrends.of(spent = 242_000L, budget = 210_000L, prevSpent = 0L)
        assertEquals(-32_000L, trend.left)
        assertEquals(115, trend.percent)
    }

    @Test
    fun `예산이 없으면 목표 대비를 말하지 않는다`() {
        val trend = WeekTrends.of(spent = 168_000L, budget = 0L, prevSpent = 0L)
        assertFalse(trend.hasBudget)
        assertEquals(0, trend.percent)
    }

    @Test
    fun `사용률은 반올림한다`() {
        // 100,000 중 33,333 → 33.333% → 33
        assertEquals(33, WeekTrends.of(33_333L, 100_000L, 0L).percent)
        // 100,000 중 33,335 → 33.335% → 33 (반올림 경계 아래)
        assertEquals(34, WeekTrends.of(33_500L, 100_000L, 0L).percent)
    }

    @Test
    fun `한 푼도 안 쓴 주도 지난주가 있으면 견준다`() {
        val trend = WeekTrends.of(spent = 0L, budget = 210_000L, prevSpent = 90_000L)
        assertEquals(-90_000L, trend.vsPrev)
        assertEquals(210_000L, trend.left)
    }
}
