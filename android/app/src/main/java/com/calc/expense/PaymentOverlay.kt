package com.calc.expense

import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings as AndroidSettings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.TextView
import kotlin.math.abs

/**
 * 결제를 보면 **다른 앱 위, 화면 아래(토스트 자리)** 에 5초 뜨는 팝업.
 *
 * 배너 알림([NotificationHelper.showPaymentBanner])은 시스템이 줄을 세운다 — 카드사 알림이 이미
 * 팝업으로 떠 있으면 그게 들어갈 때까지 우리 배너가 기다렸다. 이 창은 시스템 팝업 줄과 무관하게
 * 바로 그려진다. 시스템 팝업보다 한 층 아래라 화면 위에 두면 카드사 팝업에 가려지므로 아래에 둔다.
 *
 * «다른 앱 위에 표시» 권한이 있고, 화면이 켜져 잠금이 풀려 있을 때만 쓴다. 잠금화면 위에는
 * 이 창이 그려지지 않는다. 쓸 수 없으면 예전 배너로 떨어진다 — 결제를 알리는 길은 늘 하나다.
 *
 * 누르면 수집함, 옆·아래로 밀면 닫힘. **10초에 한 번만 띄운다**([throttle]) — 한 결제가 카드사·카카오페이
 * 두 알림으로 오면 둘째는 띄우지 않는다. 막힌 결제도 수집함에는 담긴다. 배너도 같은 제한을 따른다.
 *
 * **은행 앱 위에서는 안 보인다.** 금융 앱은 보안 때문에 자기 화면이 떠 있는 동안 다른 앱 위의
 * 창을 전부 숨긴다. 그때는 창을 붙여 둔 채 기다리다가, 그 앱을 나가는 순간 뜬다
 * ([waitUntilDrawn]). 한계: 폰 설정에서 애니메이션을 꺼 두면 가려졌는지 알아챌 수 없어
 * 가려진 채 5초가 지날 수 있다.
 */
object PaymentOverlay {

    private const val TAG = "PaymentOverlay"
    private const val SHOW_MS = 5_000L
    private const val ANIM_MS = 180L

    /** 그려지는지 다시 보는 간격. */
    private const val POLL_MS = 400L

    /**
     * 은행 앱 등에 가려진 팝업을 이만큼까지 기다린다. 그 안에 그 앱을 나가면(홈·다른 앱) 그때
     * 뜨고, 넘으면 조용히 치운다 — 한참 뒤에 뜬 결제 팝업은 무슨 결제인지 헷갈린다. 결제는
     * 수집함에 남아 있다.
     */
    private const val WAIT_HIDDEN_MS = 60_000L
    private const val RISE_DP = 24f

    private val main = Handler(Looper.getMainLooper())

    /** 팝업·배너 사이 최소 간격. */
    private const val GAP_MS = 10_000L
    private val throttle = PopupThrottle(GAP_MS)

    // 메인 스레드에서만 만진다.
    private var card: View? = null
    private var lastAmount: Long = 0L
    private var lastMerchant: String = ""

    /** 화면에 그려지기 시작했는지. 그 전까지는 5초를 세지 않고, 터치도 받지 않는다. */
    private var live: Boolean = false
    private val autoHide = Runnable { hide() }

    /** 권한이 있고 사람이 화면을 보고 있는지. 아니면 배너를 쓴다. */
    fun canShow(context: Context): Boolean {
        if (!AndroidSettings.canDrawOverlays(context)) return false
        val power: PowerManager? = context.getSystemService(PowerManager::class.java)
        if (power?.isInteractive != true) return false
        val keyguard: KeyguardManager? = context.getSystemService(KeyguardManager::class.java)
        return keyguard?.isKeyguardLocked != true
    }

    /** 팝업을 띄우고, 띄울 수 없으면 배너로 알린다. 어느 스레드에서 불러도 된다. */
    fun showOrBanner(context: Context, amount: Long, merchant: String) {
        val app: Context = context.applicationContext
        if (!throttle.tryAcquire(SystemClock.elapsedRealtime())) return
        if (!canShow(app)) {
            NotificationHelper.showPaymentBanner(app, amount, merchant)
            return
        }
        main.post {
            val shown: Boolean = try {
                show(app, amount, merchant)
            } catch (e: Exception) {
                // 제조사가 막았거나 권한이 방금 꺼졌다. 알림은 놓치지 않는다.
                Log.w(TAG, "결제 팝업을 띄우지 못해 배너로 대체", e)
                false
            }
            if (!shown) NotificationHelper.showPaymentBanner(app, amount, merchant)
        }
    }

