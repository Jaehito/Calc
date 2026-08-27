package com.calc.expense

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput

/**
 * 잠금화면에 상주하는 입력용 알림.
 *
 * 핵심은 인라인 답장(RemoteInput)이다. Action 의 authenticationRequired 가 false 여야
 * 잠금 해제 없이 바로 전송된다 (API 31+ 기본값이 false).
 */
object NotificationHelper {

    const val CHANNEL_ID = "expense_input"
    const val NOTIF_ID = 1001
    const val KEY_REPLY = "key_expense_reply"

    private const val IDLE_TEXT = "금액과 내용을 입력하세요 · 예: 커피 4500"

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

        val remoteInput = RemoteInput.Builder(KEY_REPLY)
            .setLabel("예: 커피 4500")
            .build()

        // RemoteInput 이 결과를 주입하려면 PendingIntent 가 MUTABLE 이어야 한다.
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags = flags or PendingIntent.FLAG_MUTABLE
        }

        val replyIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, ReplyReceiver::class.java).setAction(ReplyReceiver.ACTION_REPLY),
            flags,
        )

        val action = NotificationCompat.Action.Builder(
            R.drawable.ic_add,
            "기록",
            replyIntent,
        )
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(false)
            // 잠금 해제를 요구하지 않아야 잠금화면에서 바로 전송된다.
            .setAuthenticationRequired(false)
            .setShowsUserInterface(false)
            .build()

        val openApp = PendingIntent.getActivity(
            context,
            1,
            Intent(context, MainActivity::class.java),
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
            .setContentIntent(openApp)
            .addAction(action)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIF_ID, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS 권한이 없는 경우. 설정 화면에서 안내한다.
        }
    }

    fun hide(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIF_ID)
    }

    fun isEnabled(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()
}
