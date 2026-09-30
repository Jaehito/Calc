package com.calc.expense

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.Executors

/**
 * 앱을 열면 나오는 화면. 하단 탭으로 홈·통계·도감을 오간다.
 *
 * 설정과 빠른 입력은 XML 그대로다 — 잘 도는 화면을 다시 만들 이유가 없다.
 * 여기만 Compose 인 이유는 이 화면들이 새로 만드는 화면이기 때문이다.
 */
class HomeActivity : ComponentActivity() {

    companion object {
        /** 결제 배너로 들어왔다는 표시. 수집함을 반드시 한 번 연다([NotificationHelper]). */
        const val EXTRA_OPEN_INBOX = "openInbox"
        private const val STATE_INBOX_ASKED = "inboxAsked"
        private const val PREFS = "home"
        private const val KEY_PURSE = "purse"
    }

    private val io = Executors.newSingleThreadExecutor()

    private var tab: Int by mutableStateOf(0)
    /** 연결된 지갑들과 각 지갑의 오늘 숫자(예산이 없으면 null). */
    private var purses: List<Purse> by mutableStateOf(emptyList())
    private var snapshots: Map<Purse, LedgerSnapshot?> by mutableStateOf(emptyMap())
    private var purseLabels: Map<Purse, String> by mutableStateOf(emptyMap())
    /** 지갑마다의 주기 경계. 공용은 가정이 정한 목표날을 쓴다. */
    private var payDays: Map<Purse, Int> by mutableStateOf(emptyMap())

    /** 홈 토글로 고른 지갑. 마지막에 고른 쪽을 기억한다. */
    private var purse: Purse by mutableStateOf(Purse.PERSONAL)
    private var notice: String? by mutableStateOf(null)

    /** 지갑마다의 통계. 토글을 오갈 때 이미 읽은 쪽은 바로 그린다. */
    private var stats: Map<Purse, StatsData> by mutableStateOf(emptyMap())
    /** 카테고리 막대가 보는 주기. 0 = 이번 주기, 1 = 지난 주기. 달력 달이 아니라 월급날 기준이다. */
    private var categoryCycleBack: Int by mutableStateOf(0)

    /** 도감. 저장해 둔 것으로 먼저 그리고, 저장소에서 다시 세면 덧댄다([DogamStore]). */
    private var dogam: DogamUi by mutableStateOf(DogamUi())

    /** 어제 등급 팝업에 붙일, 새로 핀 꽃. 도감 탭에서 이미 봤으면 비운다. */
    private var newBlooms: List<Plant> by mutableStateOf(emptyList())

    /** 도감을 다시 세는 중. 탭을 오가며 여러 번 눌러도 한 번만 돈다. */
    private var dogamBusy: Boolean = false

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

