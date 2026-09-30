package com.calc.expense

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.firebase.auth.FirebaseAuth

/**
 * 첫 시작 — 한 달 예산을 정해 개인 곳간에 넣고, 잠금화면 알림까지 켠 채 홈으로 보낸다.
 *
 * 로그인 직후 [LoginActivity] 가 [Onboarding.shouldShow] 로 판단해 [EXTRA_FIRST_RUN] 을 달고
 * 여기로 보낸다. 설정·홈의 «고정비로 계산하기»도 같은 화면을 연다(엑스트라 없이) — 그때는 월급
 * 단계부터 시작하고 알림은 묻지 않는다([Onboarding.firstStep]).
 *
 * **개인 곳간만 정한다.** 공용 곳간 예산은 가정 코드로 묶으면 가정에서 받아 온다([JOIN] 단계).
 */
class OnboardingActivity : ComponentActivity() {

    companion object {
        /** 로그인 직후 처음 들어왔다는 표시. 금액 한 칸에서 시작하고 끝에 알림을 묻는다. */
        const val EXTRA_FIRST_RUN = "firstRun"

        fun firstRun(context: Context): Intent =
            Intent(context, OnboardingActivity::class.java).putExtra(EXTRA_FIRST_RUN, true)
    }

    private var ui: OnboardingUi by mutableStateOf(OnboardingUi())

    private val firstRun: Boolean
        get() = intent?.getBooleanExtra(EXTRA_FIRST_RUN, false) == true

    private val firstStep: OnboardingStep
        get() = Onboarding.firstStep(firstRun)

    /** 화면이 그리는 계산 결과. 입력칸 문자열에서 매번 다시 만든다 — 상태를 두 벌 들지 않는다. */
    private val plan: FixedCostPlan
        get() = FixedCostPlan(
            monthlyIncome = readAmount(ui.incomeText),
            items = FixedCosts.clean(
                ui.rows.map { FixedCostItem(name = it.name, amount = readAmount(it.amountText)) },
            ),
        )

