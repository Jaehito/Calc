package com.calc.expense

import android.content.Context
import java.time.LocalDate

/**
 * 주기 리포트를 모은다.
 *
 * 두 갈래에서 읽는다 — 지출 기록은 저장소(Firestore), 결제 알림은 폰 안의 [PaymentLogStore].
 * **한쪽이 실패해도 멈추지 않는다.** 네트워크가 없어 기록을 못 읽어도 알림만으로 고정비를
 * 찾을 수 있고, 그 반대도 마찬가지다. 사용자가 고정비를 모르는 상황 자체가 이 기능의 이유라
 * 반쯤이라도 답하는 편이 «나중에 다시 오세요»보다 낫다. 대신 [CycleReport.error] 로
 * «덜 봤다»를 숨기지 않고 말한다.
 *
 * 저장소를 읽으므로 반드시 백그라운드 스레드에서 부른다.
 */
object CycleReportRepository {

    /**
     * [purse] 곳간의 가장 최근에 끝난 주기 리포트. 주기는 그 곳간의 목표날을 따른다.
     *
     * 주기 경계는 [RecurringCosts.recentCycles] 하나가 정한다 — 결산 팝업과 통계 탭이 각자
     * 계산하면 월급날 전후로 서로 다른 주기를 보여줄 수 있다.
     *
     * 결제 알림은 폰 주인의 것이라 개인 리포트에만 섞는다. 공용 리포트는 고정비 찾기를
     * 보여 주지 않는다([CycleReport.showsFixedCosts]).
     */
    fun build(context: Context, purse: Purse, today: LocalDate = LocalDate.now()): CycleReport {
        val settings: Settings = SettingsStore.load(context)
        val payDay: Int = settings.payDayOf(purse)
        val cycles: List<BudgetCycle> = RecurringCosts.recentCycles(today, payDay)
        val ended: BudgetCycle = cycles.first()

        val spent: Long = Ledger.spentInCycle(context, purse, ended)
        val config: PurseSettings = settings.of(purse)
        val budget: Long = if (config.hasBudget) config.monthlyBudget else 0L

        // 그 앞 주기가 없으면 견주지 않는다. 0 원과 견주면 언제나 «더 썼어요»가 된다.
        val prevSpent: Long =
            if (cycles.size < 2) 0L
            else Ledger.spentInCycle(context, purse, cycles[1])

        // 되풀이를 찾을 때는 **진행 중인 주기까지** 본다. 그러지 않으면 이달에 적은 것이
        // 통째로 버려져, 이달 중순에 깔고 한 달 쓴 사람은 아무것도 못 찾는다.
        val lookback: List<BudgetCycle> = RecurringCosts.lookback(today, payDay)
        val rows: List<ExpenseRow>? =
            FirestoreExpenseReader.rowsBetween(context, purse, lookback.last().start, today)

        val plan: FixedCostPlan = FixedCostStore.load(context)
        val logs: List<MoneyEvent> =
            if (purse == Purse.PERSONAL) PaymentLogs.events(PaymentLogStore.load(context)) else emptyList()
        val events: List<MoneyEvent> = logs + rows.orEmpty().map { it.toMoneyEvent() }

        return CycleReport(
            cycle = ended,
            spent = spent,
            budget = budget,
            prevSpent = prevSpent,
            categories = categoriesOf(rows, ended),
            candidates = RecurringCosts.of(events, lookback, known = plan.items.map { it.name }),
            plan = plan,
            loading = false,
            error = when {
                rows != null -> null
                purse == Purse.PERSONAL -> tr("기록을 불러오지 못해 결제 알림만 봤어요", "Couldn't load your entries, so only payment alerts were used", "No se pudieron cargar tus gastos; solo se usaron los avisos de pago")
                else -> tr("기록을 불러오지 못했어요. 잠시 뒤 다시 열어 주세요", "Couldn't load entries. Please try again in a moment", "No se pudieron cargar los gastos. Vuelve a intentarlo en un momento")
            },
            purse = purse,
        )
    }

    /** 끝난 주기 안의 행만 카테고리로 묶는다. 읽지 못했으면 빈 목록이다. */
    private fun categoriesOf(rows: List<ExpenseRow>?, cycle: BudgetCycle): List<CategorySlice> {
        if (rows == null) return emptyList()
        val totals = LinkedHashMap<String, Long>()
        for (row in rows) {
            if (!cycle.contains(row.date)) continue
            totals[row.category] = (totals[row.category] ?: 0L) + row.amount
        }
        return CategoryBreakdown.of(totals)
    }
}
