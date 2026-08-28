package com.calc.expense

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.time.LocalDate
import java.time.LocalTime

/**
 * 결제 감지 [ReminderPolicy.WAIT_MINUTES] 분 뒤에 깨어나, 그 사이 기록이 없으면 «적었어?» 를
 * 한 번 띄운다. 규칙 판정은 [ReminderPolicy] 가 한다 — 여기서는 상태만 읽어 넘긴다.
 *
 * 어떤 경우든 마지막에 대기를 푼다. 무음·상한으로 건너뛰었더라도 다음 결제는 다시 잡히게.
 */
class PaymentReminderWorker(
    context: Context,
    params: WorkerParameters,
) : Worker(context, params) {

    override fun doWork(): Result {
        val app: Context = applicationContext
        try {
            if (!ReminderState.isEnabled(app) || !NotificationState.isOn(app)) return Result.success()

            val pendingAt: Long = ReminderState.pendingAt(app)
            if (pendingAt <= 0L) return Result.success()

            val today: LocalDate = LocalDate.now()
            val recordedAfter: Boolean = ReminderState.lastRecordAt(app) >= pendingAt
            val quiet: Boolean = ReminderPolicy.isQuietHour(LocalTime.now())
            val count: Int = ReminderState.todayCount(app, today)

            if (ReminderPolicy.shouldRemind(recordedAfter, quiet, count)) {
                NotificationHelper.showReminder(app)
                ReminderState.incrementCount(app, today)
            }
        } finally {
            ReminderState.clearPending(app)
        }
        return Result.success()
    }
}
