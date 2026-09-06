package com.calc.expense

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.time.LocalDate

/**
 * 예약된 시각에 깨어나 주간 돌아보기 알림을 띄우고, 다음 주를 다시 예약한다.
 *
 * 로컬 캐시만 읽어 알림을 만든다 — 네트워크를 타지 않는다. 앱 알림이 꺼져 있거나 설정이
 * 비어 있으면 알림은 건너뛰되, 다음 주 예약은 언제나 다시 건다(꺼 둔 주가 지나도 되살아나게).
 */
class WeeklyReviewWorker(
    context: Context,
    params: WorkerParameters,
) : Worker(context, params) {

    override fun doWork(): Result {
        val app: Context = applicationContext
        try {
            if (NotificationState.isOn(app) && SettingsStore.load(app).isComplete) {
                val base: StatusLines = StatusText.weekly(Ledger.weeklyTotals(app))
                // 지난 7일 등급을 한 줄 덧붙인다. 채점 대상이 아니면(곳간 없음) 조용히 생략한다.
                val gradeLine: String? = GradeText.trailing(GradeRepository.trailing(app, LocalDate.now(), 7))
                val lines: StatusLines = if (gradeLine == null) {
                    base
                } else {
                    StatusLines(
                        summary = "${base.summary} · $gradeLine",
                        detail = "${base.detail}\n\n$gradeLine",
                    )
                }
                NotificationHelper.showWeekly(app, lines)
            }
        } finally {
            // 알림을 띄웠든 건너뛰었든 다음 주는 반드시 다시 예약한다.
            WeeklyReviewScheduler.schedule(app)
        }
        return Result.success()
    }
}
