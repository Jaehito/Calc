package com.calc.expense

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * 잠금화면 카드 켜고 끄기. 첫 시작의 «알림 켜기»와 설정의 스위치가 같은 길을 쓴다 —
 * 한쪽에만 예약을 넣으면 다른 쪽으로 켠 사람은 저녁 등급·주간 돌아보기를 못 받는다.
 *
 * 권한 요청은 Activity 만 할 수 있어 여기서는 «필요한가»만 답한다([needsPermission]).
 */
object LockCard {

    /** 안드로이드 13 이상에서 알림 권한이 아직 없는가. 있으면 바로 [enable] 해도 된다. */
    fun needsPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED

    /** 카드를 띄우고, 카드를 따라다니는 예약(주간 돌아보기·저녁 등급·카드 다시 올리기)을 건다. */
    fun enable(context: Context) {
        NotificationState.setOn(context, true)
        NotificationHelper.show(context)
        WeeklyReviewScheduler.schedule(context)
        GradeScheduler.schedule(context)
        CardRefreshScheduler.schedule(context)
    }

    fun disable(context: Context) {
        // 먼저 꺼야 DismissReceiver 가 되살리지 않는다.
        NotificationState.setOn(context, false)
        NotificationHelper.hide(context)
        WeeklyReviewScheduler.cancel(context)
        GradeScheduler.cancel(context)
        CardRefreshScheduler.cancel(context)
    }
}
