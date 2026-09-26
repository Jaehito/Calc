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
 * **개인 곳간만 채점한다([GRADED]).** 공용 곳간에는 배우자의 지출과 기록이 섞여서, 합쳐 매기면
 * 내가 아낀 날도 배우자가 크게 쓴 날이면 등급이 깨지고, 내가 안 적은 날도 배우자가 적으면
 * 적은 날이 된다. 등급은 «내가 어떻게 썼나»에 대한 것이라 내 곳간만 본다. 도감도 같은 기준이다.
 */
object GradeRepository {

    /** 채점하는 곳간. */
    val GRADED: Purse = Purse.PERSONAL

    /** 그 날 채점. 로컬 캐시만 읽는다 — 네트워크가 없어도 된다. */
    fun day(context: Context, day: LocalDate): SpendingGrade {
        val config: PurseSettings = gradedSettings(context) ?: return SpendingGrade.NoBudget
        val cycle: BudgetCycle = Payday.cycleOf(day, SettingsStore.load(context).payDay)
        val budget: Long = Budget.baseRate(config.monthlyBudget, cycle)

        val monthTotals: Map<LocalDate, Long> = SpendingCache.totals(context, GRADED, YearMonth.from(day))
        return SpendingGrading.of(monthTotals.containsKey(day), monthTotals[day] ?: 0L, budget)
    }

    /**
     * 지난 [days] 일(오늘 포함) 채점. 주간 돌아보기 알림이 쓴다.
     *
     * 기록없음 상태는 없다 — 이 정도 기간이면 하루도 안 적었을 가능성은 앱을 아예 안 쓴다는
     * 뜻이라 «채점 안 됨» 이 아니라 그대로 0원으로 채점하는 편이 맞다.
     */
    fun trailing(context: Context, today: LocalDate, days: Int): SpendingGrade {
        val config: PurseSettings = gradedSettings(context) ?: return SpendingGrade.NoBudget
        val cycle: BudgetCycle = Payday.cycleOf(today, SettingsStore.load(context).payDay)
        val budget: Long = Budget.baseRate(config.monthlyBudget, cycle) * days

        var spent = 0L
        for (back in 0 until days) spent += SpendingCache.spentOn(context, GRADED, today.minusDays(back.toLong()))
        return SpendingGrading.of(recorded = true, spent = spent, budget = budget)
    }

    /** 막 끝난 주기 채점. 월급날이 지나 새 주기로 넘어간 직후 한 번 부른다. */
    fun cycle(context: Context, cycle: BudgetCycle): SpendingGrade {
        val config: PurseSettings = gradedSettings(context) ?: return SpendingGrade.NoBudget
        val spent: Long = Ledger.spentInCycle(context, GRADED, cycle)
        return SpendingGrading.of(recorded = true, spent = spent, budget = config.monthlyBudget)
    }

    /** 채점하는 곳간의 설정. 연결이 안 됐거나 예산이 없으면 채점하지 않는다. */
    private fun gradedSettings(context: Context): PurseSettings? {
        if (!PurseAccess.isLinked(context, GRADED)) return null
        val config: PurseSettings = SettingsStore.load(context).of(GRADED)
        return if (config.hasBudget) config else null
    }
}
