package com.calc.expense

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * 저녁 9시 «오늘 등급» 알림 예약.
 *
 * [WeeklyReviewScheduler] 와 같은 모양이다 — WorkManager 의 주기 작업은 시각을 정확히
 * 잡기 어려워, «오늘(또는 내일) 저녁 9시»까지의 지연을 계산해 1회성 작업으로 걸고
 * [GradeWorker] 가 끝날 때 다음 날을 다시 예약한다. 예약은 재부팅에도 살아남는다.
 */
object GradeScheduler {

    const val WORK_NAME = "daily_grade"

    fun schedule(context: Context) {
        val delay: Duration = GradeTime.untilNextReview(LocalDateTime.now())
        val request = OneTimeWorkRequestBuilder<GradeWorker>()
            .setInitialDelay(delay.toMinutes(), TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