    private fun show(app: Context, amount: Long, merchant: String): Boolean {
        val title: String = if (merchant.isBlank()) StatusText.won(amount) else StatusText.won(amount) + " · " + merchant
        val action: String = tr("눌러서 기록", "Tap to log", "Toca para anotar")

        lastAmount = amount
        lastMerchant = merchant

        val existing: View? = card
        if (existing != null) {
            bind(existing, title, action)
            // 가려져 기다리는 중이면 그대로 기다린다 — 보이기 시작할 때 5초를 센다.
            if (live) restartTimer(existing)
            return true
        }

        live = false
        val view: View = LayoutInflater.from(app).inflate(R.layout.overlay_payment, null)
        view.clipToOutline = true
        bind(view, title, action)
        attachGestures(app, view)

        val wm: WindowManager = app.getSystemService(WindowManager::class.java) ?: return false
        // 보이기 전까지는 터치를 통과시킨다 — 가려진 채 기다리는 투명한 창이 그 자리 터치를 막지 않게.
        wm.addView(view, layoutParams(app, touchable = false))
        card = view

        // 떠오르는 연출이 곧 «그려지고 있나» 의 증거다. 은행 앱처럼 다른 앱 위의 창을 숨기는 앱이
        // 앞에 있으면, 창은 붙어도 그려지지 않아 연출이 멈춘다(실측: 붙음·보임은 true 인데 투명도 0).
        // view.post 에 기대지 않고 바로 시작한다 — 숨겨진 창에서는 post 도 늦게 돈다.
        view.alpha = 0f
        view.translationY = RISE_DP * app.resources.displayMetrics.density
        view.animate().alpha(1f).translationY(0f).setDuration(ANIM_MS).start()
        val addedAt: Long = System.currentTimeMillis()
        main.postDelayed({ waitUntilDrawn(app, view, addedAt) }, POLL_MS)
        return true
    }

    /**
     * 팝업이 실제로 그려질 때까지 기다린다.
     *
     * 은행 앱처럼 다른 앱 위의 창을 숨기는 앱이 앞에 있으면 우리 창은 붙어 있어도 안 그려진다.
     * 그 앱을 나가는(홈·다른 앱) 순간 그려지기 시작하므로, 그때부터 5초를 센다. 배너를 대신
     * 띄우지 않는다 — 은행 앱 안에서는 조용하고, 나오자마자 하단에 뜬다.
     */
    private fun waitUntilDrawn(app: Context, view: View, addedAt: Long) {
        if (card !== view) return
        val waited: Long = System.currentTimeMillis() - addedAt
        if (view.alpha >= 0.99f) {
            live = true
            setTouchable(app, view)
            restartTimer(view)
            return
        }
        if (waited >= WAIT_HIDDEN_MS) {
            card = null
            remove(view)
            return
        }
        // 멈춘 연출을 다시 건다. 가려진 동안은 여전히 멈춰 있고, 보이기 시작하면 이어서 돈다.
        view.animate().alpha(1f).translationY(0f).setDuration(ANIM_MS).start()
        main.postDelayed({ waitUntilDrawn(app, view, addedAt) }, POLL_MS)
    }

    private fun setTouchable(app: Context, view: View) {
        try {
            app.getSystemService(WindowManager::class.java)?.updateViewLayout(view, layoutParams(app, touchable = true))
        } catch (e: Exception) {
            Log.w(TAG, "팝업을 누를 수 있게 바꾸지 못함", e)
        }
    }

    private fun bind(view: View, title: String, action: String) {
        view.findViewById<TextView>(R.id.overlayTitle).text = title
        view.findViewById<TextView>(R.id.overlayAction).text = action
        view.contentDescription = "$title. $action"
    }

    /** 초록 줄을 다시 채워 5초 동안 줄이고, 끝나면 닫는다. */
    private fun restartTimer(view: View) {
        main.removeCallbacks(autoHide)
        val bar: View = view.findViewById(R.id.overlayTimer)
        bar.animate().cancel()
        bar.pivotX = 0f
        bar.scaleX = 1f
        bar.animate().scaleX(0f).setDuration(SHOW_MS).setInterpolator(LinearInterpolator()).start()
        main.postDelayed(autoHide, SHOW_MS)
    }

