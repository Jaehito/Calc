package com.calc.expense

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * 결제가 감지되면 [ReminderPolicy.WAIT_MINUTES] 분 뒤 확인을 예약한다.
 *
 * 재알림 없음·연쇄 방지를 위해 «대기 중인 결제»가 이미 있으면 새로 예약하지 않는다 —
 * 결제와 승인 알림이 잇따라 와도 확인은 한 번만 잡힌다. 확인이 끝나면 대기가 풀려
 * 다음 결제부터 다시 예약된다.
 */
object ReminderScheduler {

    private const val WORK_NAME = "payment_reminder"

    /** 결제 알림이 감지됐을 때 [PaymentNotificationListener] 가 부른다. */
    fun onPaymentDetected(context: Context) {
        if (!ReminderState.isEnabled(context)) return
        if (!NotificationState.isOn(context)) return

        val now: Long = System.currentTimeMillis()
        // 이미 대기 중이면(신선한 것) 두 번째 결제는 무시한다 — 확인은 한 번만.
        if (ReminderState.hasFreshPending(context, now)) return

        ReminderState.setPending(context, now)
        val request = OneTimeWorkRequestBuilder<PaymentReminderWorker>()
            .setInitialDelay(ReminderPolicy.WAIT_MINUTES, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        ReminderState.clearPending(context)
    }
}
