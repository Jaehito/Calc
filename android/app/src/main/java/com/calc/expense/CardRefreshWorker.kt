package com.calc.expense

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.time.LocalTime

/**
 * 한 시간마다 깨어나 상시 카드를 조용히 다시 올린다.
 *
 * 새 알림을 만드는 게 아니라 **이미 떠 있는 같은 알림([NotificationHelper.NOTIF_ID])을
 * 다시 낸다.** 알림은 하나뿐이고 소리·진동·배너도 없다 — 바뀌는 것은 목록에서의 자리뿐이다.
 *
 * 알림을 꺼 뒀으면 올리지 않고, 밤이면 건너뛴다. 어느 쪽이든 다음 번은 반드시 다시
 * 예약한다 — 꺼 둔 채로 시간이 지나도 다시 켜면 되살아나게.
 */
class CardRefreshWorker(
    context: Context,
    params: WorkerParameters,
) : Worker(context, params) {

    override fun doWork(): Result {
        val app: Context = applicationContext
        try {
            if (NotificationState.isOn(app) && CardRefreshTime.canRefresh(LocalTime.now())) {
                NotificationHelper.show(app)
            }
        } finally {
            CardRefreshScheduler.schedule(app)
        }
        return Result.success()
    }
}