    private fun layoutParams(app: Context, touchable: Boolean): WindowManager.LayoutParams {
        val density: Float = app.resources.displayMetrics.density
        val side: Int = (12 * density).toInt()
        val params = WindowManager.LayoutParams(
            app.resources.displayMetrics.widthPixels - side * 2,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // 포커스를 뺏지 않는다 — 보던 앱의 키보드·입력이 그대로다. 창 밖 터치는 그 앱으로 간다.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                (if (touchable) 0 else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE),
            PixelFormat.TRANSLUCENT,
        )
        params.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        params.y = (24 * density).toInt()
        // 하단 바와 키보드 위로 올린다. 키보드 뒤에 숨으면 입력 중에는 못 본다.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            params.fitInsetsTypes = WindowInsets.Type.systemBars() or WindowInsets.Type.ime()
        }
        params.windowAnimations = 0
        params.title = "PaymentOverlay"
        return params
    }

    /**
     * 누르면 수집함을 열고, 밀면 닫는다. 조금 움직인 건 누른 것으로 본다 —
     * 손가락이 살짝 떨려도 눌리게.
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun attachGestures(app: Context, view: View) {
        val slop: Float = 12 * app.resources.displayMetrics.density
        var downX = 0f
        var downY = 0f
        var dragging = false

        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    dragging = false
                    // 만지는 동안은 사라지지 않는다.
                    main.removeCallbacks(autoHide)
                    v.findViewById<View>(R.id.overlayTimer).animate().cancel()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx: Float = event.rawX - downX
                    val dy: Float = event.rawY - downY
                    if (!dragging && (abs(dx) > slop || dy > slop)) dragging = true
                    if (dragging) {
                        v.translationX = dx
                        v.translationY = if (dy > 0f) dy else 0f
                        v.alpha = 1f - (abs(dx) / v.width.coerceAtLeast(1)).coerceIn(0f, 0.7f)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val dx: Float = event.rawX - downX
                    val dy: Float = event.rawY - downY
                    when {
                        !dragging -> {
                            hide(immediate = true)
                            openInbox(app)
                        }
                        abs(dx) > v.width / 3f -> dismissSideways(v, dx)
                        dy > v.height / 2f -> hide()
                        else -> {
                            v.animate().translationX(0f).translationY(0f).alpha(1f).setDuration(ANIM_MS).start()
                            restartTimer(v)
                        }
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    v.animate().translationX(0f).translationY(0f).alpha(1f).setDuration(ANIM_MS).start()
                    restartTimer(v)
                    true
                }
                else -> false
            }
        }
    }

    private fun openInbox(app: Context) {
        try {
            app.startActivity(NotificationHelper.intentFor(app, EntryDoor.PENDING_INBOX))
        } catch (e: Exception) {
            Log.w(TAG, "수집함을 열지 못함", e)
        }
    }

    // 닫는 애니메이션을 시작하는 순간 [card] 를 비운다. 그 사이 새 결제가 오면 사라지는 중인
    // 카드에 글자를 바꿔 쓰지 않고 새 카드를 띄운다.
    private fun dismissSideways(view: View, dx: Float) {
        main.removeCallbacks(autoHide)
        if (card === view) card = null
        val target: Float = if (dx > 0f) view.width.toFloat() else -view.width.toFloat()
        view.animate().translationX(target).alpha(0f).setDuration(ANIM_MS)
            .withEndAction { remove(view) }
            .start()
        removeSoon(view)
    }

    private fun hide(immediate: Boolean = false) {
        main.removeCallbacks(autoHide)
        val view: View = card ?: return
        card = null
        if (immediate) {
            remove(view)
            return
        }
        view.animate().alpha(0f).translationY(view.height * 0.6f).setDuration(ANIM_MS)
            .withEndAction { remove(view) }
            .start()
        removeSoon(view)
    }

    /**
     * 연출이 끝나기를 기다리지 않고도 창을 뗀다. 숨겨진 창에서는 연출이 멈춰 withEndAction 이
     * 영영 안 불릴 수 있다 — 그러면 투명한 창이 남아 터치를 막는다. [remove] 는 두 번 불려도 된다.
     */
    private fun removeSoon(view: View) {
        main.postDelayed({ remove(view) }, ANIM_MS + 120L)
    }

    private fun remove(view: View) {
        if (card === view) card = null
        try {
            view.context.getSystemService(WindowManager::class.java)?.removeView(view)
        } catch (_: Exception) {
            // 이미 떨어졌다.
        }
    }
}
