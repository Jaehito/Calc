package com.calc.expense

import android.content.Context
import java.time.LocalDate
import java.time.YearMonth

/**
 * 하루·주·주기 채점에 필요한 값을 로컬 캐시에서 모아 [SpendingGrade] 로 만든다.
 *
 * 예산 기준선은 그날의 «하루치 기본값»([Budget.baseRate])을 쓴다 — 초과로 줄어든 그날의
 * 실제 dailyRate(Ledger.snapshot 이 보여주는 값)가 아니다. 전날 많이 써서 오늘 채점 기준까지
 * 낮아지면 다음 날 채점이 눈덩이처럼 나빠져 «이 정도가 하루 기준»이라는 감각과 어긋난다.
 *
 * 연결된 곳간을 전부 합쳐 하나로 채점한다 — 개인·공용을 따로 매기면 «오늘 등급»이 두 개가
 * 되어 동기부여로서 오히려 흐려진다.
 */
object GradeRepository {

    /** 그 날 채점. 로컬 캐시만 읽는다 — 네트워크가 없어도 된다. */
    fun day(context: Context, day: LocalDate): SpendingGrade {
        val settings: Settings = SettingsStore.load(context)
        val purses: List<Purse> = PurseAccess.linked(context)
        if (purses.isEmpty()) return SpendingGrade.NoBudget

        val cycle: BudgetCycle = Payday.cycleOf(day, settings.payDay)
        var spent = 0L
        var budget = 0L
        var recorded = false

        for (purse in purses) {
            val config: PurseSettings = settings.of(purse)
            if (!config.hasBudget) continue
            budget += Budget.baseRate(config.monthlyBudget, cycle)

            val monthTotals: Map<LocalDate, Long> = SpendingCache.totals(context, purse, YearMonth.from(day))
            if (monthTotals.containsKey(day)) recorded = true
            spent += monthTotals[day] ?: 0L
        }

        return SpendingGrading.of(recorded, spent, budget)
    }

    /**
     * 지난 [days] 일(오늘 포함) 채점. 주간 돌아보기 알림이 쓴다.
     *
     * 기록없음 상태는 없다 — 이 정도 기간이면 하루도 안 적었을 가능성은 앱을 아예 안 쓴다는
     * 뜻이라 «채점 안 됨» 이 아니라 그대로 0원으로 채점하는 편이 맞다.
     */
    fun trailing(context: Context, today: LocalDate, days: Int): SpendingGrade {
        val settings: Settings = SettingsStore.load(context)
        val purses: List<Purse> = PurseAccess.linked(context)
        if (purses.isEmpty()) return SpendingGrade.NoBudget

        val cycle: BudgetCycle = Payday.cycleOf(today, settings.payDay)
        var budget = 0L
        for (purse in purses) {
            val config: PurseSettings = settings.of(purse)
            if (config.hasBudget) budget += Budget.baseRate(config.monthlyBudget, cycle) * days
        }

        val spent: Long = StatsRepository.spentBetween(context, today.minusDays((days - 1).toLong()), today)
        return SpendingGrading.of(recorded = true, spent = spent, budget = budget)
    }

    /** 막 끝난 주기 채점. 월급날이 지나 새 주기로 넘어간 직후 한 번 부른다. */
    fun cycle(context: Context, cycle: BudgetCycle): SpendingGrade {
        val settings: Settings = SettingsStore.load(context)
        val purses: List<Purse> = PurseAccess.linked(context)
        if (purses.isEmpty()) return SpendingGrade.NoBudget

        var spent = 0L
        var budget = 0L
        for (purse in purses) {
            val config: PurseSettings = settings.of(purse)
            if (!config.hasBudget) continue
            budget += config.monthlyBudget
            spent += Ledger.spentInCycle(context, purse, cycle)
        }

        return SpendingGrading.of(recorded = true, spent = spent, budget = budget)
    }
}
