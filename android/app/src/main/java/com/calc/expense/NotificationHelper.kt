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

    /** 곳간마다 다른 requestCode 를 쓰기 위한 기준값. */
    private const val REQUEST_REPLY_BASE = 100
    private const val REQUEST_OPEN_INPUT = 1
    private const val REQUEST_DISMISSED = 2

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

        // 연결된 곳간이 하나뿐이면 고르게 하지 않는다. 애매한 지출에서 멈칫하는 3초가 이탈 지점이다.
        val settings = SettingsStore.load(context)
        val purses: List<Purse> = settings.linkedPurses.ifEmpty { listOf(Purse.PERSONAL) }
        val labelled: Boolean = purses.size > 1

        // 알림 카드 전체가 입력 화면을 여는 버튼이 된다. 작은 액션 버튼보다 조준이 쉽다.
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

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
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

        for (purse in purses) {
            builder.addAction(
                replyAction(context, purse, if (labelled) settings.labelOf(purse) else "기록")
            )
        }

        val notification = builder.build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIF_ID, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS 권한이 없는 경우. 설정 화면에서 안내한다.
        }
    }

    /**
     * 곳간 하나에 대응하는 인라인 답장 액션.
     *
     * PendingIntent 는 extras 를 동일성 비교에 넣지 않는다. 곳간마다 requestCode 를 달리 주지
     * 않으면 FLAG_UPDATE_CURRENT 가 두 액션을 하나로 합쳐 버린다.
     */
    private fun replyAction(
        context: Context,
        purse: Purse,
        label: String,
    ): NotificationCompat.Action {
        val remoteInput = RemoteInput.Builder(KEY_REPLY)
            .setLabel("예: 커피 4500")
            .build()

        // RemoteInput 이 결과를 주입하려면 PendingIntent 가 MUTABLE 이어야 한다.
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags = flags or PendingIntent.FLAG_MUTABLE
        }

        val intent = Intent(context, ReplyReceiver::class.java)
            .setAction("${ReplyReceiver.ACTION_REPLY}.${purse.key}")
            .putExtra(ReplyReceiver.EXTRA_PURSE, purse.key)

        val replyIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_REPLY_BASE + purse.ordinal,
            intent,
            flags,
        )

        return NotificationCompat.Action.Builder(R.drawable.ic_add, label, replyIntent)
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(false)
            // 잠금 해제를 요구하지 않아야 잠금화면에서 바로 전송된다.
            .setAuthenticationRequired(false)
            .setShowsUserInterface(false)
            .build()
    }

    fun hide(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIF_ID)
    }

    fun isEnabled(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()
}
