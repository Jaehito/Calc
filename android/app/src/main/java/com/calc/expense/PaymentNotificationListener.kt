package com.calc.expense

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * 기기에 올라오는 알림을 읽어 «결제»로 보이면 두 가지를 한다.
 *
 * 1. 금액·가맹점을 짐작해 **수집함**([PendingPaymentStore])에 담는다 — 앱을 열면 «이거 기록할까요?»
 *    로 물어보기 위해서다. **자동으로 기록하지 않는다.** 파싱은 짐작이라 틀릴 수 있고, 틀린 금액이
 *    조용히 곳간을 갉아먹으면 숫자를 믿을 수 없게 된다. 사람이 확인해야 기록이 된다.
 * 2. 기존대로 «적었어?» 리마인더를 예약한다.
 *
 * 사용자가 설정에서 «알림 접근»을 직접 허용해야만 동작한다(아무 앱이나 켤 수 없는 권한).
 * 차단한 발신자·앱([PaymentBlocklist])은 수집하지 않는다.
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

        if (!PaymentDetector.isPayment(title, text)) return

        collect(sbn, title, text)
        ReminderScheduler.onPaymentDetected(applicationContext)
    }

    /**
     * 결제로 보이는 알림을 수집함에 담는다.
     *
     * 알림 키([StatusBarNotification.getKey])를 그대로 항목 id 로 쓴다 — 안드로이드가 같은 알림을
     * 갱신할 때마다 다시 올리는데, 이 키가 같으므로 덮어써서 중복이 쌓이지 않는다.
     */
    private fun collect(sbn: StatusBarNotification, title: String?, text: String?) {
        val app = applicationContext
        val sender: String = title.orEmpty()
        if (PaymentBlocklist.isBlocked(app, sbn.packageName, sender)) return

        val candidate: PaymentCandidate =
            PaymentParse.parse(title, text, CategoryStore.load(app)) ?: return

        PendingPaymentStore.add(
            app,
            PendingPayment(
                id = sbn.key,
                amount = candidate.amount,
                merchant = candidate.merchant,
                category = candidate.category,
                packageName = sbn.packageName,
                sender = sender,
                postedAt = if (sbn.postTime > 0L) sbn.postTime else System.currentTimeMillis(),
                issuer = candidate.issuer,
            ),
        )
    }
}
