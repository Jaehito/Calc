package com.calc.expense

import android.content.Context
import java.time.LocalDate
import java.time.YearMonth

/**
 * 곳간 계산 · 로컬 캐시 · Notion 을 잇는 유일한 진입점.
 *
 * [Budget] 은 순수 계산, [SpendingCache] 는 저장, [NotionClient] 는 네트워크다.
 * 셋을 어디서 어떤 순서로 부를지는 여기 한 곳에서만 정한다.
 *
 * 모든 함수가 [Purse] 를 받는다. 개인과 공용은 DB·캐시·곳간이 전부 따로이고,
 * 이 파일 어디에서도 둘을 합치지 않는다.
 */
object Ledger {

    /**
     * 오늘 기준으로 정산한 뒤 화면에 필요한 숫자를 돌려준다.
     * 그 곳간에 월 예산이 없으면 null — 곳간이 성립하지 않는다.
     */
    fun snapshot(
        context: Context,
        purse: Purse,
        today: LocalDate = LocalDate.now(),
    ): LedgerSnapshot? {
        val settings = SettingsStore.load(context)
        val config = settings.of(purse)
        if (!config.isActive) return null

        val stored: BudgetState? = BudgetStore.load(context, purse)
        val reckoning: Budget.Reckoning =
            Budget.reckon(stored, config.monthlyBudget, today) { day ->
                SpendingCache.spentOn(context, purse, day)
            }

        // 저장하는 건 앵커뿐이다. 오늘 상태는 매번 캐시에서 다시 접으므로
        // Notion 재동기화로 이번 달 지난 날짜가 고쳐지면 곳간도 같이 고쳐진다.
        if (reckoning.anchor != stored) BudgetStore.save(context, reckoning.anchor, purse)

        val month: YearMonth = YearMonth.from(today)
        val totals: Map<LocalDate, Long> = SpendingCache.totals(context, purse, month)

        return LedgerSnapshot(
            purse = purse,
            dailyRate = reckoning.today.dailyRate,
            vault = reckoning.today.vault,
            todaySpent = totals[today] ?: 0L,
            monthSpent = totals.values.sum(),
            monthlyBudget = config.monthlyBudget,
        )
    }

    /** Notion 쓰기가 성공한 뒤 로컬 사본에 반영한다. 실패한 기록을 더하면 숫자가 거짓말을 한다. */
    fun record(context: Context, purse: Purse, day: LocalDate, amount: Long) {
        SpendingCache.add(context, purse, day, amount)
    }

    /**
     * Notion 을 기준으로 그 곳간의 이번 달 캐시를 다시 맞춘다. 성공하면 null, 실패하면 오류 문구.
     *
     * 네트워크를 타므로 반드시 백그라운드 스레드에서 부른다. 잠금화면 기록 경로에서는
     * 부르지 않는다 — 브로드캐스트 수명 안에 왕복을 두 번 할 수 없다.
     */
    fun resync(
        context: Context,
        purse: Purse,
        month: YearMonth = YearMonth.now(),
    ): String? {
        val target: NotionTarget = SettingsStore.load(context).target(purse)
            ?: return "${purse.label} 곳간에 DB가 연결되지 않았습니다"

        return when (val r = NotionClient(target).queryMonth(month)) {
            is NotionClient.MonthOutcome.Ok -> {
                SpendingCache.replaceMonth(context, purse, month, r.totals)
                null
            }
            is NotionClient.MonthOutcome.Err -> r.message
        }
    }
}
