package com.calc.expense

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.LocalDateTime

class WeeklyReviewTimeTest {

    // 2026-08-28 은 금요일. 다음 일요일은 8/30.
    @Test
    fun `주중이면 다음 일요일 저녁 8시까지 기다린다`() {
        val friday = LocalDateTime.of(2026, 8, 28, 9, 0)
        val delay: Duration = WeeklyReviewTime.untilNextReview(friday)
        // 금 09:00 → 일 20:00 = 2일 11시간
        assertEquals(Duration.ofDays(2).plusHours(11), delay)
    }

    @Test
    fun `일요일 낮이면 그날 저녁 8시다`() {
        val sundayNoon = LocalDateTime.of(2026, 8, 30, 12, 0)
        assertEquals(Duration.ofHours(8), WeeklyReviewTime.untilNextReview(sundayNoon))
    }

    @Test
    fun `일요일 저녁 8시가 지났으면 다음 주 일요일이다`() {
        val sundayNight = LocalDateTime.of(2026, 8, 30, 20, 30)
        // 30분 지남 → 다음 일요일(9/6) 20:00 까지 = 6일 23시간 30분
        assertEquals(
            Duration.ofDays(6).plusHours(23).plusMinutes(30),
            WeeklyReviewTime.untilNextReview(sundayNight),
        )
    }

    @Test
    fun `정확히 일요일 저녁 8시면 다음 주로 민다`() {
        val exactly = LocalDateTime.of(2026, 8, 30, 20, 0)
        assertEquals(Duration.ofDays(7), WeeklyReviewTime.untilNextReview(exactly))
    }
}
