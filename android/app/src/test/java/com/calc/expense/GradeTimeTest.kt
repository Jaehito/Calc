package com.calc.expense

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class GradeTimeTest {

    @Test
    fun `9시 전이면 오늘 9시까지`() {
        val from = LocalDateTime.of(2026, 9, 6, 14, 30)
        val delay = GradeTime.untilNextReview(from)
        assertEquals(LocalDateTime.of(2026, 9, 6, 21, 0), from.plus(delay))
    }

    @Test
    fun `9시가 지났으면 내일 9시까지`() {
        val from = LocalDateTime.of(2026, 9, 6, 21, 30)
        val delay = GradeTime.untilNextReview(from)
        assertEquals(LocalDateTime.of(2026, 9, 7, 21, 0), from.plus(delay))
    }

    @Test
    fun `정확히 9시면 다음 날 9시까지 (같은 시각을 지난 것으로 본다)`() {
        val from = LocalDateTime.of(2026, 9, 6, 21, 0)
        val delay = GradeTime.untilNextReview(from)
        assertEquals(LocalDateTime.of(2026, 9, 7, 21, 0), from.plus(delay))
    }
}
