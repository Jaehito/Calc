package com.calc.expense

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * 잠금화면에 상주하는 입력용 알림.
 *
 * 버튼이 없다. 카드를 누르면 [QuickInputActivity] 가 뜨고 거기서 적는다.
 * 인라인 답장(RemoteInput)도 잠금 해제 없이 되지만, 그 좁은 칸에는 오늘 쓸 수 있는 돈이
 * 보이지 않고 한 건마다 알림이 닫힌다 — 적으면서 판단하고 여러 건을 이어 적는 쪽을 택했다.
 */
object NotificationHelper {

    const val CHANNEL_ID = "expense_input"
    const val NOTIF_ID = 1001
    const val KEY_REPLY = "key_expense_reply"

    /** 주 1회 돌아보기는 별도 채널·별도 알림이다. 상시 입력 알림과 섞이지 않는다. */
    private const val WEEKLY_CHANNEL_ID = "weekly_review"
    private const val WEEKLY_NOTIF_ID = 1002

    /** 결제 뒤 «적었어?» 리마인더. 또 다른 별도 채널·별도 알림. */
    private const val REMINDER_CHANNEL_ID = "payment_reminder"
    private const val REMINDER_NOTIF_ID = 1003

    private const val IDLE_TEXT = "눌러서 기록하세요 · 예: 커피 4500"

    private const val REQUEST_OPEN_INPUT = 1
    private const val REQUEST_DISMISSED = 2
    private const val REQUEST_OPEN_HOME = 3
    private const val REQUEST_OPEN_INPUT_REMINDER = 4

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "지출 빠른 입력",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "잠금화면에서 지출을 바로 기록하는 상시 알림"
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            enableVibration(false)
            setSound(null, null)
        }
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    /**
     * 상시 알림을 띄우거나 갱신한다.
     *
     * @param lines 직전 기록 결과. null 이면 기본 안내 문구를 보여준다.
     *   [StatusLines.summary] 는 잠금화면에 접힌 채로 보이는 한 줄이고,
     *   [StatusLines.detail] 은 펼쳤을 때의 본문이다. 접힌 줄에 가장 중요한 숫자를 둔다 —
     *   대부분은 펼치지 않는다.
     */
    fun show(context: Context, lines: StatusLines? = null) {
        ensureChannel(context)

        // 알림 카드 전체가 입력 화면을 여는 버튼이 된다. 작은 액션 버튼보다 조준이 쉽고,
        // 무엇보다 적는 동안 오늘 쓸 수 있는 돈이 보인다. 그래서 «기록» 액션 버튼은 두지 않는다.
        val openInput = PendingIntent.getActivity(
            context,
            REQUEST_OPEN_INPUT,
            Intent(context, QuickInputActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // 안드로이드 13 부터 setOngoing 으로는 스와이프도 «지우기» 도 막지 못한다.
        // 지워지면 이 인텐트가 불리고, 앱에서 끈 게 아니면 되살린다.
        val onDismissed = PendingIntent.getBroadcast(
            context,
            REQUEST_DISMISSED,
            Intent(context, DismissReceiver::class.java)
                .setAction(DismissReceiver.ACTION_DISMISSED),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_wallet)
            .setContentTitle("지출 기록")
            .setContentText(lines?.summary ?: IDLE_TEXT)
            .setStyle(NotificationCompat.BigTextStyle().bigText(lines?.detail ?: IDLE_TEXT))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openInput)
            .setDeleteIntent(onDismissed)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIF_ID, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS 권한이 없는 경우. 설정 화면에서 안내한다.
        }
    }

    /**
     * 주 1회 돌아보기 알림. 상시 입력 알림과 달리 지울 수 있고 되살리지 않는다 —
     * 한 주에 한 번 툭 던지는 알림이라 스와이프로 넘기면 그만이다.
     */
    fun showWeekly(context: Context, lines: StatusLines) {
        ensureWeeklyChannel(context)

        val openHome = PendingIntent.getActivity(
            context,
            REQUEST_OPEN_HOME,
            Intent(context, HomeActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, WEEKLY_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_wallet)
            .setContentTitle("이번 주 돌아보기")
            .setContentText(lines.summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(lines.detail))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(openHome)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(WEEKLY_NOTIF_ID, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS 권한이 없는 경우.
        }
    }

    private fun ensureWeeklyChannel(context: Context) {
        val channel = NotificationChannel(
            WEEKLY_CHANNEL_ID,
            "주간 돌아보기",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "주 1회 지난 7일 지출을 돌아보는 알림"
            setShowBadge(true)
        }
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    /**
     * 결제 뒤 기록이 없을 때 한 번 띄우는 «적었어?» 리마인더.
     *
     * 금액도 개수도 말하지 않는다 — «방금 쓴 거 있으면 적어 둬요» 정도의 가벼운 찌름이다.
     * 누르면 바로 입력 화면이 열린다. 스와이프로 넘기면 그만이고 되살리지 않는다.
     */
    fun showReminder(context: Context) {
        ensureReminderChannel(context)

        val openInput = PendingIntent.getActivity(
            context,
            REQUEST_OPEN_INPUT_REMINDER,
            Intent(context, QuickInputActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, REMINDER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_wallet)
            .setContentTitle("방금 쓴 거 있어요?")
            .setContentText("있으면 눌러서 적어 두세요")
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(openInput)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(REMINDER_NOTIF_ID, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS 권한이 없는 경우.
        }
    }

    private fun ensureReminderChannel(context: Context) {
        val channel = NotificationChannel(
            REMINDER_CHANNEL_ID,
            "기록 리마인더",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "결제 알림 뒤 기록이 없으면 한 번 알려줌 (금액은 읽지 않음)"
            setShowBadge(true)
        }
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    fun hide(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIF_ID)
    }

    fun isEnabled(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()
}
