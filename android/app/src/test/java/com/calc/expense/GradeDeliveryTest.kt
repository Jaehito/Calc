package com.calc.expense

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 채점을 내밀지 말지. **주기는 무조건, 일간·주간은 B 이상만.**
 *
 * 이 규칙이 흔들리면 나쁜 날마다 알림이 와서 앱을 안 열게 되므로 등급마다 못 박아 둔다.
 */
class GradeDeliveryTest {

    private fun graded(grade: Grade): SpendingGrade =
        SpendingGrade.Graded(grade, spent = 24_000L, budget = 30_000L)

    @Test
    fun `일간은 B 이상만 보낸다`() {
        assertTrue(GradeDelivery.shouldSend(GradePeriod.DAILY, graded(Grade.S)))
        assertTrue(GradeDelivery.shouldSend(GradePeriod.DAILY, graded(Grade.A)))
        assertTrue(GradeDelivery.shouldSend(GradePeriod.DAILY, graded(Grade.B)))
        assertFalse(GradeDelivery.shouldSend(GradePeriod.DAILY, graded(Grade.C)))
        assertFalse(GradeDelivery.shouldSend(GradePeriod.DAILY, graded(Grade.D)))
    }

    @Test
    fun `주간도 같은 기준이다`() {
        assertTrue(GradeDelivery.shouldSend(GradePeriod.WEEKLY, graded(Grade.B)))
        assertFalse(GradeDelivery.shouldSend(GradePeriod.WEEKLY, graded(Grade.C)))
    }

    @Test
    fun `주기는 나빠도 무조건 보낸다`() {
        // 진짜 평가는 주기 하나다. 나쁜 결산을 숨기면 그 다음 주기를 고칠 수가 없다.
        for (grade in Grade.entries) {
            assertTrue(
                "주기 결산은 $grade 도 보내야 한다",
                GradeDelivery.shouldSend(GradePeriod.CYCLE, graded(grade)),
            )
        }
    }

    @Test
    fun `채점이 안 됐으면 어느 기간이든 보내지 않는다`() {
        for (period in GradePeriod.entries) {
            assertFalse(GradeDelivery.shouldSend(period, SpendingGrade.NoRecord))
            assertFalse(GradeDelivery.shouldSend(period, SpendingGrade.NoBudget))
        }
    }

    @Test
    fun `등급은 좋은 것부터 선언돼 있다`() {
        // isGoodEnough 가 ordinal 비교에 기대므로 선언 순서가 바뀌면 규칙이 뒤집힌다.
        assertTrue(Grade.S.ordinal < Grade.A.ordinal)
        assertTrue(Grade.A.ordinal < Grade.B.ordinal)
        assertTrue(Grade.B.ordinal < Grade.C.ordinal)
        assertTrue(Grade.C.ordinal < Grade.D.ordinal)
    }
}