    /** 권한 창의 답. 거절해도 홈으로 간다 — 설정의 스위치로 언제든 다시 켤 수 있다. */
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) LockCard.enable(this)
            goHome()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        restore()

        // 뒤로가기는 앱을 나가는 게 아니라 앞 단계로 돌아간다. 시작한 단계에서만 나간다.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (ui.step == OnboardingStep.NOTIFY) {
                    goHome()
                    return
                }
                val previous: OnboardingStep? = Onboarding.previous(ui.step, firstStep)
                if (previous == null) {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                } else {
                    ui = ui.copy(step = previous)
                }
            }
        })

        setContent {
            OnboardingScreen(
                ui = ui,
                plan = plan,
                onIncomeChange = { ui = ui.copy(incomeText = it) },
                onRowChange = { index, row ->
                    ui = ui.copy(rows = ui.rows.mapIndexed { i, old -> if (i == index) row else old })
                },
                onAddRow = { ui = ui.copy(rows = ui.rows + FixedCostRow(name = "", amountText = "")) },
                onRemoveRow = { index ->
                    ui = ui.copy(rows = ui.rows.filterIndexed { i, _ -> i != index })
                },
                onBudgetChange = { ui = ui.copy(budgetText = it) },
                onNext = { next() },
                onSkip = { skip() },
                onEditManually = { editManually() },
                onBackToFixed = { backToFixed() },
                onFinish = { finishWithBudget() },
                onOpenCalculator = { ui = ui.copy(step = OnboardingStep.INCOME) },
                onOpenJoin = { ui = ui.copy(step = OnboardingStep.JOIN, joinMessage = null) },
                onJoinCodeChange = { ui = ui.copy(joinCode = HouseholdCode.normalize(it).take(HouseholdCode.LENGTH)) },
                onJoin = { join() },
                onLeaveJoin = { ui = ui.copy(step = OnboardingStep.BUDGET) },
                onEnableNotification = { enableNotification() },
                onSkipNotification = { goHome() },
            )
        }
    }

    /**
     * 이미 적어 둔 것이 있으면 채워 넣는다. 설정에서 «고정비로 계산하기»로 다시 들어왔을 때
     * 빈 화면부터 시작하면 지난번에 적은 월세를 또 적게 된다.
     */
    private fun restore() {
        val saved: FixedCostPlan = FixedCostStore.load(this)
        val rows: List<FixedCostRow> =
            if (saved.items.isEmpty()) {
                FixedCosts.suggestedRows().map { FixedCostRow(it.name, "") }
            } else {
                saved.items.map { FixedCostRow(it.name, it.amount.toString()) }
            }

        ui = OnboardingUi(
            step = firstStep,
            incomeText = if (saved.monthlyIncome > 0L) saved.monthlyIncome.toString() else "",
            rows = rows,
            budgetText = budgetText(),
            // 이미 묶인 사람에게 «코드가 있어요»는 소음이다. 계산하러 온 사람에게도.
            canJoin = firstRun && HouseholdStore.householdId(this) == null,
        )
    }

    private fun budgetText(): String {
        val budget: Long = SettingsStore.load(this).personal.monthlyBudget
        return if (budget > 0L) budget.toString() else ""
    }

    private fun next() {
        ui = when (ui.step) {
            OnboardingStep.INCOME -> ui.copy(step = OnboardingStep.FIXED)
            OnboardingStep.FIXED -> ui.copy(step = OnboardingStep.RESULT)
            else -> ui
        }
    }

    /** «나중에 할래요» — 계산을 접고 금액 한 칸만 묻는다. */
    private fun skip() {
        FixedCostStore.markAsked(this)
        ui = ui.copy(step = OnboardingStep.BUDGET)
    }

    /** «직접 고치기» — 계산 결과를 칸에 넣어 둔 채로 고치게 한다. */
    private fun editManually() {
        val seed: Long = plan.recommended
        ui = ui.copy(
            step = OnboardingStep.BUDGET,
            budgetText = if (seed > 0L) seed.toString() else ui.budgetText,
        )
    }

    /** «고정비로 계산하기» / «고정비 고치기». 어디서 눌렀느냐로 돌아갈 곳이 갈린다. */
    private fun backToFixed() {
        ui = when (ui.step) {
            OnboardingStep.RESULT -> ui.copy(step = OnboardingStep.FIXED)
            else -> ui.copy(step = OnboardingStep.INCOME)
        }
    }

    /**
     * 배우자에게 받은 코드로 가정에 들어가고, 가정에 올려 둔 공용 예산·월급날을 받아 온다.
     * 성공하면 이 화면에 무엇을 가져왔는지 보여주고, 버튼은 개인 예산으로 돌아가는 길이 된다.
     */
    private fun join() {
        val uid: String = FirebaseAuth.getInstance().currentUser?.uid
            ?: return run { ui = ui.copy(joinMessage = tr("로그인 정보를 확인할 수 없어요.", "Couldn't verify your sign-in.", "No se pudo verificar tu sesión."), joinFailed = true) }
        ui = ui.copy(joinBusy = true, joinMessage = null)
        HouseholdRepository.join(uid, ui.joinCode) { result ->
            if (isFinishing || isDestroyed) return@join
            result
                .onSuccess { householdId ->
                    HouseholdStore.setHouseholdId(this, householdId)
                    HouseholdStore.setCode(this, HouseholdCode.normalize(ui.joinCode))
                    HouseholdSync.pull(this) { pulled ->
                        if (isFinishing || isDestroyed) return@pull
                        ui = ui.copy(
                            joinBusy = false,
                            joined = true,
                            canJoin = false,
                            joinFailed = false,
                            joinMessage = joinedMessage(pulled),
                        )
                    }
                }
                .onFailure {
                    ui = ui.copy(joinBusy = false, joinFailed = true, joinMessage = it.message ?: tr("연결하지 못했어요.", "Couldn't connect.", "No se pudo conectar."))
                }
        }
    }

    /** 가져온 값을 그대로 적어 보여준다. 가정에 아직 값이 없거나 못 읽었으면 설정에서 정하라고 한다. */
    private fun joinedMessage(pulled: HouseholdPull): String {
        if (pulled != HouseholdPull.CHANGED && pulled != HouseholdPull.SAME) {
            return tr(
                "가정에 연결됐어요.\n공용 예산은 설정 › 예산에서 정할 수 있어요.",
                "Joined the household.\nYou can set the shared budget in Settings › Budget.",
                "Te uniste al hogar.\nPuedes definir el presupuesto compartido en Ajustes › Presupuesto.",
            )
        }
        val settings: Settings = SettingsStore.load(this)
        val budget: String =
            if (settings.shared.hasBudget) tr("공용 예산 ", "Shared budget ", "Presupuesto compartido ") + StatusText.won(settings.shared.monthlyBudget)
            else tr("공용 예산 미정", "Shared budget not set", "Presupuesto compartido sin definir")
        // 가정 문서가 주는 건 공용 목표날이다(개인 목표날은 이 폰의 값 그대로).
        val target: String = StatusText.targetDay(settings.sharedPayDay)
        return tr(
            "가정에 연결됐어요.\n$budget · 공용 목표날($target)을 가져왔어요.",
            "Joined the household.\nImported: $budget · shared target day: $target.",
            "Te uniste al hogar.\nSe importó: $budget · día objetivo compartido: $target.",
        )
    }

    /**
     * 정해진 금액을 개인 곳간 예산에 넣는다. 처음 쓰는 사람이고 알림이 꺼져 있으면 알림 단계로,
     * 아니면 홈으로 간다.
     *
     * 고정비 계획도 함께 저장한다 — 매달 초 추천([HomeActivity.checkCycleGrade])이 이 값을 읽고,
     * 다시 들어왔을 때 [restore] 가 이 값을 채운다. 건너뛴 사람은 계획이 비어 있을 뿐이다.
     */
    private fun finishWithBudget() {
        val budget: Long = when (ui.step) {
            OnboardingStep.RESULT -> plan.recommended
            else -> readAmount(ui.budgetText)
        }
        if (budget <= 0L) return

        FixedCostStore.save(this, plan)

        val settings: Settings = SettingsStore.load(this)
        SettingsStore.save(this, settings.copy(personal = settings.personal.copy(monthlyBudget = budget)))

        if (Onboarding.asksNotification(firstRun, NotificationState.isOn(this))) {
            ui = ui.copy(step = OnboardingStep.NOTIFY)
        } else {
            goHome()
        }
    }

    private fun enableNotification() {
        if (LockCard.needsPermission(this)) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        LockCard.enable(this)
        goHome()
    }

    private fun goHome() {
        startActivity(Intent(this, HomeActivity::class.java))
        finish()
    }
}
