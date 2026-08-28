package com.calc.expense

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth
import java.util.concurrent.Executors

/**
 * 앱을 열면 나오는 화면. 하단 탭으로 홈·통계·챌린지를 오간다.
 *
 * 설정과 빠른 입력은 XML 그대로다 — 잘 도는 화면을 다시 만들 이유가 없다.
 * 여기만 Compose 인 이유는 이 화면들이 새로 만드는 화면이기 때문이다.
 */
class HomeActivity : ComponentActivity() {

    private val io = Executors.newSingleThreadExecutor()

    private var tab: Int by mutableStateOf(0)
    private var snapshots: List<LedgerSnapshot> by mutableStateOf(emptyList())
    private var notice: String? by mutableStateOf(null)

    private var stats: StatsData? by mutableStateOf(null)
    /** 카테고리 막대가 보는 달. 0 = 이번 달, 1 = 지난 달. */
    private var categoryMonthBack: Int by mutableStateOf(0)

    private val challenge: ChallengeRepository by lazy { FirestoreChallengeRepository(this) }
    private var challengeUi: ChallengeUi by mutableStateOf(ChallengeUi())
    private var challengeReg: Cancellable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Scaffold(bottomBar = { BottomBar() }) { padding ->
                Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                    when (tab) {
                        1 -> StatsScreen(
                            data = stats ?: StatsRepository.localOnly(this@HomeActivity),
                            onToggleCategoryMonth = { toggleCategoryMonth() },
                        )
                        2 -> ChallengeScreen(
                            ui = challengeUi,
                            savedName = ChallengeStore.myName(this@HomeActivity),
                            onCreate = { roomName, myName -> createChallenge(roomName, myName) },
                            onJoin = { code, myName -> joinChallenge(code, myName) },
                            onLeave = { leaveChallenge() },
                        )
                        else -> HomeScreen(
                            today = LocalDate.now(),
                            snapshots = snapshots,
                            notice = notice,
                            onOpenSettings = { startActivity(Intent(this@HomeActivity, MainActivity::class.java)) },
                            onRecord = { startActivity(Intent(this@HomeActivity, QuickInputActivity::class.java)) },
                        )
                    }
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun BottomBar() {
        NavigationBar(containerColor = HomePalette.Card) {
            NavigationBarItem(
                selected = tab == 0,
                onClick = { tab = 0 },
                icon = { Icon(painterResource(R.drawable.ic_tab_home), contentDescription = "홈", modifier = Modifier.size(23.dp)) },
                label = { Text("홈") },
                colors = navColors(),
            )
            NavigationBarItem(
                selected = tab == 1,
                onClick = { selectStats() },
                icon = { Icon(painterResource(R.drawable.ic_tab_stats), contentDescription = "통계", modifier = Modifier.size(23.dp)) },
                label = { Text("통계") },
                colors = navColors(),
            )
            NavigationBarItem(
                selected = tab == 2,
                onClick = { selectChallenge() },
                icon = { Icon(painterResource(R.drawable.ic_tab_challenge), contentDescription = "챌린지", modifier = Modifier.size(23.dp)) },
                label = { Text("챌린지") },
                colors = navColors(),
            )
        }
    }

    @androidx.compose.runtime.Composable
    private fun navColors() = NavigationBarItemDefaults.colors(
        selectedIconColor = HomePalette.AccentBright,
        selectedTextColor = HomePalette.Accent,
        indicatorColor = HomePalette.Soft,
        unselectedIconColor = HomePalette.Muted,
        unselectedTextColor = HomePalette.Muted,
    )

    override fun onResume() {
        super.onResume()
        refresh()
        republishNotification()
        resyncInBackground()
        if (tab == 2) loadChallenge()
    }

    override fun onDestroy() {
        challengeReg?.cancel()
        io.shutdown()
        super.onDestroy()
    }

    /** 통계 탭으로 옮기며 데이터를 채운다. 기간 비교는 즉시, 카테고리는 노션에서 뒤따라. */
    private fun selectStats() {
        tab = 1
        loadStats()
    }

    /** 챌린지 탭으로 옮기며 참가 상태를 확인·구독한다. */
    private fun selectChallenge() {
        tab = 2
        loadChallenge()
    }

    private fun toggleCategoryMonth() {
        categoryMonthBack = if (categoryMonthBack == 0) 1 else 0
        loadStats()
    }

    private fun loadStats() {
        val today: LocalDate = LocalDate.now()
        val base: StatsData = StatsRepository.localOnly(this, today)
        val month: YearMonth = YearMonth.from(today).minusMonths(categoryMonthBack.toLong())
        val label: String = if (categoryMonthBack == 0) "이번 달" else "지난 달"
        stats = base.copy(categoryMonthLabel = label, loadingCategories = true, error = null)

        val app = applicationContext
        io.execute {
            val (totals: Map<String, Long>, error: String?) =
                try {
                    StatsRepository.fetchCategories(app, month)
                } catch (e: Exception) {
                    emptyMap<String, Long>() to "오류: ${e.message ?: e.javaClass.simpleName}"
                }

            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                stats = base.copy(
                    categoryMonthLabel = label,
                    categories = CategoryBreakdown.of(totals),
                    categoryTotal = CategoryBreakdown.total(totals),
                    loadingCategories = false,
                    error = error,
                )
            }
        }
    }

