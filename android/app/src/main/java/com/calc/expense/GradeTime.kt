package com.calc.expense

import java.time.Duration
import java.time.LocalDateTime

/**
 * 오늘 채점 알림 시각 계산. Android·WorkManager 에 의존하지 않아 단위 테스트로 고정한다.
 *
 * 매일 저녁 9시. [WeeklyReviewTime] 과 같은 모양(다음 시각까지의 지연)이다.
 */
object GradeTime {

    const val REVIEW_HOUR = 21

    /** [from] 에서 다음 저녁 9시까지 남은 시간. 오늘 9시가 이미 지났으면 내일로 민다. */
    fun untilNextReview(from: LocalDateTime): Duration {
        var next: LocalDateTime = from
            .withHour(REVIEW_HOUR)
            .withMinute(0)
            .withSecond(0)
            .withNano(0)
        if (!next.isAfter(from)) next = next.plusDays(1)
        return Duration.between(from, next)
    }
}
