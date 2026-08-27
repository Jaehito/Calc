package com.calc.expense

import android.content.Context
import java.time.LocalDate
import java.time.YearMonth

/** 화면과 알림이 필요로 하는 오늘의 숫자 한 벌. */
data class LedgerSnapshot(
    val dailyRate: Long,
    val vault: Long,
    val todaySpent: Long,
    val monthSpent: Long,
    val monthlyBudget: Long,
) {
    /** 오늘 쓸 수 있는 돈. 오늘 안에서는 음수가 될 수 있고 그건 사실이므로 감추지 않는다. */
    val available: Long
        get() = dailyRate + vault - todaySpent
}

/**
 * 곳간 계산 · 로컬 캐시 · Notion 을 잇는 유일한 진입점.
 *
 * [Budget] 은 순수 계산, [SpendingCache] 는 저장, [NotionClient] 는 네트워크다.
 * 셋을 어디서 어떤 순서로 부를지는 여기 한 곳에서만 정한다.
 */
object Ledger {

    /**
     * 오늘 기준으로 정산한 뒤 화면에 필요한 숫자를 돌려준다.
     * 월 예산이 정해지지 않았으면 null — 곳간이 성립하지 않는다.
     *
     * 정산 결과가 달라졌으면 그 자리에서 저장한다. 하루가 지나 처음 부르는 쪽이 정산을 떠맡는데,
     * 그게 잠금화면일 수도 앱일 수도 있어서 어느 쪽이든 같은 결과가 나오게 여기에 모아 둔다.
     */
    fun snapshot(context: Context, today: LocalDate = LocalDate.now()): LedgerSnapshot? {
        val settings = SettingsStore.load(context)
        if (!settings.hasBudget) return null

        val stored: BudgetState? = BudgetStore.load(context)
        val reckoning: Budget.Reckoning = Budget.reckon(stored, settings.monthlyBudget, today) { day ->
            SpendingCache.spentOn(context, day)
        }

        // 저장하는 건 앵커뿐이다. 오늘 상태는 매번 캐시에서 다시 접으므로
        // Notion 재동기화로 이번 달 지난 날짜가 고쳐지면 곳간도 같이 고쳐진다.
        if (reckoning.anchor != stored) BudgetStore.save(context, reckoning.anchor)

        val month: YearMonth = YearMonth.from(today)
        val totals: Map<LocalDate, Long> = SpendingCache.totals(context, month)

        return LedgerSnapshot(
            dailyRate = reckoning.today.dailyRate,
            vault = reckoning.today.vault,
            todaySpent = totals[today] ?: 0L,
            monthSpent = totals.values.sum(),
            monthlyBudget = settings.monthlyBudget,
        )
    }

    /** Notion 쓰기가 성공한 뒤 로컬 사본에 반영한다. 실패한 기록을 더하면 숫자가 거짓말을 한다. */
    fun record(context: Context, day: LocalDate, amount: Long) {
        SpendingCache.add(context, day, amount)
    }

    /**
     * Notion 을 기준으로 그 달의 캐시를 다시 맞춘다. 성공하면 null, 실패하면 오류 문구.
     *
     * 네트워크를 타므로 반드시 백그라운드 스레드에서 부른다. 잠금화면 기록 경로에서는
     * 부르지 않는다 — 브로드캐스트 수명 안에 왕복을 두 번 할 수 없다.
     */
    fun resync(context: Context, month: YearMonth = YearMonth.now()): String? {
        val settings = SettingsStore.load(context)
        if (!settings.isComplete) return "설정이 비어 있습니다"

        return when (val r = NotionClient(settings).queryMonth(month)) {
            is NotionClient.MonthOutcome.Ok -> {
                SpendingCache.replaceMonth(context, month, r.totals)
                null
            }
            is NotionClient.MonthOutcome.Err -> r.message
        }
    }
}
