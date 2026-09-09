package com.calc.expense

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.time.LocalDate

/**
 * 예약된 저녁 9시에 깨어나 오늘 등급 알림을 띄우고, 내일을 다시 예약한다.
 *
 * **등급이 나쁘면 알림을 띄우지 않는다** ([GradeDelivery]). 예약은 그래도 다시 건다.
 *
 * [WeeklyReviewWorker] 와 같은 모양이다. 로컬 캐시만 읽어 알림을 만든다 — 네트워크를 타지
 * 않는다. 앱 알림이 꺼져 있거나 설정이 비어 있으면 알림은 건너뛰되, 다음 날 예약은
 * 언제나 다시 건다(꺼 둔 날이 지나도 되살아나게).
 */
class GradeWorker(
    context: Context,
    params: WorkerParameters,
) : Worker(context, params) {

    override fun doWork(): Result {
        val app: Context = applicationContext
        try {
            if (NotificationState.isOn(app) && PurseAccess.isReady(app)) {
                val grade: SpendingGrade = GradeRepository.day(app, LocalDate.now())
                // B 이상일 때만 보낸다 — 나쁜 등급을 매일 들이밀면 격려가 잔소리가 된다.
                if (GradeDelivery.shouldSend(GradePeriod.DAILY, grade)) {
                    NotificationHelper.showGrade(app, GradeText.daily(grade))
                }
            }
        } finally {
            // 알림을 띄웠든 건너뛰었든 내일은 반드시 다시 예약한다.
            GradeScheduler.schedule(app)
        }
        return Result.success()
    }
}
