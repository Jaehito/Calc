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
     * 가장 최근에 끝난 주기의 리포트.
     *
     * 주기 경계는 [RecurringCosts.recentCycles] 하나가 정한다 — 결산 팝업과 통계 탭이 각자
     * 계산하면 월급날 전후로 서로 다른 주기를 보여줄 수 있다.
     */
    fun build(context: Context, today: LocalDate = LocalDate.now()): CycleReport {
        val settings: Settings = SettingsStore.load(context)
        val cycles: List<BudgetCycle> = RecurringCosts.recentCycles(today, settings.payDay)
        val ended: BudgetCycle = cycles.first()
        val purses: List<Purse> = PurseAccess.linked(context)

        var spent = 0L
        var budget = 0L
        for (purse in purses) {
            spent += Ledger.spentInCycle(context, purse, ended)
            val config: PurseSettings = settings.of(purse)
            if (config.hasBudget) budget += config.monthlyBudget
        }

        // 그 앞 주기가 없으면 견주지 않는다. 0 원과 견주면 언제나 «더 썼어요»가 된다.
        val prevSpent: Long =
            if (cycles.size < 2) 0L
            else purses.sumOf { Ledger.spentInCycle(context, it, cycles[1]) }

        val oldest: BudgetCycle = cycles.last()
        val rows: List<ExpenseRow>? = readRows(context, purses, oldest.start, ended.lastDay)

        val plan: FixedCostPlan = FixedCostStore.load(context)
        val events: List<MoneyEvent> =
            PaymentLogs.events(PaymentLogStore.load(context)) + rows.orEmpty().map { it.toMoneyEvent() }

        return CycleReport(
            cycle = ended,
            spent = spent,
            budget = budget,
            prevSpent = prevSpent,
            categories = categoriesOf(rows, ended),
            candidates = RecurringCosts.of(events, cycles, known = plan.items.map { it.name }),
            plan = plan,
            loading = false,
            error = if (rows == null) "기록을 불러오지 못해 결제 알림만 봤어요" else null,
        )
    }

    /** 곳간을 합쳐 읽는다. 하나라도 실패하면 null — 반쪽만 담으면 숫자가 조용히 작아진다. */
    private fun readRows(
        context: Context,
        purses: List<Purse>,
        first: LocalDate,
        last: LocalDate,
    ): List<ExpenseRow>? {
        val merged = ArrayList<ExpenseRow>()
        for (purse in purses) {
            val rows: List<ExpenseRow> =
                FirestoreExpenseReader.rowsBetween(context, purse, first, last) ?: return null
            merged.addAll(rows)
        }
        return merged
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
