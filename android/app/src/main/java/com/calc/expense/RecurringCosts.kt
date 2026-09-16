package com.calc.expense

import java.time.LocalDate

/**
 * 고정비로 보이는 한 줄. 앱이 «찾은» 것이지 «정한» 것이 아니다 — 사용자가 빼고 더한다.
 *
 * @param amount 다음 주기에 나갈 것으로 보는 금액. 가장 최근에 나간 금액을 쓴다
 *   (평균이 아니다 — 보험료가 올랐으면 오른 값이 맞다)
 * @param cycles 최근 세 주기 중 몇 번 보였나. 화면에 «3개월 중 2번»으로 보인다
 * @param fromRecord 손으로 적은 기록에서도 보였나. 알림에서만 보였으면 false
 */
data class FixedCostCandidate(
    val name: String,
    val amount: Long,
    val cycles: Int,
    val fromRecord: Boolean,
)

/**
 * 지난 몇 주기의 «돈이 나간 건»들에서 **달마다 되풀이되는 것**을 골라낸다.
 *
 * 사용자가 자기 고정비를 모르는 이유는 그것들이 눈에 띄지 않기 때문이다 — 자동이체라 손으로
 * 적지 않고, 알림이 와도 확인할 게 없어 넘긴다. 그래서 반대로, 앱이 본 것 전부를 놓고
 * «같은 이름 · 같은 금액이 여러 주기에 걸쳐 나왔나»만 센다.
 *
 * 재료는 두 갈래다 — 사용자가 적은 지출([ExpenseRow])과 앱이 본 결제 알림([PaymentLogEntry]).
 * 어느 쪽에서 왔든 [MoneyEvent] 로 같아지고, 여기서는 출처를 판정에 쓰지 않는다.
 * 통신비를 손으로 적는 사람과 안 적는 사람 모두에게 같은 답이 나와야 한다.
 *
 * Android 에 의존하지 않아 단위 테스트로 고정한다.
 */
object RecurringCosts {

    /** 몇 주기에서 보여야 고정비로 보나. 두 번이면 «되풀이»라고 말할 수 있는 최소다. */
    const val MIN_CYCLES = 2

    /** 돌아보는 주기 수. 넉 달 치를 들고 있지만 판정은 최근 셋만 본다. */
    const val LOOK_BACK_CYCLES = 3

    /**
     * 금액이 이만큼까지 흔들려도 같은 고정비로 본다.
     *
     * 관리비·통신비는 달마다 조금씩 다르고, 그걸 «다른 항목»으로 갈라 버리면 정작 찾아야 할
     * 것들을 못 찾는다. 반대로 폭을 더 넓히면 커피값과 밥값이 한 덩어리가 된다.
     */
    const val TOLERANCE_PERCENT = 15

    /**
     * 한 주기에 이보다 자주 나오면 고정비가 아니다.
     *
     * 고정비는 한 주기에 한 번 나간다. 두 번까지 허용하는 것은 문자앱·은행앱이 같은 결제를
     * 각각 올렸는데 시간 폭을 벗어나 합쳐지지 않은 경우를 위해서다. 같은 카페를 여덟 번
     * 간 것은 금액이 같아도 고정비가 아니다 — 이 한 줄이 그걸 걸러 낸다.
     */
    const val MAX_PER_CYCLE = 2

    /** 이보다 작으면 제안하지 않는다. 몇천 원짜리까지 늘어놓으면 목록이 쓸모를 잃는다. */
    const val MIN_AMOUNT = 5_000L

    /** 한 번에 제안하는 최대 줄 수. 넘치면 큰 것부터. */
    const val MAX_CANDIDATES = 12

    /**
     * [today] 가 속한 주기를 뺀, 바로 앞 [LOOK_BACK_CYCLES] 개의 **끝난 주기**.
     *
     * 진행 중인 주기를 넣지 않는 이유는 아직 이번 달 월세가 안 나갔을 수 있어서다. 반쯤 지난
     * 주기를 한 칸으로 세면 «3개월 중 2번»이 «아직 안 나갔을 뿐»과 구별되지 않는다.
     */
    fun recentCycles(today: LocalDate, payDay: Int, count: Int = LOOK_BACK_CYCLES): List<BudgetCycle> {
        val cycles = ArrayList<BudgetCycle>(count)
        var day: LocalDate = Payday.cycleOf(today, payDay).start.minusDays(1)
        var left: Int = count
        while (left > 0) {
            val cycle: BudgetCycle = Payday.cycleOf(day, payDay)
            cycles.add(cycle)
            day = cycle.start.minusDays(1)
            left--
        }
        return cycles
    }

    /**
     * 되풀이되는 것들. 큰 금액부터.
     *
     * @param known 사용자가 이미 고정비로 적어 둔 이름들. 같은 것을 다시 제안하지 않는다
     */
    fun of(
        events: List<MoneyEvent>,
        cycles: List<BudgetCycle>,
        known: List<String> = emptyList(),
    ): List<FixedCostCandidate> {
        if (cycles.isEmpty()) return emptyList()

        val knownNames: Set<String> = known.map { normalize(it) }.filter { it.isNotEmpty() }.toSet()
        val placed = ArrayList<Pair<Int, MoneyEvent>>()
        for (event in events) {
            if (event.amount <= 0L) continue
            val key: String = normalize(event.name)
            if (key.isEmpty() || key in knownNames) continue
            val index: Int = cycles.indexOfFirst { it.contains(event.date) }
            if (index < 0) continue
            placed.add(index to event)
        }

        return placed
            .groupBy { normalize(it.second.name) }
            .mapNotNull { (_, entries) -> candidateOf(entries) }
            .sortedByDescending { it.amount }
            .take(MAX_CANDIDATES)
    }

    /** 한 이름에 모인 건들이 고정비 조건을 채우면 한 줄로, 아니면 null. */
    private fun candidateOf(entries: List<Pair<Int, MoneyEvent>>): FixedCostCandidate? {
        val cycleCount: Int = entries.map { it.first }.distinct().size
        if (cycleCount < MIN_CYCLES) return null

        val perCycle: Map<Int, Int> = entries.groupingBy { it.first }.eachCount()
        if (perCycle.values.any { it > MAX_PER_CYCLE }) return null

        val amounts: List<Long> = entries.map { it.second.amount }
        val low: Long = amounts.min()
        val high: Long = amounts.max()
        // 정수만으로 «high 가 low 보다 TOLERANCE_PERCENT % 넘게 크지 않은가»를 본다.
        if (high * 100L > low * (100L + TOLERANCE_PERCENT)) return null

        val latest: MoneyEvent = entries.maxByOrNull { it.second.date }?.second ?: return null
        if (latest.amount < MIN_AMOUNT) return null

        return FixedCostCandidate(
            name = latest.name.trim(),
            amount = latest.amount,
            cycles = cycleCount,
            fromRecord = entries.any { it.second.fromRecord },
        )
    }

    /**
     * 이름을 견주기 좋게 다듬는다. 띄어쓰기만 없앤다 — 「우리카드 우아한형제들」과
     * 「우리카드우아한형제들」은 같은 곳이다. 그 이상 손대면(숫자·기호 제거) 「KT 5G」와
     * 「KT」가 한 덩어리가 되어 없는 고정비를 만들어 낸다.
     */
    fun normalize(raw: String): String = raw.filterNot { it.isWhitespace() }
}
