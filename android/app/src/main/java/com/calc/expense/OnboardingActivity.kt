package com.calc.expense

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 첫 시작 — 월급에서 고정비를 빼 **이번 달 챌린지 금액**을 정하고 개인 곳간 예산에 넣는다.
 *
 * 로그인 직후 [LoginActivity] 가 [Onboarding.shouldShow] 로 판단해 여기로 보낸다. 설정·홈의
 * «고정비로 계산하기»도 같은 화면을 연다 — 다시 하고 싶을 때 들어오는 문이다.
 *
 * **개인 곳간만 정한다.** 공용은 배우자와 묶은 뒤 설정에서 따로 적는다 — 여기서 둘을 나누게
 * 하면 첫 화면이 길어지고, 공용을 안 쓰는 사람에게는 있지도 않은 개념을 설명하게 된다.
 */
class OnboardingActivity : ComponentActivity() {

    private var ui: OnboardingUi by mutableStateOf(OnboardingUi())

    /** 화면이 그리는 계산 결과. 입력칸 문자열에서 매번 다시 만든다 — 상태를 두 벌 들지 않는다. */
    private val plan: FixedCostPlan
        get() = FixedCostPlan(
            monthlyIncome = readAmount(ui.incomeText),
            items = FixedCosts.clean(
                ui.rows.map { FixedCostItem(name = it.name, amount = readAmount(it.amountText)) },
            ),
        )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        restore()

        // 뒤로가기는 앱을 나가는 게 아니라 앞 단계로 돌아간다. 첫 단계에서만 나간다.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val previous: OnboardingStep? = when (ui.step) {
                    OnboardingStep.INCOME -> null
                    OnboardingStep.FIXED -> OnboardingStep.INCOME
                    OnboardingStep.RESULT -> OnboardingStep.FIXED
                    OnboardingStep.BUDGET -> OnboardingStep.INCOME
                }
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
            step = OnboardingStep.INCOME,
            incomeText = if (saved.monthlyIncome > 0L) saved.monthlyIncome.toString() else "",
            rows = rows,
            budgetText = budgetText(),
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

    /** «나중에 할래요» — 파이프라인을 접고 금액 한 칸만 묻는다. */
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
     * 정해진 금액을 개인 곳간 예산에 넣고 홈으로 간다.
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

        startActivity(Intent(this, HomeActivity::class.java))
        finish()
    }
}
