package com.calc.expense

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.LocusIdCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat

/**
 * 잠금화면에 상주하는 입력용 알림.
 *
 * 버튼이 없다. 카드를 누르면 [QuickInputActivity] 가 뜨고 거기서 적는다.
 * 인라인 답장(RemoteInput)도 잠금 해제 없이 되지만, 그 좁은 칸에는 오늘 쓸 수 있는 돈이
 * 보이지 않고 한 건마다 알림이 닫힌다 — 적으면서 판단하고 여러 건을 이어 적는 쪽을 택했다.
 */
object NotificationHelper {

    /**
     * v3 — 잠금화면에서 다른 앱의 새 알림보다도 위로 가도록 중요도를 IMPORTANCE_HIGH 로 올렸다.
     * v2(DEFAULT)까지는 같은 중요도끼리 «최신 알림이 위»라, 다른 앱이 새 알림을 띄우면 상시
     * 카드가 아래로 밀렸다(마지막 기록 시각이 옛날이라). HIGH 는 상단(알림) 영역으로 올려
     * 이 밀림을 줄인다. 안드로이드는 이미 만든 채널의 중요도를 앱이 못 바꾸므로(사용자만 가능)
     * id 를 바꿔야 기존 설치에도 적용된다 — [ensureChannel] 이 옛 v1·v2 채널을 지운다.
     * 그래도 제조사(삼성 등) 정책에 따라 «항상 맨 위»가 100% 보장되진 않는다.
     */
    const val CHANNEL_ID = "expense_input_v3"
    private const val LEGACY_CHANNEL_ID = "expense_input"
    private const val LEGACY_CHANNEL_ID_V2 = "expense_input_v2"
    const val NOTIF_ID = 1001
    const val KEY_REPLY = "key_expense_reply"

    /** 주 1회 돌아보기는 별도 채널·별도 알림이다. 상시 입력 알림과 섞이지 않는다. */
    private const val WEEKLY_CHANNEL_ID = "weekly_review"
    private const val WEEKLY_NOTIF_ID = 1002

    /** 결제 뒤 «적었어?» 리마인더. 또 다른 별도 채널·별도 알림. */
    private const val REMINDER_CHANNEL_ID = "payment_reminder"
    private const val REMINDER_NOTIF_ID = 1003

    /** 저녁 9시 «오늘 등급». 또 다른 별도 채널·별도 알림 — 상시 카드와 섞이면 등급이 묻힌다. */
    private const val GRADE_CHANNEL_ID = "daily_grade"
    private const val GRADE_NOTIF_ID = 1004

    private const val IDLE_TEXT = "눌러서 기록하세요 · 예: 커피 4500"

    /**
     * 상시 카드를 **«대화» 알림**으로 만들기 위한 바로가기 id.
     *
     * 안드로이드 11 부터 알림 목록 맨 위 칸은 «대화» 전용이고, 일반 알림은 중요도를 아무리
     * 올려도 그 아래에서 시작한다. v3 에서 IMPORTANCE_HIGH 로 올려도 다른 앱 알림에 밀리던
     * 이유가 이것이다. 대화로 인정받으려면 세 가지가 필요하다 —
     * [NotificationCompat.MessagingStyle], 오래 사는 동적 바로가기, 그리고 둘을 잇는 이 id.
     *
     * 사용자가 이 알림을 길게 눌러 «우선 대화» 로 지정하면 그 칸 안에서도 맨 위에 고정된다.
     */
    private const val SHORTCUT_ID = "gotgan_record"

    /** 대화 상대. 알림에 이 이름이 보인다 — 이 앱의 말로 «곳간» 이다. */
    private const val SENDER_NAME = "곳간"

    private const val REQUEST_OPEN_INPUT = 1
    private const val REQUEST_DISMISSED = 2
    private const val REQUEST_OPEN_HOME = 3
    private const val REQUEST_OPEN_INPUT_REMINDER = 4
    private const val REQUEST_OPEN_HOME_GRADE = 5

