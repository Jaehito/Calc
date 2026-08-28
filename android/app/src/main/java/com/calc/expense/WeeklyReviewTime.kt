package com.calc.expense

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters

/**
 * 주간 돌아보기 시각 계산. Android·WorkManager 에 의존하지 않아 단위 테스트로 고정한다.
 *
 * 돌아보는 때: 일요일 저녁 8시. 요일·시각이 시간대에 걸쳐 흔들리지 않게 여기 한 곳에 둔다.
 */
object WeeklyReviewTime {

    val REVIEW_DAY: DayOfWeek = DayOfWeek.SUNDAY
    const val REVIEW_HOUR: Int = 20

    /** [from] 에서 다음 돌아보기 시각까지 남은 시간. 그 요일이어도 시각이 지났으면 다음 주로 민다. */
    fun untilNextReview(from: LocalDateTime): Duration {
        var next: LocalDateTime = from
            .with(TemporalAdjusters.nextOrSame(REVIEW_DAY))
            .withHour(REVIEW_HOUR)
            .withMinute(0)
            .withSecond(0)
            .withNano(0)
        if (!next.isAfter(from)) next = next.plusWeeks(1)
        return Duration.between(from, next)
    }
}
