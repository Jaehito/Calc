package com.calc.expense

import java.time.LocalDate

/**
 * 곳간 하나의 예산 상태.
 *
 * 곳간은 공동과 개인이 각각 독립적으로 존재하며 서로 섞이지 않는다.
 * 공동에서 초과가 나도 개인 하루치는 줄지 않고, 반대도 마찬가지다.
 *
 * @param monthlyBudget 한 주기(월급날→다음 월급날) 예산
 * @param dailyRate 오늘 적용되는 하루치. 초과분이 곳간을 넘어서면 그 주기 남은 기간 동안 내려간다
 * @param vault 곳간 잔액. 0 아래로 내려가지 않는다
 * @param settledThrough 이 날짜까지 정산이 끝났다 (보통 어제)
 */
data class BudgetState(
    val monthlyBudget: Long,
    val dailyRate: Long,
    val vault: Long,
    val settledThrough: LocalDate,
)

/**
 * 곳간 정산.
 *
 * 규칙은 비대칭이다 — 아낀 돈은 곳간에 그대로 쌓이고, 초과분은 곳간이 먼저 흡수한 뒤
 * 모자란 만큼만 그 주기 남은 날에 분산된다. 그래서 크게 쓴 다음 날에도 "오늘 쓸 수 있는 돈"이
 * 음수로 남지 않는다. 마이너스 잔고는 계획이 아니라 판결문이라 앱을 안 열게 만든다.
 *
 * Android 타입에 의존하지 않는다. 이 계산이 틀리면 화면의 모든 숫자가 거짓말이 되므로
 * 단위 테스트로만 검증할 수 있게 순수하게 유지한다.
 */
object Budget {

    /** 월급날에 다음 주기로 넘길 수 있는 곳간의 상한 (하루치 기준 일수). */
    const val VAULT_CAP_DAYS = 5L

    /** 그 주기의 하루치. 예산을 주기 길이로 나눈다 — 주기는 28~31 일로 달마다 다르다. */
    fun baseRate(monthlyBudget: Long, cycle: BudgetCycle): Long =
        if (monthlyBudget <= 0L || cycle.length <= 0) 0L else monthlyBudget / cycle.length

    /** 처음 시작하는 상태. 과거 지출을 소급하지 않는다 — 첫날부터 마이너스로 시작하면 안 된다. */
    fun start(monthlyBudget: Long, today: LocalDate, payDay: Int): BudgetState = BudgetState(
        monthlyBudget = monthlyBudget,
        dailyRate = baseRate(monthlyBudget, Payday.cycleOf(today, payDay)),
        vault = 0L,
        settledThrough = today.minusDays(1),
    )

    /** 오늘 쓸 수 있는 돈. 오늘 하루 안에서는 음수가 될 수 있고, 그건 사실이므로 그대로 보여준다. */
    fun available(state: BudgetState, todaySpent: Long): Long =
        state.dailyRate + state.vault - todaySpent

    /** 예산을 바꾼다. 이번 주기 하루치를 다시 계산하되 이미 쌓인 곳간은 건드리지 않는다. */
    fun updateBudget(
        state: BudgetState,
        monthlyBudget: Long,
        today: LocalDate,
        payDay: Int,
    ): BudgetState = state.copy(
        monthlyBudget = monthlyBudget,
        dailyRate = baseRate(monthlyBudget, Payday.cycleOf(today, payDay)),
    )

