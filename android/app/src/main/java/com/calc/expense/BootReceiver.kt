package com.calc.expense

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 재부팅하면 상시 알림이 사라지므로 다시 띄운다. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != "android.intent.action.QUICKBOOT_POWERON"
        ) return

        val app = context.applicationContext
        // 사용자가 앱에서 켜 둔 상태였을 때만 되살린다.
        if (NotificationState.isOn(app) && SettingsStore.load(app).isComplete) {
            NotificationHelper.show(app)
            WeeklyReviewScheduler.schedule(app)
        }
    }
}