    /** 이 화면을 그린 언어. 설정에서 언어를 바꾸고 돌아오면 다시 그린다. */
    private val createdLang: Lang = L10n.lang

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 언어를 바꿔 다시 그릴 때 수집함을 또 띄우지 않는다.
        inboxAsked = savedInstanceState?.getBoolean(STATE_INBOX_ASKED) == true
        setContent {
            Scaffold(bottomBar = { BottomBar() }) { padding ->
                Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                    when (tab) {
                        1 -> StatsScreen(
                            data = stats[purse] ?: StatsRepository.localOnly(this@HomeActivity, purse),
                            purse = purse,
                            purses = purses,
                            purseLabels = purseLabels,
                            onSelectPurse = { selectPurse(it) },
                            onToggleCategoryMonth = { toggleCategoryMonth() },
                            onOpenReport = { openCycleReport(purse) },
                            onOpenCategory = { name -> openCategoryDetail(name) },
                            onOpenSettings = { openSettings() },
                        )
                        2 -> DogamScreen(ui = dogam, today = LocalDate.now(), onOpenSettings = { openSettings() })
                        else -> HomeScreen(
                            snapshots = snapshots,
                            purse = purse,
                            purses = purses,
                            purseLabels = purseLabels,
                            payDays = payDays,
                            notice = notice,
                            onSelectPurse = { selectPurse(it) },
                            onSetBudget = { startActivity(Intent(this@HomeActivity, OnboardingActivity::class.java)) },
                            onOpenSettings = { openSettings() },
                            onOpenHistory = { p -> openHistory(p) },
                            onRecord = {
                                startActivity(
                                    Intent(this@HomeActivity, QuickInputActivity::class.java),
                                )
                            },
                            onEditBudget = { p -> openSettings(if (p == Purse.SHARED) MainActivity.EDIT_SHARED_BUDGET else MainActivity.EDIT_BUDGET) },
                            onEditPayday = { p -> openSettings(if (p == Purse.SHARED) MainActivity.EDIT_SHARED_PAYDAY else MainActivity.EDIT_PAYDAY) },
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
                                onOpenReport = {
                                    cycleGrade = null
                                    // 결산 팝업은 개인 곳간의 결산이다.
                                    openCycleReport(Purse.PERSONAL)
                                },
                                onDismiss = { cycleGrade = null },
                            )
                        } else if (yesterday != null) {
                            DailyGradeDialog(
                                grade = yesterday,
                                saved = dailySaved,
                                blooms = newBlooms,
                                onDismiss = {
                                    dailyGrade = null
                                    announceBlooms()
                                },
                                onOpenDogam = {
                                    dailyGrade = null
                                    selectDogam()
                                },
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

    /**
     * 하단 탭. 머티리얼 기본 모양(고른 탭 뒤 알약)은 ‘기본 틀’로 읽혀서, 채운 아이콘에
     * 고른 탭만 짙은 글자로 가른다.
     */
    @androidx.compose.runtime.Composable
    private fun BottomBar() {
        Column(modifier = Modifier.fillMaxWidth().background(HomePalette.Card).navigationBarsPadding()) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(HomePalette.Ground))
            Row(modifier = Modifier.fillMaxWidth().height(62.dp), verticalAlignment = Alignment.CenterVertically) {
                TabItem(R.drawable.ic_tab_home, tr("홈", "Home", "Inicio"), tab == 0) { tab = 0 }
                TabItem(R.drawable.ic_tab_stats, tr("통계", "Stats", "Estadísticas"), tab == 1) { selectStats() }
                TabItem(R.drawable.ic_tab_dogam, tr("도감", "Garden", "Jardín"), tab == 2) { selectDogam() }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun androidx.compose.foundation.layout.RowScope.TabItem(icon: Int, label: String, selected: Boolean, onClick: () -> Unit) {
        val color: Color = if (selected) HomePalette.Ink else TabIdle
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .weight(1f)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        ) {
            Icon(painterResource(icon), contentDescription = label, tint = color, modifier = Modifier.size(25.dp))
            Spacer(Modifier.height(3.dp))
            Text(label, color = color, fontSize = 11.5f.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
        }
    }

    /** singleTop 이라 이미 떠 있으면 여기로 온다. 새 인텐트를 받아 둬야 수집함 표시가 살아난다. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_INBOX_ASKED, inboxAsked)
    }

    override fun onResume() {
        super.onResume()
        if (createdLang != L10n.lang) {
            recreate()
            return
        }
        refresh()
        // 배너로 들어왔으면 이미 한 번 띄웠더라도 다시 연다. 그 배너를 누른 이유가 그것이다.
        val fromBanner: Boolean = intent?.getBooleanExtra(EXTRA_OPEN_INBOX, false) == true
        if (fromBanner) intent.removeExtra(EXTRA_OPEN_INBOX)
        refreshInbox(show = fromBanner || !inboxAsked)
        republishNotification()
        resyncInBackground()
        // 개인 설정을 계정에 맡겨 둔다(프로세스마다 한 번). 재설치하면 로그인 때 되찾는다.
        PersonalBackupSync.ensureBackedUp(this)
        // 다시 설치했으면 가정 연결을 되찾는다. 되찾으면 공용 곳간이 바로 보이게 다시 그린다.
        HouseholdSync.restoreIfMissing(this) { restored ->
            if (!restored || isFinishing || isDestroyed) return@restoreIfMissing
            refresh()
            republishNotification()
            resyncInBackground()
        }
        checkCycleGrade()
        checkDailyGrade()
        // 카테고리를 펼쳐 다시 분류하고 돌아오면 도넛이 달라져 있어야 한다. 대조(resync)
        // 끝에도 한 번 부르지만 그건 네트워크를 타므로, 돌아온 자리에서 바로 한 번 더 읽는다.
        if (tab == 1) loadStats()
        newBlooms = DogamStore.unannounced(this)
        refreshDogam(force = tab == 2)
        pullHouseholdSettings()
    }

    /**
     * 배우자가 공용 예산·월급날을 바꿨으면 받아 온다([HouseholdSync]). 바뀌었으면 홈 카드·잠금화면
     * 카드가 새 예산을 보도록 다시 그리고, 월급날이 옮겨졌을 수 있으니 주기 캐시도 다시 맞춘다.
     */
    private fun pullHouseholdSettings() {
        HouseholdSync.pull(this) { result ->
            if (result != HouseholdPull.CHANGED || isFinishing || isDestroyed) return@pull
            refresh()
            republishNotification()
            resyncInBackground()
        }
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

    /** 주기 리포트 화면을 연다. 결산 팝업과 통계 탭 두 곳에서 같은 곳으로 보낸다. */
    private fun openCycleReport(purse: Purse) {
        startActivity(CycleReportActivity.intent(this, purse))
    }

    /**
     * 도넛 조각 하나를 이름별로 펼친다. 범위는 **지금 도넛이 보고 있는 주기**와 같아야 한다 —
     * 화면에 「이번 주기 620,000원」이라 써 놓고 펼쳤더니 다른 합계가 나오면 숫자를 못 믿는다.
     *
     * 도넛의 «미분류»는 저장된 값이 빈 문자열이다([CategoryBreakdown.UNCATEGORIZED]).
     */
    private fun openCategoryDetail(sliceName: String) {
        val cycle: BudgetCycle = categoryCycle()
        val category: String = if (sliceName == CategoryBreakdown.UNCATEGORIZED) "" else sliceName
        startActivity(
            CategoryDetailActivity.intent(this, purse, category, cycle.start, cycle.lastDay),
        )
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
                RecordResult(ok = false, lines = StatusText.failed(StatusText.error(e), now))
            }
            if (result.ok) {
                PendingPaymentStore.remove(app, item.id)
                // 알림이 읽은 이름을 사용자가 어떻게 고쳤는지 기억한다. 다음 결제 알림부터는
                // 카드가 이 이름으로 떠서 같은 수정을 되풀이하지 않는다. 열쇠는 기억으로 바뀌기
                // **전** 이름이다 — 바뀐 이름을 열쇠로 쓰면 다시 고쳐도 원래 기억이 안 바뀐다.
                NameMemoryStore.remember(app, item.memoryKey, name)
            }

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
        io.shutdown()
        super.onDestroy()
    }

    /** 설정을 연다. [edit] 를 주면 그 값을 고치는 창이 바로 뜨고, 닫으면 홈으로 돌아온다. */
    private fun openSettings(edit: String? = null) {
        val intent = Intent(this, MainActivity::class.java)
        if (edit != null) intent.putExtra(MainActivity.EXTRA_EDIT, edit)
        startActivity(intent)
    }

    /** 홈·통계 토글. 둘이 같은 선택이다. 고른 쪽을 기억해 다음에 열어도 같은 지갑이 보인다. */
    private fun selectPurse(next: Purse) {
        if (next == purse) return
        purse = next
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_PURSE, next.key).apply()
        if (tab == 1) loadStats()
    }

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

    /** 도감 탭으로 옮긴다. 저장해 둔 것으로 바로 그리고, 지난 기록을 다시 세어 덧댄다. */
    private fun selectDogam() {
        tab = 2
        dogam = dogam.copy(fresh = emptySet())
        showDogam(DogamStore.load(this))
        refreshDogam(force = true)
    }

    private fun toggleCategoryMonth() {
        categoryCycleBack = if (categoryCycleBack == 0) 1 else 0
        loadStats()
    }

    /**
     * 카테고리 도넛이 보고 있는 주기. 달력 달이 아니라 **월급날 기준**이다 — 곳간·등급이
     * 전부 월급날부터 다음 월급날 전날까지를 한 덩어리로 보는데 카테고리만 1일부터 세면
     * 같은 화면의 숫자들이 서로 다른 기간을 말하게 된다.
     */
    private fun categoryCycle(today: LocalDate = LocalDate.now()): BudgetCycle =
        Payday.cycleBefore(today, SettingsStore.load(this).payDayOf(purse), categoryCycleBack)

    /** 고른 지갑의 통계를 채운다. 토글·주기를 빨리 오가도 늦게 온 옛 결과가 덮어쓰지 않는다. */
    private fun loadStats() {
        val today: LocalDate = LocalDate.now()
        val target: Purse = purse
        val back: Int = categoryCycleBack
        val base: StatsData = StatsRepository.localOnly(this, target, today)
        val cycle: BudgetCycle = categoryCycle(today)
        val label: String =
            if (categoryCycleBack == 0) tr("이번 주기", "This cycle", "Este ciclo")
            else tr("지난 주기", "Last cycle", "Ciclo anterior")
        val range: String = StatusText.cycleRange(cycle)
        // 이미 본 도넛이 있으면 그대로 두고 막대·합계만 새로 — 토글을 오갈 때 빈 도넛이 번쩍이지 않게.
        val previous: StatsData? = stats[target]?.takeIf { it.categoryCycleRange == range }
        stats = stats + (target to base.copy(
            categoryCycleLabel = label,
            categoryCycleRange = range,
            categories = previous?.categories.orEmpty(),
            categoryTotal = previous?.categoryTotal ?: 0L,
            loadingCategories = true,
            error = null,
        ))

        val app = applicationContext
        io.execute {
            val (totals: Map<String, Long>, error: String?) =
                try {
                    StatsRepository.fetchCategories(app, target, cycle)
                } catch (e: Exception) {
                    emptyMap<String, Long>() to StatusText.error(e)
                }

            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (back != categoryCycleBack) return@runOnUiThread
                stats = stats + (target to base.copy(
                    categoryCycleLabel = label,
                    categoryCycleRange = range,
                    categories = CategoryBreakdown.of(totals),
                    categoryTotal = CategoryBreakdown.total(totals),
                    loadingCategories = false,
                    error = error,
                ))
            }
        }
    }

    /**
     * 도감을 그린다. NEW 는 이번에 처음 보는 꽃에만 붙이고, 본 것으로 적어 둔다. 도감에서 본 꽃은
     * 어제 등급 팝업에 다시 붙일 이유가 없으므로 알린 것으로도 친다.
     */
    private fun showDogam(result: DogamResult, loading: Boolean = dogam.loading, error: String? = dogam.error) {
        val unseen: Set<Plant> = DogamStore.unseen(this)
        DogamStore.markSeen(this, unseen)
        announceBlooms(DogamStore.unannounced(this))
        val settings: Settings = SettingsStore.load(this)
        dogam = DogamUi(
            result = result,
            fresh = dogam.fresh + unseen,
            loading = loading,
            error = error,
            purseLabels = Purse.entries.associateWith { settings.labelOf(it) },
        )
    }

    /** 팝업에 붙였던 꽃을 알린 것으로 적는다. 다음 팝업에 같은 꽃이 또 붙지 않게. */
    private fun announceBlooms(plants: List<Plant> = newBlooms) {
        DogamStore.markAnnounced(this, plants)
        newBlooms = emptyList()
    }

    /**
     * 저장소에서 처음부터 다시 세어 도감을 덧댄다. 도감 탭을 열 때마다, 아니면 앱을 열 때 하루 한 번 —
     * 꽃은 대부분 하루가 끝나야 피므로 그보다 자주 셀 필요가 없다. 새로 핀 꽃은 도감 탭을 보고 있으면
     * 바로 NEW 로, 아니면 다음 어제 등급 팝업에 한 줄로 알린다.
     */
    private fun refreshDogam(force: Boolean) {
        if (!PurseAccess.isReady(this) || dogamBusy) return
        val today: LocalDate = LocalDate.now()
        if (!force && DogamStore.evaluatedOn(this) == today) return

        dogamBusy = true
        if (tab == 2) dogam = dogam.copy(loading = true, error = null)
        val app = applicationContext
        io.execute {
            val load: DogamLoad = try {
                DogamRepository.refresh(app, today)
            } catch (e: Exception) {
                DogamLoad(DogamStore.load(app), StatusText.error(e))
            }

            runOnUiThread {
                dogamBusy = false
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (tab == 2) {
                    showDogam(load.result, loading = false, error = load.error)
                } else {
                    dogam = dogam.copy(result = load.result, loading = false, error = load.error)
                    newBlooms = DogamStore.unannounced(this)
                }
            }
        }
    }

    /** 로컬 캐시만으로 즉시 그린다. 저장소 대조는 그 뒤에 따라온다. */
    private fun refresh() {
        val today: LocalDate = LocalDate.now()
        val settings: Settings = SettingsStore.load(this)
        val linked: List<Purse> = PurseAccess.linked(this)
        purses = linked
        snapshots = linked.associateWith { Ledger.snapshot(this, it, today) }
        purseLabels = linked.associateWith { settings.labelOf(it) }
        payDays = linked.associateWith { settings.payDayOf(it) }
        // 기억해 둔 지갑이 연결에서 빠졌으면(가정 연결 해제 등) 개인으로 돌아온다.
        val saved: String? = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_PURSE, null)
        purse = linked.firstOrNull { it.key == saved } ?: linked.firstOrNull() ?: Purse.PERSONAL
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

        notice = tr("맞추는 중…", "Syncing…", "Sincronizando…")
        io.execute {
            var failure: String? = null
            for (purse in purses) {
                val error: String? = try {
                    Ledger.resync(app, purse)
                } catch (e: Exception) {
                    StatusText.error(e)
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

/** 하단 탭에서 고르지 않은 탭의 아이콘·글자 색. */
private val TabIdle = Color(0xFFB0B8C1)