    /**
     * 참가 중이면 이번 주 성적을 올리고 순위를 실시간 구독한다. 참가 전이면 빈 화면을 보인다.
     *
     * 순위 계산은 [ChallengeStandings] 가 한다 — 이 화면은 방에서 받은 참가자 목록을 세우기만 한다.
     */
    private fun loadChallenge() {
        val challengeId: String? = challenge.joinedChallengeId
        if (challengeId == null) {
            challengeReg?.cancel()
            challengeReg = null
            challengeUi = ChallengeUi(joined = false)
            return
        }

        val today: LocalDate = LocalDate.now()
        val weekKey: String = ChallengeWeek.key(today)
        challengeUi = ChallengeUi(joined = false, loading = true)

        val app = applicationContext
        challenge.ensureSignedIn { uid ->
            if (isFinishing || isDestroyed) return@ensureSignedIn
            if (uid == null) {
                challengeUi = ChallengeUi(joined = false, error = "로그인에 실패했어요. 네트워크를 확인해 주세요.")
                return@ensureSignedIn
            }

            // 내 이번 주 성적을 올린다 — 캐시 읽기는 백그라운드에서.
            val myName: String = ChallengeStore.myName(app).ifBlank { "나" }
            io.execute {
                val (spent: Long, budget: Long) = ChallengeWeek.myWeek(app, today)
                challenge.pushMyWeek(challengeId, weekKey, myName, spent, budget)
            }

            challengeReg?.cancel()
            challengeReg = challenge.observe(challengeId, weekKey) { result ->
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    result.onSuccess { room ->
                        challengeUi = ChallengeUi(
                            joined = true,
                            name = room.name,
                            code = room.code,
                            standings = ChallengeStandings.rank(room.members),
                            myUid = uid,
                            weekLabel = ChallengeWeek.label(today),
                            daysLeftText = daysLeftText(today),
                            error = null,
                        )
                    }
                    result.onFailure {
                        challengeUi = ChallengeUi(joined = false, error = it.message ?: "불러오지 못했어요.")
                    }
                }
            }
        }
    }

    private fun createChallenge(roomName: String, myName: String) {
        ChallengeStore.setMyName(this, myName)
        challengeUi = ChallengeUi(loading = true)
        challenge.createChallenge(roomName.trim(), myName.trim()) { result ->
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { loadChallenge() }
                result.onFailure { challengeUi = ChallengeUi(joined = false, error = it.message ?: "방을 만들지 못했어요.") }
            }
        }
    }

    private fun joinChallenge(code: String, myName: String) {
        ChallengeStore.setMyName(this, myName)
        challengeUi = ChallengeUi(loading = true)
        challenge.joinChallenge(code, myName.trim()) { result ->
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { loadChallenge() }
                result.onFailure { challengeUi = ChallengeUi(joined = false, error = it.message ?: "참가하지 못했어요.") }
            }
        }
    }

    private fun leaveChallenge() {
        challenge.leave()
        challengeReg?.cancel()
        challengeReg = null
        challengeUi = ChallengeUi(joined = false)
    }

    private fun daysLeftText(today: LocalDate): String {
        val left: Int = ChallengeWeek.daysLeft(today)
        return if (left <= 0) "오늘 마지막 날" else "${left}일 남음"
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
                // 통계 탭을 보고 있으면 캐시 갱신을 반영한다.
                if (tab == 1) loadStats()
            }
        }
    }
}
