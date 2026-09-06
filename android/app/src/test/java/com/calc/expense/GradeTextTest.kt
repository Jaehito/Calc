package com.calc.expense

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GradeTextTest {

    @Test
    fun `등급이 있으면 등급 문자와 금액을 담는다`() {
        val lines: StatusLines = GradeText.daily(SpendingGrade.Graded(Grade.S, 4_000L, 10_000L))
        assertTrue(lines.summary.contains("S"))
        assertTrue(lines.summary.contains("4,000"))
        assertTrue(lines.detail.contains("10,000"))
    }

    @Test
    fun `기록없음은 채점 불가를 말한다`() {
        val lines: StatusLines = GradeText.daily(SpendingGrade.NoRecord)
        assertEquals(lines.summary, lines.detail)
        assertTrue(lines.summary.contains("기록"))
    }

    @Test
    fun `예산없음은 등급 대신 안내한다`() {
        val lines: StatusLines = GradeText.daily(SpendingGrade.NoBudget)
        assertTrue(lines.summary.contains("예산"))
    }

    @Test
    fun `기록없음 예산없음은 주간 한 줄을 만들지 않는다`() {
        assertNull(GradeText.trailing(SpendingGrade.NoRecord))
        assertNull(GradeText.trailing(SpendingGrade.NoBudget))
        assertEquals("지난 7일 등급 A", GradeText.trailing(SpendingGrade.Graded(Grade.A, 7_000L, 10_000L)))
    }

    @Test
    fun `주기 결산 한 줄에 등급과 금액이 담긴다`() {
        val line: String? = GradeText.cycle(SpendingGrade.Graded(Grade.B, 900_000L, 930_000L))
        assertTrue(line!!.contains("B"))
        assertTrue(line.contains("900,000"))
    }
}