    /**
     * IMPORTANCE_HIGH 로 잠금화면 상단(알림) 영역에 올린다 — 다른 앱의 새 알림에도 덜 밀린다.
     * 정렬의 본체는 이제 [SHORTCUT_ID] 의 «대화» 승격이고, 중요도는 그 보조다.
     * 대신 소리·진동은 채널에서 꺼 둔다(setSound null·enableVibration false) — HIGH 라도 소리는
     * 나지 않는다. 다만 «알림» 영역 소속이라 처음 뜰 때 헤드업 배너가 한 번 뜰 수 있고, 이후
     * 갱신은 [show] 의 setOnlyAlertOnce 로 반복 배너를 막는다. 무음 유지를 위해 이전엔 붙였던
     * setSilent 는 뺐다 — 그게 알림을 «무음 알림» 하단 묶음으로 내려 상단 정렬을 되레 깨뜨린다.
     */
    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.deleteNotificationChannel(LEGACY_CHANNEL_ID)
        manager.deleteNotificationChannel(LEGACY_CHANNEL_ID_V2)

        val channel = NotificationChannel(
            CHANNEL_ID,
            "지출 빠른 입력",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "잠금화면에서 지출을 바로 기록하는 상시 알림"
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            enableVibration(false)
            setSound(null, null)
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * 그 문으로 들어왔을 때 열 화면. 규칙은 [EntryRoutes] 가 갖고 여기서는 액티비티로 옮기기만 한다.
     *
     * 알림마다 목적지를 직접 적지 않는 이유는, 그렇게 두면 알림이 늘 때마다 «홈이야 기록이야» 를
     * 그 자리에서 다시 정하게 되고 규칙이 흩어지기 때문이다.
     */
    private fun openFor(context: Context, door: EntryDoor, requestCode: Int): PendingIntent {
        val intent: Intent = when (EntryRoutes.of(door)) {
            EntryRoute.HOME -> Intent(context, HomeActivity::class.java)
            EntryRoute.RECORD -> Intent(context, QuickInputActivity::class.java)
                .putExtra(QuickInputActivity.EXTRA_FORCE_INPUT, true)
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** 알림에 보일 «곳간». 바로가기와 알림이 같은 사람을 가리켜야 대화로 묶인다. */
    private fun sender(context: Context): Person = Person.Builder()
        .setKey(SHORTCUT_ID)
        .setName(SENDER_NAME)
        .setIcon(IconCompat.createWithResource(context, R.drawable.ic_wallet))
        .setBot(false)
        // 우선 대화 후보로 올려 준다. 지정은 사용자가 한다.
        .setImportant(true)
        .build()

    /**
     * 대화 알림이 붙을 **오래 사는 동적 바로가기**를 만든다(이미 있으면 갱신).
     *
     * [ShortcutInfoCompat.Builder.setLongLived] 가 핵심이다 — 이게 없으면 시스템이 바로가기를
     * 잠깐 쓰는 것으로 보고 대화로 묶지 않는다. 바로가기 자체도 홈 화면에 꺼내 쓸 수 있어
     * «기록» 바로가기 노릇을 겸한다.
     */
    private fun ensureShortcut(context: Context) {
        val shortcut = ShortcutInfoCompat.Builder(context, SHORTCUT_ID)
            .setShortLabel(SENDER_NAME)
            .setLongLabel("지출 기록")
            .setIcon(IconCompat.createWithResource(context, R.drawable.ic_wallet))
            .setPerson(sender(context))
            .setLongLived(true)
            .setIntent(
                Intent(context, QuickInputActivity::class.java)
                    .setAction(Intent.ACTION_VIEW)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            .build()
        try {
            ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
        } catch (_: Exception) {
            // 바로가기 상한을 넘었거나 제조사가 막은 경우. 알림은 그대로 뜨되 일반 알림이 된다.
        }
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
        ensureShortcut(context)

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

        val now: Long = System.currentTimeMillis()
        val who: Person = sender(context)
        val message: String = lines?.summary ?: IDLE_TEXT

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_wallet)
            // 대화 알림은 MessagingStyle 이어야 한다. 그 대신 BigText 본문을 잃으므로
            // 접힌 한 줄(summary)을 그대로 쓴다 — 어차피 대부분은 펼치지 않고, 자세한
            // 숫자는 카드를 눌러 들어간 입력 화면에 다 있다.
            .setStyle(
                NotificationCompat.MessagingStyle(who)
                    .addMessage(message, now, who),
            )
            .setShortcutId(SHORTCUT_ID)
            .setLocusId(LocusIdCompat(SHORTCUT_ID))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            // setOngoing(true) 를 뺐다. One UI 는 «진행 중» 알림을 별도 묶음으로 내리므로,
            // 켜 두면 중요도를 올려도 그 묶음째 아래에 깔린다. 지워져도 DismissReceiver 가
            // 되살리므로 상시성은 그대로다 — 안드로이드 13 부터 setOngoing 은 어차피
            // 스와이프를 막지 못했다.
            .setOngoing(false)
            // 다시 띄울 때마다 시각을 갱신해 목록 위쪽으로 되돌린다. 시각 자체는 숨긴다 —
            // «오전 6:54» 는 마지막 기록 시각으로 오해되기 쉽다.
            .setWhen(now)
            .setShowWhen(false)
            // 처음 한 번만 알린다(배너). 이후 기록·앱 열기로 갱신될 때는 다시 튀지 않는다.
            .setOnlyAlertOnce(true)
            // setSilent 는 일부러 안 쓴다 — 무음 알림 묶음으로 내려가 상단 정렬을 깨기 때문.
            // 소리·진동은 채널(IMPORTANCE_HIGH + setSound null)에서 이미 꺼 둔다.
            .setPriority(NotificationCompat.PRIORITY_HIGH)
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

        val openHome: PendingIntent = openFor(context, EntryDoor.WEEKLY_REVIEW, REQUEST_OPEN_HOME)

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

    /**
     * 저녁 9시 «오늘 등급» 알림. 주간 돌아보기처럼 지울 수 있고 되살리지 않는다 —
     * 하루에 한 번 툭 던지는 알림이라 스와이프로 넘기면 그만이다.
     */
    fun showGrade(context: Context, lines: StatusLines) {
        ensureGradeChannel(context)

        val openHome: PendingIntent = openFor(context, EntryDoor.DAILY_GRADE, REQUEST_OPEN_HOME_GRADE)

        val notification = NotificationCompat.Builder(context, GRADE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_wallet)
            .setContentTitle("오늘 등급")
            .setContentText(lines.summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(lines.detail))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(openHome)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(GRADE_NOTIF_ID, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS 권한이 없는 경우.
        }
    }

    private fun ensureGradeChannel(context: Context) {
        val channel = NotificationChannel(
            GRADE_CHANNEL_ID,
            "오늘 등급",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "매일 저녁 9시, 오늘 지출을 예산 대비 등급으로 알려줌"
            setShowBadge(true)
        }
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
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
     *
     * 잠금 여부와 무관하게 늘 입력 화면으로 간다(EXTRA_FORCE_INPUT) — 이 알림의 목적이
     * "방금 그 결제를 지금 적자"라서, 상시 카드처럼 잠금 풀렸다고 홈으로 돌리면 안 된다.
     */
    fun showReminder(context: Context) {
        ensureReminderChannel(context)

        val openInput = PendingIntent.getActivity(
            context,
            REQUEST_OPEN_INPUT_REMINDER,
            Intent(context, QuickInputActivity::class.java)
                .putExtra(QuickInputActivity.EXTRA_FORCE_INPUT, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        // 위 인텐트는 openFor(EntryDoor.PAYMENT_REMINDER) 와 같은 곳을 연다.
        // 잠금화면 특성(showWhenLocked)을 잃지 않으려 QuickInputActivity 로 직행시킨다.

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
