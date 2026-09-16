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
 * 주기 리포트 화면. 결산 팝업의 «리포트 보기»와 통계 탭의 «지난 주기 리포트»가 같은 곳으로 온다.
 *
 * 문이 둘인 이유는 필요한 순간이 둘이기 때문이다. 결산 팝업은 주기가 바뀐 그 순간 한 번만
 * 뜨는데, 그때 «나중에»를 누른 사람에게 다음 달까지 이 화면을 못 보게 하면 정작 궁금해졌을 때
 * 갈 곳이 없다. 통계 탭은 그 «나중에»가 왔을 때의 문이다.
 */
class CycleReportActivity : ComponentActivity() {

    private val io = Executors.newSingleThreadExecutor()

    private var report: CycleReport? by mutableStateOf(null)

    /** 고른 후보의 이름 키([RecurringCosts.normalize]). 처음에는 모두 골라져 있다. */
    private var selected: Set<String> by mutableStateOf(emptySet())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CycleReportScreen(
                report = report,
                selected = selected,
                onToggle = { toggle(it) },
                onApply = { apply() },
                onEditFixed = {
                    startActivity(Intent(this@CycleReportActivity, OnboardingActivity::class.java))
                },
                onClose = { finish() },
            )
        }
    }

    /**
     * 화면에 돌아올 때마다 다시 만든다.
     *
     * «직접 더하거나 고치기»로 나갔다 오면 고정비 계획이 달라져 있고, 그러면 이미 적힌 것은
     * 후보에서 빠져야 한다([RecurringCosts.of] 의 known). 만들어 둔 것을 그대로 보여주면
     * 방금 적은 월세를 이 화면이 또 제안한다.
     */
    override fun onResume() {
        super.onResume()
        load()
    }

    private fun load() {
        io.execute {
            val built: CycleReport = CycleReportRepository.build(this, LocalDate.now())
            runOnUiThread {
                report = built
                selected = built.candidates.map { RecurringCosts.normalize(it.name) }.toSet()
            }
        }
    }

    override fun onDestroy() {
        io.shutdown()
        super.onDestroy()
    }

    private fun toggle(candidate: FixedCostCandidate) {
        val key: String = RecurringCosts.normalize(candidate.name)
        selected = if (key in selected) selected - key else selected + key
    }

    /**
     * 고른 것을 고정비에 넣고, 월급을 아는 경우에는 개인 곳간 예산까지 새 금액으로 바꾼다.
     *
     * 예산을 함께 바꾸는 이유는 숫자가 하나이기 때문이다([FixedCostPlan]) — 고정비만 적어 두고
     * 예산이 옛 값으로 남으면 화면이 «고정비를 반영했다»고 해 놓고 실제 곳간은 그대로다.
     * 월급을 모르면 예산은 건드리지 않는다. 근거 없는 숫자를 곳간에 넣지 않는다.
     */
    private fun apply() {
        val current: CycleReport = report ?: return
        val chosen: List<FixedCostCandidate> =
            current.candidates.filter { RecurringCosts.normalize(it.name) in selected }
        if (chosen.isEmpty()) return

        val plan: FixedCostPlan = current.planWith(chosen)
        FixedCostStore.save(this, plan)

        if (plan.canRecommend) {
            val settings: Settings = SettingsStore.load(this)
            SettingsStore.save(
                this,
                settings.copy(personal = settings.personal.copy(monthlyBudget = plan.recommended)),
            )
        }

        finish()
    }
}
