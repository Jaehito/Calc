package com.calc.expense

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * 상시 카드를 한 시간에 한 번 다시 올리는 예약.
 *
 * [WeeklyReviewScheduler]·[GradeScheduler] 와 같은 모양이다 — 다음 시각까지의 지연을
 * [CardRefreshTime] 이 계산하고, 1회성 작업으로 걸고, [CardRefreshWorker] 가 끝날 때
 * 다음 번을 다시 예약한다. WorkManager 의 주기 작업을 쓰지 않는 이유는 밤을 건너뛰는
 * 규칙을 그쪽으로는 표현할 수 없기 때문이다.
 */
object CardRefreshScheduler {

    const val WORK_NAME = "card_refresh"

    fun schedule(context: Context) {
        val delay: Duration = CardRefreshTime.untilNextRefresh(LocalDateTime.now())
        val request = OneTimeWorkRequestBuilder<CardRefreshWorker>()
            .setInitialDelay(delay.toMinutes(), TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
