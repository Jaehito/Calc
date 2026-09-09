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
import java.time.LocalTime
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

    /** 이번 달 고정비 합계. 0 이면 홈에 그 카드를 그리지 않는다. */
    private var fixedTotal: Long by mutableStateOf(0L)
    private var notice: String? by mutableStateOf(null)

    private var stats: StatsData? by mutableStateOf(null)
    /** 카테고리 막대가 보는 달. 0 = 이번 달, 1 = 지난 달. */
    private var categoryMonthBack: Int by mutableStateOf(0)

    private val challenge: ChallengeRepository by lazy { FirestoreChallengeRepository(this) }
    private var challengeUi: ChallengeUi by mutableStateOf(ChallengeUi())
    private var challengeReg: Cancellable? = null

    /** 기록 안 한 결제 수집함. 비어 있지 않으면 앱을 열 때 한 번 물어본다. */
    private var inbox: PendingInboxUi by mutableStateOf(PendingInboxUi())
    private var inboxVisible: Boolean by mutableStateOf(false)

    /** 막 끝난 주기 결산. 월급날이 지난 걸 처음 확인한 순간에만 채워진다. */
    private var cycleGrade: SpendingGrade.Graded? by mutableStateOf(null)

    /** 그 팝업에 함께 붙는 새 주기 추천 금액. 월급을 안 적었으면 비어 있다. */
    private var cyclePlan: FixedCostPlan by mutableStateOf(FixedCostPlan())

    /** 어제 등급. B 이상일 때만 채워진다 ([GradeDelivery]) — 하루 한 번. */
    private var dailyGrade: SpendingGrade.Graded? by mutableStateOf(null)

    /** 어제 하루치에서 아껴 곳간으로 간 돈. 0 이면 팝업에서 그 줄을 생략한다. */
    private var dailySaved: Long by mutableStateOf(0L)

    /**
     * 이번에 앱을 연 뒤 수집함을 이미 띄웠는지.
     *
     * onResume 마다 띄우면 설정·내역에 갔다 돌아올 때도 다시 열려 «나중에»가 무의미해진다.
     * 화면을 새로 만들 때(앱을 새로 열 때) 한 번만 띄운다.
     */
    private var inboxAsked: Boolean = false

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
                            fixedTotal = fixedTotal,
                            onSetBudget = { startActivity(Intent(this@HomeActivity, OnboardingActivity::class.java)) },
                            onOpenSettings = { startActivity(Intent(this@HomeActivity, MainActivity::class.java)) },
                            onOpenHistory = { purse -> openHistory(purse) },
                            onRecord = {
                                startActivity(
                                    Intent(this@HomeActivity, QuickInputActivity::class.java),
                                )
                            },
                        )
                    }

                    // 한 번에 하나만 띄운다. 기록(수집함)이 채점보다 먼저다 — 수집함에서 한 건
                    // 적으면 등급이 달라지므로 순서가 결과를 바꾼다. 그 다음이 주기 결산(진짜
                    // 평가), 마지막이 어제 등급(격려)이다.
                    val inboxOpen: Boolean = inboxVisible && inbox.items.isNotEmpty()
                    if (!inboxOpen) {
                        val ended: SpendingGrade.Graded? = cycleGrade
                        val yesterday: SpendingGrade.Graded? = dailyGrade
                        if (ended != null) {
                            CycleGradeDialog(
                                grade = ended,
                                recommended = if (cyclePlan.canRecommend) cyclePlan.recommended else 0L,
                                fixedTotal = cyclePlan.fixedTotal,
                                monthlyIncome = cyclePlan.monthlyIncome,
                                onApply = { applyRecommendedBudget() },
                                onDismiss = { cycleGrade = null },
                            )
                        } else if (yesterday != null) {
                            DailyGradeDialog(
                                grade = yesterday,
                                saved = dailySaved,
                                onDismiss = { dailyGrade = null },
                            )
                        }
                    }

                    if (inboxVisible && inbox.items.isNotEmpty()) {
                        PendingInboxDialog(
                            ui = inbox,
                            onRecord = { item, purse, name, amount, category ->
                                recordPending(item, purse, name, amount, category)
                            },
                            onIgnore = { item -> ignorePending(item) },
                            onBlockSender = { item -> blockSender(item) },
                            onBlockApp = { item -> blockApp(item) },
                            onDismiss = { inboxVisible = false },
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
        refreshInbox(show = !inboxAsked)
        republishNotification()
        resyncInBackground()
        checkCycleGrade()
        checkDailyGrade()
        if (tab == 2) loadChallenge()
    }

    /**
     * 주기가 바뀐 걸 처음 확인하는 순간, 막 끝난 주기를 한 번만 결산해 보여준다.
     *
     * [CycleGradeStore] 에 마지막으로 본 주기 시작일을 기억해 둔다 — 그 값과 지금 주기
     * 시작일이 다르면 최소 한 번은 주기가 바뀐 것이다. 처음 쓰는 사람(저장된 값이 없음)에게는
     * 비교할 지난 주기가 없으므로 그냥 지금 주기를 기억만 하고 넘어간다.
     */
    private fun checkCycleGrade() {
        val settings: Settings = SettingsStore.load(this)
        if (!PurseAccess.isReady(this)) return

        val today: LocalDate = LocalDate.now()
        val currentCycle: BudgetCycle = Payday.cycleOf(today, settings.payDay)
        val lastSeen: LocalDate? = CycleGradeStore.lastSeenStart(this)

        if (lastSeen == null || lastSeen == currentCycle.start) {
            CycleGradeStore.setLastSeenStart(this, currentCycle.start)
            return
        }

        val endedCycle: BudgetCycle = Payday.cycleOf(currentCycle.start.minusDays(1), settings.payDay)
        CycleGradeStore.setLastSeenStart(this, currentCycle.start)

        val result: SpendingGrade = GradeRepository.cycle(this, endedCycle)
        if (result !is SpendingGrade.Graded) return

        cyclePlan = FixedCostStore.load(this)
        cycleGrade = result
    }

    /**
     * 결산 팝업의 «적용» — 추천 금액을 개인 곳간 예산에 넣는다.
     *
     * 공용 곳간은 건드리지 않는다. 고정비 파이프라인은 개인 곳간만 정한다(첫 시작과 같은 규칙).
     */
    private fun applyRecommendedBudget() {
        val amount: Long = cyclePlan.recommended
        cycleGrade = null
        if (amount <= 0L) return

        val settings: Settings = SettingsStore.load(this)
        SettingsStore.save(this, settings.copy(personal = settings.personal.copy(monthlyBudget = amount)))
        refresh()
        NotificationHelper.show(this)
    }

    /**
     * 어제 등급을 하루에 한 번 보여준다.
     *
     * **오늘이 아니라 어제다** — 오늘은 아직 끝나지 않았으므로 채점할 수 없다. 그리고
     * [GradeDelivery] 가 B 이상만 통과시키므로 나쁜 날은 조용히 넘어간다. 다만 그런 날도
     * «봤다»고 기억해 둔다 — 안 그러면 다음 날 앱을 열 때마다 지난 나쁜 날을 다시 채점하려 든다.
     */
    private fun checkDailyGrade() {
        if (!PurseAccess.isReady(this)) return

        val yesterday: LocalDate = LocalDate.now().minusDays(1)
        if (DailyGradeStore.lastShownDay(this) == yesterday) return
        DailyGradeStore.setLastShownDay(this, yesterday)

        val result: SpendingGrade = GradeRepository.day(this, yesterday)
        if (!GradeDelivery.shouldSend(GradePeriod.DAILY, result)) return

        val graded: SpendingGrade.Graded = result as SpendingGrade.Graded
        dailySaved = maxOf(0L, graded.budget - graded.spent)
        dailyGrade = graded
    }

    /**
     * 수집함을 다시 읽는다.
     *
     * @param show 비어 있지 않으면 팝업을 띄울지. 앱을 새로 연 첫 onResume 에서만 true —
     *   사용자가 «나중에»로 닫은 뒤 항목을 처리하거나 다른 화면에 갔다 올 때마다 다시 열리면 안 된다.
     */
    private fun refreshInbox(show: Boolean = false) {
        val settings: Settings = SettingsStore.load(this)
        val purses: List<Purse> = PurseAccess.linked(this)
        val items: List<PendingPayment> = PendingPaymentStore.load(this)

        inbox = inbox.copy(
            items = items,
            purses = purses,
            purseLabels = purses.associateWith { settings.labelOf(it) },
            categories = CategoryStore.load(this),
            busyId = null,
        )
        if (items.isEmpty()) {
            inboxVisible = false
        } else if (show) {
            inboxVisible = true
            inboxAsked = true
        }
    }

    /** 수집함 한 건을 기록한다. 성공해야 수집함에서 뺀다 — 실패하면 다시 시도할 수 있어야 한다. */
    private fun recordPending(
        item: PendingPayment,
        purse: Purse,
        name: String,
        amount: Long,
        category: String,
    ) {
        if (amount <= 0L || name.isBlank()) return
        inbox = inbox.copy(busyId = item.id, message = null, messageIsError = false)

        val app = applicationContext
        val now: String = LocalTime.now().format(ReplyReceiver.TIME_FORMAT)
        val expense = Expense(name = name, amount = amount, category = category)

        io.execute {
            val result: RecordResult = try {
                RecordExpense.record(app, expense, purse.key, now)
            } catch (e: Exception) {
                RecordResult(ok = false, lines = StatusText.failed("오류: ${e.message ?: e.javaClass.simpleName}", now))
            }
            if (result.ok) PendingPaymentStore.remove(app, item.id)

            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                refreshInbox()
                refresh()
                if (!result.ok) {
                    inbox = inbox.copy(message = result.lines.summary, messageIsError = true)
                }
            }
        }
    }

    private fun ignorePending(item: PendingPayment) {
        PendingPaymentStore.remove(this, item.id)
        refreshInbox()
    }

    /** 이 발신자에서 온 것은 앞으로 수집하지 않는다. 이미 쌓인 것도 함께 치운다. */
    private fun blockSender(item: PendingPayment) {
        PaymentBlocklist.blockSender(this, item.sender)
        PendingPaymentStore.removeFrom(this, sender = item.sender)
        refreshInbox()
    }

    /** 이 앱에서 온 것은 앞으로 수집하지 않는다. 이미 쌓인 것도 함께 치운다. */
    private fun blockApp(item: PendingPayment) {
        PaymentBlocklist.blockPackage(this, item.packageName)
        PendingPaymentStore.removeFrom(this, packageName = item.packageName)
        refreshInbox()
    }

    override fun onDestroy() {
        challengeReg?.cancel()
        io.shutdown()
        super.onDestroy()
    }

    /** 곳간 카드를 누르면 그 곳간의 내역 화면을 연다. */
    private fun openHistory(purse: Purse) {
        startActivity(
            Intent(this, PurseHistoryActivity::class.java)
                .putExtra(PurseHistoryActivity.EXTRA_PURSE, purse.key),
        )
    }

    /** 통계 탭으로 옮기며 데이터를 채운다. 기간 비교는 즉시, 카테고리는 저장소에서 뒤따라. */
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

    /** 로컬 캐시만으로 즉시 그린다. 저장소 대조는 그 뒤에 따라온다. */
    private fun refresh() {
        val today: LocalDate = LocalDate.now()
        snapshots = PurseAccess.linked(this)
            .mapNotNull { Ledger.snapshot(this, it, today) }
        fixedTotal = FixedCostStore.load(this).fixedTotal
    }

    /**
     * 셰이드에 남아 있던 옛 알림은 옛 버전의 PendingIntent 를 들고 있어 눌러도 아무 데도 안 간다.
     * 앱을 열 때마다 지금 버전으로 갈아 끼운다.
     */
    private fun republishNotification() {
        if (NotificationState.isOn(this)) {
            NotificationHelper.show(this)
            // 주 1회 돌아보기·매일 저녁 9시 등급·한 시간마다 카드 다시 올리기 예약을 확인·갱신한다.
            // 예약이 사라졌어도 앱을 열면 되살아난다.
            WeeklyReviewScheduler.schedule(this)
            GradeScheduler.schedule(this)
            CardRefreshScheduler.schedule(this)
        }
    }

    /**
     * 저장소를 기준으로 캐시를 다시 맞춘다.
     *
     * 잠금화면 기록은 로컬 사본만 더하고 끝낸다 — 브로드캐스트 수명 안에 왕복을 두 번 할 수 없다.
     * 그래서 다른 기기에서 적은 기록은 여기서만 반영된다.
     */
    private fun resyncInBackground() {
        val app = applicationContext
        val purses: List<Purse> = PurseAccess.linked(this)
        if (purses.isEmpty()) return

        notice = "맞추는 중…"
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