    /**
     * [BudgetState.settledThrough] 다음 날부터 어제까지 하루씩 정산해 오늘 기준 상태를 만든다.
     *
     * 기록이 없는 날은 지출 0으로 본다. 즉 적지 않으면 곳간이 부풀어 숫자가 거짓말을 한다.
     * 이건 버그가 아니라 의도된 손해다 — 앱이 나를 혼내는 대신, 내 도구가 나에게 쓸모없어진다.
     *
     * @param spentOn 그 날의 지출 합계를 돌려준다
     */
    fun settle(
        state: BudgetState,
        today: LocalDate,
        payDay: Int,
        spentOn: (LocalDate) -> Long,
    ): BudgetState {
        if (!state.settledThrough.isBefore(today)) return state

        var rate: Long = state.dailyRate
        var vault: Long = state.vault
        var day: LocalDate = state.settledThrough.plusDays(1)

        while (!day.isAfter(today)) {
            val cycle: BudgetCycle = Payday.cycleOf(day, payDay)

            // 월급날이 오면 하루치를 원래대로 되돌리고 곳간에 상한을 건다.
            // 무한 누적을 막으면서도 주기 끝에 전부 소멸시키지는 않는다.
            if (day == cycle.start) {
                rate = baseRate(state.monthlyBudget, cycle)
                vault = minOf(vault, rate * VAULT_CAP_DAYS)
            }

            // 오늘은 아직 끝나지 않았으므로 정산하지 않는다. 위 월급날 처리까지만 적용한다.
            if (day == today) break

            vault += rate - spentOn(day)
            if (vault < 0L) {
                val shortfall: Long = -vault
                vault = 0L
                // day 이후로 이 주기에 남은 날. 여기에만 분산한다.
                val remainingDays: Int = cycle.daysLeftFrom(day) - 1
                if (remainingDays > 0) {
                    rate = maxOf(0L, rate - ceilDiv(shortfall, remainingDays.toLong()))
                }
                // 주기 마지막 날의 초과분은 분산할 곳이 없다. 다음 주기로 넘기지 않고 끊는다.
            }
            day = day.plusDays(1)
        }

        return state.copy(
            dailyRate = rate,
            vault = vault,
            settledThrough = today.minusDays(1),
        )
    }

    /**
     * 저장할 앵커와 오늘 화면에 쓸 상태.
     *
     * 둘을 나누는 이유는 곳간이 **캐시에서 매번 다시 계산되어야** 하기 때문이다. 정산 결과를
     * 그대로 저장해 버리면, Notion 재동기화로 이번 달 지난 날짜가 고쳐져도 곳간이 따라오지 않는다.
     */
    data class Reckoning(val anchor: BudgetState, val today: BudgetState)

    /**
     * 저장된 앵커와 오늘 날짜로부터 화면에 쓸 상태를 만든다.
     *
     * [Reckoning.anchor] 는 이번 주기 시작(월급날) 기준값이라 주기가 바뀔 때만 움직인다 — 지난 주기 캐시가
     * 사라져도 곳간이 흔들리지 않게 고정해 두는 값이다. [Reckoning.today] 는 그 앵커에서
     * 오늘까지 다시 접은 결과이고 저장하지 않는다.
     *
     * 이번 주기 안의 수정은 즉시 반영되고, 지난 주기의 수정은 반영되지 않는다. 이미 앵커로
     * 굳었기 때문이다. 그건 의도한 것이다 — 지난 주기를 다시 계산하기 시작하면 곳간이
     * 언제든 뒤집힐 수 있게 되고, 그러면 오늘의 숫자를 믿을 수 없다.
     */
    fun reckon(
        stored: BudgetState?,
        monthlyBudget: Long,
        today: LocalDate,
        payDay: Int,
        spentOn: (LocalDate) -> Long,
    ): Reckoning {
        var anchor: BudgetState = stored ?: start(monthlyBudget, today, payDay)

        if (anchor.monthlyBudget != monthlyBudget) {
            anchor = updateBudget(anchor, monthlyBudget, today, payDay)
        }

        val cycle: BudgetCycle = Payday.cycleOf(today, payDay)
        val previousCycleEnd: LocalDate = cycle.start.minusDays(1)
        if (anchor.settledThrough.isBefore(previousCycleEnd)) {
            anchor = settle(anchor, cycle.start, payDay, spentOn)
        }

        return Reckoning(anchor = anchor, today = settle(anchor, today, payDay, spentOn))
    }

    /** 두 값 모두 양수일 때만 쓴다. */
    private fun ceilDiv(a: Long, b: Long): Long = (a + b - 1L) / b
}
