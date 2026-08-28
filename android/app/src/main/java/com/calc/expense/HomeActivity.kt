package com.calc.expense

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.LocalDate
import java.util.concurrent.Executors

/**
 * 앱을 열면 나오는 화면. 곳간 숫자를 보여주는 것 하나만 한다.
 *
 * 설정과 빠른 입력은 XML 그대로다 — 잘 도는 화면을 다시 만들 이유가 없다.
 * 여기만 Compose 인 이유는 이 화면이 새로 만드는 화면이기 때문이다.
 */
class HomeActivity : ComponentActivity() {

    private val io = Executors.newSingleThreadExecutor()

    private var snapshots: List<LedgerSnapshot> by mutableStateOf(emptyList())
    private var notice: String? by mutableStateOf(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HomeScreen(
                today = LocalDate.now(),
                snapshots = snapshots,
                notice = notice,
                onOpenSettings = { startActivity(Intent(this, MainActivity::class.java)) },
                onRecord = { startActivity(Intent(this, QuickInputActivity::class.java)) },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
        republishNotification()
        resyncInBackground()
    }

    override fun onDestroy() {
        io.shutdown()
        super.onDestroy()
    }

    /** 로컬 캐시만으로 즉시 그린다. Notion 왕복은 그 뒤에 따라온다. */
    private fun refresh() {
        val today: LocalDate = LocalDate.now()
        snapshots = SettingsStore.load(this).linkedPurses
            .mapNotNull { Ledger.snapshot(this, it, today) }
    }

    /**
     * 셰이드에 남아 있던 옛 알림은 옛 버전의 PendingIntent 를 들고 있어 눌러도 아무 데도 안 간다.
     * 앱을 열 때마다 지금 버전으로 갈아 끼운다.
     */
    private fun republishNotification() {
        if (NotificationState.isOn(this)) {
            NotificationHelper.show(this)
            // 주 1회 돌아보기 예약을 확인·갱신한다. 예약이 사라졌어도 앱을 열면 되살아난다.
            WeeklyReviewScheduler.schedule(this)
        }
    }

    /**
     * Notion 을 기준으로 캐시를 다시 맞춘다.
     *
     * 잠금화면 기록은 로컬 사본만 더하고 끝낸다 — 브로드캐스트 수명 안에 왕복을 두 번 할 수 없다.
     * 그래서 Notion 에서 직접 고친 행이나 다른 기기의 기록은 여기서만 반영된다.
     */
    private fun resyncInBackground() {
        val app = applicationContext
        val purses: List<Purse> = SettingsStore.load(this).linkedPurses
        if (purses.isEmpty()) return

        notice = "Notion과 맞추는 중…"
        io.execute {
            var failure: String? = null
            for (purse in purses) {
                val error: String? = try {
                    Ledger.resync(app, purse)
                } catch (e: Exception) {
                    "오류: ${e.message ?: e.javaClass.simpleName}"
                }
                if (error != null && failure == null) failure = error
            }

            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                notice = failure
                refresh()
            }
        }
    }
}
