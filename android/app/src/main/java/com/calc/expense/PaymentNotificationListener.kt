package com.calc.expense

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * 기기에 올라오는 알림을 읽어 «결제»로 보이면 리마인더를 예약한다.
 *
 * **금액을 저장하거나 어디로 보내지 않는다.** 결제 여부만 판단하고 버린다. 이 서비스는
 * 사용자가 설정에서 «알림 접근»을 직접 허용해야만 동작한다 (아무 앱이나 켤 수 없는 권한).
 *
 * 자기 자신의 알림(입력·돌아보기·리마인더)은 건너뛴다 — 자기 알림에 자기가 반응하면 안 된다.
 */
class PaymentNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        if (sbn.packageName == packageName) return

        val enabled: Boolean = ReminderState.isEnabled(this) && NotificationState.isOn(this)
        if (!enabled) return

        val extras = sbn.notification?.extras ?: return
        val title: String? = extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString()
        val text: String? = extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString()

        if (PaymentDetector.isPayment(title, text)) {
            ReminderScheduler.onPaymentDetected(applicationContext)
        }
    }
}
