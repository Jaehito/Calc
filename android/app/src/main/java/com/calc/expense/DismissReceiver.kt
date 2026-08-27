package com.calc.expense

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 알림이 지워졌을 때 되살린다.
 *
 * 스와이프로 넘기거나 «지우기» 를 눌러도 이 리시버가 불린다. 앱에서 «알림 끄기» 를 누른
 * 경우에는 [NotificationState] 가 먼저 꺼지므로 되살리지 않는다.
 */
class DismissReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_DISMISSED = "com.calc.expense.ACTION_DISMISSED"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        if (!NotificationState.isOn(app)) return
        NotificationHelper.show(app)
    }
}
