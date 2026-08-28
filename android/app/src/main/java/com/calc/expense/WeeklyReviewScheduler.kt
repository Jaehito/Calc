package com.calc.expense

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * 주 1회 돌아보기 알림 예약.
 *
 * WorkManager 의 주기 작업은 요일·시각을 정확히 잡기 어렵다. 그래서 «다음 일요일 저녁 8시»
 * 까지의 지연을 계산해 1회성 작업으로 예약하고, [WeeklyReviewWorker] 가 끝날 때 다음 주를
 * 다시 예약한다. 예약은 재부팅에도 살아남는다.
 */
object WeeklyReviewScheduler {

    const val WORK_NAME = "weekly_review"

    /** 다음 돌아보기를 예약한다. 이미 예약돼 있으면 다음 시각으로 바꾼다. */
    fun schedule(context: Context) {
        val delay: Duration = WeeklyReviewTime.untilNextReview(LocalDateTime.now())
        val request = OneTimeWorkRequestBuilder<WeeklyReviewWorker>()
            .setInitialDelay(delay.toMinutes(), TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
