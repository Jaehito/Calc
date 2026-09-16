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
     * 결제로 보이는 알림을 **수집함과 결제 기록 두 곳에** 담는다.
     *
     * 알림 키([StatusBarNotification.getKey])를 그대로 수집함 항목 id 로 쓴다 — 안드로이드가 같은
     * 알림을 갱신할 때마다 다시 올리는데, 이 키가 같으므로 덮어써서 중복이 쌓이지 않는다.
     *
     * 목록이 둘인 이유는 수명이 다르기 때문이다. 수집함은 «지금 물어볼 것»이라 기록하거나
     * 무시하면 그 자리에서 사라지고 7일이면 버린다. 결제 기록([PaymentLogStore])은 그것과
     * 무관하게 남아 «달마다 되풀이되는 것»을 세는 재료가 된다 — 월세·보험처럼 손으로 적지 않고
     * 수집함에서도 치워 버리는 항목이 정작 사용자가 모르는 고정비다.
     */
    private fun collect(sbn: StatusBarNotification, title: String?, text: String?) {
        val app = applicationContext
        val sender: String = title.orEmpty()
        if (PaymentBlocklist.isBlocked(app, sbn.packageName, sender)) return

        val categories: List<String> = CategoryStore.load(app)
        val candidate: PaymentCandidate = PaymentParse.parse(title, text, categories) ?: return

        // 예전에 이 이름을 고쳐서 기록했으면 그 이름으로 띄운다. 같은 가게를 볼 때마다
        // 같은 수정을 되풀이하게 두지 않는다([NameMemories]).
        val remembered: String? = NameMemoryStore.recall(app, candidate.merchant)
        val merchant: String = remembered ?: candidate.merchant

        // 카테고리도 «사용자의 이름»으로 찾는다 — 카테고리 기억은 사용자가 적은 이름에
        // 붙어 있어서 알림의 원래 이름으로는 찾지 못한다. 기억이 낱말 규칙을 이기는 것은
        // 기록 화면([QuickInputActivity.applyAutoCategory])과 같은 순서다.
        val category: String =
            CategoryMemoryStore.recall(app, merchant, categories) ?: candidate.category

        val postedAt: Long = if (sbn.postTime > 0L) sbn.postTime else System.currentTimeMillis()

        PendingPaymentStore.add(
            app,
            PendingPayment(
                id = sbn.key,
                amount = candidate.amount,
                merchant = merchant,
                category = category,
                packageName = sbn.packageName,
                sender = sender,
                postedAt = postedAt,
                issuer = candidate.issuer,
                renamed = remembered != null,
            ),
        )

        PaymentLogStore.add(
            app,
            PaymentLogEntry(name = merchant, amount = candidate.amount, at = postedAt),
        )
    }
}
