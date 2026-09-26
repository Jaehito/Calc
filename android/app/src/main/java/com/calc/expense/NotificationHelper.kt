package com.calc.expense

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
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
     * 그래도 제조사(삼성 등) 정책에 따라 «항상 맨 위»가 100% 보장되진 않는다. 그래서 지금은
     * **[CardRefreshWorker] 가 한 시간에 한 번 카드를 조용히 다시 올리는 쪽**이 정렬의 본체이고,
     * 채널 중요도는 그 사이를 버티는 보조다 — «억지로 위에 붙잡아 두기»를 그만두고
     * «내려가면 한 시간 안에 되올라온다»로 문제를 옮겼다.
     */
    const val CHANNEL_ID = "expense_input_v3"
    private const val LEGACY_CHANNEL_ID = "expense_input"
    private const val LEGACY_CHANNEL_ID_V2 = "expense_input_v2"
    const val NOTIF_ID = 1001
    const val KEY_REPLY = "key_expense_reply"

    /** 주 1회 돌아보기는 별도 채널·별도 알림이다. 상시 입력 알림과 섞이지 않는다. */
    private const val WEEKLY_CHANNEL_ID = "weekly_review"
    /** 결제 배너. 자리가 하나뿐이라 새 결제가 앞엣것을 덮는다. */
    private const val BANNER_NOTIF_ID = 1005

    /** 배너가 스스로 사라지기까지. 읽고 누를 만큼은 되고, 눈에 걸릴 만큼 길지는 않다. */
    private const val BANNER_TIMEOUT_MS = 8_000L

    private const val BANNER_CHANNEL_ID = "payment_banner"

    /** 결제 뒤 «적었어?» 리마인더. 또 다른 별도 채널·별도 알림. */
    private const val REMINDER_CHANNEL_ID = "payment_reminder"

    /** 저녁 9시 «오늘 등급». 또 다른 별도 채널·별도 알림 — 상시 카드와 섞이면 등급이 묻힌다. */
    private const val GRADE_CHANNEL_ID = "daily_grade"

    private const val IDLE_TEXT = "눌러서 기록하세요 · 예: 커피 4500"

    /** 앱 아이콘을 길게 눌렀을 때 나오는 «지출 기록» 바로가기의 id. */
    private const val SHORTCUT_ID = "gotgan_record"

    /** 카드 제목. 이 앱의 말로 «곳간» 이다. */
    private const val CARD_TITLE = "곳간"

    private const val REQUEST_OPEN_INPUT = 1
    private const val REQUEST_DISMISSED = 2
    private const val REQUEST_OPEN_HOME = 3
    private const val REQUEST_OPEN_INBOX = 4

    /**
     * IMPORTANCE_HIGH 로 잠금화면 상단(알림) 영역에 올린다 — 다른 앱의 새 알림에도 덜 밀린다.
     * 대신 소리·진동은 채널에서 꺼 둔다(setSound null·enableVibration false) — HIGH 라도 소리는
     * 나지 않는다. 다만 «알림» 영역 소속이라 처음 뜰 때 헤드업 배너가 한 번 뜰 수 있고, 이후
     * 갱신은 [show] 의 setOnlyAlertOnce 로 반복 배너를 막는다. 무음 유지를 위해 이전엔 붙였던
     * setSilent 는 뺐다 — 그게 알림을 «무음 알림» 하단 묶음으로 내려 상단 정렬을 되레 깨뜨린다.
     */
    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        removeOldChannels(manager)

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
        }
        // 배너로 들어오면 수집함을 반드시 연다. 홈은 앱을 켤 때 한 번만 수집함을 띄우므로,
        // 이미 켜 둔 앱으로 돌아오는 경우에는 그냥 홈만 보이고 끝난다 — 배너를 누른 이유가
        // 사라진다.
        // CLEAR_TOP 까지 붙여야 이미 떠 있는 홈이 앞으로 나오면서 이 인텐트를 받는다.
        // 위에 리포트나 카테고리 화면이 열려 있었다면 그것도 닫힌다 — 배너를 눌렀다는 건
        // 지금 그 결제를 처리하겠다는 뜻이다.
        val flags: Int =
            if (door == EntryDoor.PENDING_INBOX) {
                intent.putExtra(HomeActivity.EXTRA_OPEN_INBOX, true)
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            } else {
                Intent.FLAG_ACTIVITY_NEW_TASK
            }

        return PendingIntent.getActivity(
            context,
            requestCode,
            intent.addFlags(flags),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * 앱 아이콘을 길게 누르면 나오는 **«지출 기록» 바로가기**를 만든다(이미 있으면 갱신).
     *
     * 예전에는 상시 카드를 «대화» 알림으로 올리려고 이 바로가기에 사람·오래 삶 표시를 붙였다.
     * 대화 알림은 One UI 가 목록의 별도 칸으로 떼어 내려서, 곳간이 다른 알림들과 따로 노는
     * 것처럼 보였다. 이제 카드는 일반 알림이고, 바로가기는 바로가기 노릇만 한다.
     */
    private fun ensureShortcut(context: Context) {
        val shortcut = ShortcutInfoCompat.Builder(context, SHORTCUT_ID)
            .setShortLabel(CARD_TITLE)
            .setLongLabel("지출 기록")
            .setIcon(IconCompat.createWithResource(context, R.drawable.ic_wallet))
            // 바로가기도 기록으로 간다 — [EntryRoutes] 의 OTHER 규칙과 같은 목적지다.
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
     * @param alert 이번 한 번은 배너로 떠오르게 할지. 등급·주간 돌아보기처럼 **말할 것이 생긴
     *   순간**에만 true 다. 기록할 때마다 떠오르면 그건 알림이 아니라 방해다.
     */
    fun show(context: Context, lines: StatusLines? = null, alert: Boolean = false) {
        ensureChannel(context)
        ensureShortcut(context)

        // 알림 카드 전체가 입력 화면을 여는 버튼이 된다. 작은 액션 버튼보다 조준이 쉽고,
        // 무엇보다 적는 동안 오늘 쓸 수 있는 돈이 보인다. 그래서 «기록» 액션 버튼은 두지 않는다.
        //
        // 목적지는 [EntryRoutes] 가 정한다 — LOCK_CARD 는 잠금 여부와 상관없이 언제나 기록이다.
        // 예전에는 잠금이 풀려 있으면 홈으로 돌렸는데, 알림 셰이드에서 카드를 눌렀을 때도 홈이
        // 열려 «적으려고 눌렀는데 메인이 뜬다»가 됐다. 카드를 누르는 이유는 늘 적으려는 것이다.
        val openInput: PendingIntent = openFor(context, EntryDoor.LOCK_CARD, REQUEST_OPEN_INPUT)

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
        val message: String = lines?.summary ?: IDLE_TEXT

        // **일반 알림이다.** 대화 알림(MessagingStyle)이었을 때는 One UI 가 목록의 «대화» 칸으로
        // 떼어 내려, 한 칸이어도 다른 알림들과 따로 노는 것처럼 보였다. 대신 펼쳤을 때의
        // 본문(detail)을 되찾았다 — 대화 알림은 한 줄밖에 못 보였다.
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_wallet)
            .setContentTitle(CARD_TITLE)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(lines?.detail ?: message))
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
            // 평소에는 처음 한 번만 알린다(배너) — 기록·앱 열기로 갱신될 때 다시 튀지 않는다.
            // 등급·주간처럼 새로 말할 것이 생긴 순간에만 alert 로 한 번 더 떠오르게 한다.
            .setOnlyAlertOnce(!alert)
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
     * 주 1회 돌아보기. [showGrade] 와 같은 이유로 **상시 카드의 문구를 바꾼다.**
     */
    fun showWeekly(context: Context, lines: StatusLines) {
        show(context, lines, alert = true)
    }

    /**
     * 저녁 9시 «오늘 등급». **따로 알림을 띄우지 않고 상시 카드의 문구를 바꾼다.**
     *
     * 예전에는 별도 알림이었고, 그래서 알림 목록에 곳간이 두 칸을 차지했다. 앱이 트레이에서
     * 쓰는 자리는 하나여야 한다 — 두 칸이 되는 순간 사용자는 «이 앱이 시끄럽다»고 느끼고,
     * 그러면 상시 카드까지 함께 꺼 버린다. 카드 하나가 때에 따라 다른 말을 하면 충분하다.
     */
    fun showGrade(context: Context, lines: StatusLines) {
        show(context, lines, alert = true)
    }

    /**
     * 결제를 보면 **잠깐 떴다 스스로 사라지는 배너.**
     *
     * 바라신 것은 토스트였는데, 안드로이드는 앱이 꺼져 있을 때 토스트를 띄우지 못하게 막아
     * 뒀다(11부터). 떠 있는 창을 쓰려면 «다른 앱 위에 표시» 권한을 따로 받아야 하고 제조사가
     * 막기도 한다. 그래서 배너 알림에 [BANNER_TIMEOUT_MS] 자동 사라짐을 걸어 같은 모양을
     * 만든다 — 뜨는 자리도 누르는 동작도 토스트와 같고 권한이 필요 없다.
     *
     * **트레이에 남지 않는다.** 그래서 «곳간이 쓰는 알림 자리는 하나»라는 약속을 깨지 않는다.
     *
     * 소리는 내지 않는다. 결제 문자가 이미 한 번 울렸고 그 위에 또 울리면, 하루에 열 번
     * 결제하는 사람에게 이 앱은 그냥 시끄러운 앱이 된다. 눈에는 확실히 띄되 조용히 뜬다.
     *
     * id 가 하나뿐이라 결제가 잇따라 오면 뒤엣것이 앞엣것을 덮는다. 어차피 몇 초 뒤 사라질
     * 배너이고, 놓친 건은 수집함에 그대로 쌓여 앱을 열 때 나온다.
     */
    fun showPaymentBanner(context: Context, amount: Long, merchant: String) {
        ensureBannerChannel(context)

        val openInbox: PendingIntent = openFor(context, EntryDoor.PENDING_INBOX, REQUEST_OPEN_INBOX)
        val title: String =
            if (merchant.isBlank()) StatusText.won(amount)
            else StatusText.won(amount) + " · " + merchant

        val notification = NotificationCompat.Builder(context, BANNER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_wallet)
            .setContentTitle(title)
            .setContentText("눌러서 기록하기")
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(openInbox)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            // 결제마다 떠올라야 한다. 이 알림은 «새 소식»이지 상태 표시가 아니다.
            .setOnlyAlertOnce(false)
            .setTimeoutAfter(BANNER_TIMEOUT_MS)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(BANNER_NOTIF_ID, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS 권한이 없는 경우.
        }
    }

    private fun ensureBannerChannel(context: Context) {
        val channel = NotificationChannel(
            BANNER_CHANNEL_ID,
            "결제 알림",
            // HIGH 라야 화면 위로 떠오른다. 소리는 위 설명대로 꺼 둔다.
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "결제를 보면 금액과 가게 이름을 잠깐 띄움 (몇 초 뒤 사라짐)"
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    /**
     * 예전에 쓰던 알림 채널을 지운다. 남겨 두면 설정 화면에 유령 항목이 보이고, 그 자체가
     * «이 앱은 알림을 여러 개 쓴다»는 인상을 남긴다 — 줄인 뜻이 반쯤 사라진다.
     */
    private fun removeOldChannels(manager: NotificationManager) {
        manager.deleteNotificationChannel(LEGACY_CHANNEL_ID)
        manager.deleteNotificationChannel(LEGACY_CHANNEL_ID_V2)
        manager.deleteNotificationChannel(GRADE_CHANNEL_ID)
        manager.deleteNotificationChannel(WEEKLY_CHANNEL_ID)
        manager.deleteNotificationChannel(REMINDER_CHANNEL_ID)
    }

    fun hide(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIF_ID)
    }

    fun isEnabled(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()
}
