package com.calc.expense

import org.junit.Assert.assertEquals
import org.junit.Test

class SpendingGradeTest {

    @Test
    fun `예산이 있고 기록도 있으면 등급이 나온다`() {
        val result: SpendingGrade = SpendingGrading.of(recorded = true, spent = 5_000L, budget = 10_000L)
        assertEquals(SpendingGrade.Graded(Grade.S, 5_000L, 10_000L), result)
    }

    @Test
    fun `기록이 없으면 지출이 0이어도 기록없음이다`() {
        val result: SpendingGrade = SpendingGrading.of(recorded = false, spent = 0L, budget = 10_000L)
        assertEquals(SpendingGrade.NoRecord, result)
    }

    @Test
    fun `예산이 없으면 기록이 있어도 채점하지 않는다`() {
        val result: SpendingGrade = SpendingGrading.of(recorded = true, spent = 5_000L, budget = 0L)
        assertEquals(SpendingGrade.NoBudget, result)
    }

    @Test
    fun `예산이 없으면 기록없음보다 우선한다`() {
        // 곳간 자체가 없는 상태가 "오늘 안 적었다" 보다 더 근본적인 사정이다.
        val result: SpendingGrade = SpendingGrading.of(recorded = false, spent = 0L, budget = 0L)
        assertEquals(SpendingGrade.NoBudget, result)
    }
}
