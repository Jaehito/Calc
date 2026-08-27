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
        if (SettingsStore.load(app).isComplete) {
            NotificationHelper.show(app)
        }
    }
}
