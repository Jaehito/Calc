package com.calc.expense

import java.time.LocalDate

/**
 * 고정비로 보이는 한 줄. 앱이 «찾은» 것이지 «정한» 것이 아니다 — 사용자가 빼고 더한다.
 *
 * @param amount 다음 주기에 나갈 것으로 보는 금액. 가장 최근에 나간 금액을 쓴다
 *   (평균이 아니다 — 보험료가 올랐으면 오른 값이 맞다)
 * @param cycles 최근 세 주기 중 몇 번 보였나. 화면에 «3개월 중 2번»으로 보인다
 * @param fromRecord 손으로 적은 기록에서도 보였나. 알림에서만 보였으면 false
 * @param averaged 금액이 달마다 달라 평균을 냈나. 화면이 «평균»이라고 밝혀야 하는 값이다
 * @param repeated 여러 주기에서 되풀이되는 것을 **실제로 확인했나**. false 면 아직 한 주기밖에
 *   못 봐서 «큰 금액»만 보고 고른 것이다 — 화면이 그 차이를 숨기면 안 되고, 기본으로
 *   체크해 두어서도 안 된다
 */
data class FixedCostCandidate(
    val name: String,
    val amount: Long,
    val cycles: Int,
    val fromRecord: Boolean,
    val averaged: Boolean = false,
    val repeated: Boolean = true,
)

/**
 * 지난 몇 주기의 «돈이 나간 건»들에서 **달마다 되풀이되는 것**을 골라낸다.
 *
 * 사용자가 자기 고정비를 모르는 이유는 그것들이 눈에 띄지 않기 때문이다 — 자동이체라 손으로
 * 적지 않고, 알림이 와도 확인할 게 없어 넘긴다. 그래서 반대로, 앱이 본 것 전부를 놓고
 * «같은 이름 · 같은 금액이 여러 주기에 걸쳐 나왔나»만 센다.
 *
 * 재료는 두 갈래다 — 사용자가 적은 지출([ExpenseRow])과 앱이 본 결제 알림([PaymentLogEntry]).
 * 어느 쪽에서 왔든 [MoneyEvent] 로 같아져 **한 목록**에 담긴다.
 *
 * **다만 금액을 보는 잣대가 갈린다.** 알림의 이름은 앱이 문구에서 짐작한 것이라 틀릴 수 있어,
 * «금액이 거의 같다»는 뒷받침이 더 필요하다. 손으로 적은 제목은 사용자가 직접 고른 말이다 —
 * 석 달 내리 «관리비»라고 적었다면 그건 금액이 98,000 · 131,000 · 112,000 으로 흔들려도
 * 관리비다. 그래서 기록에서 온 이름에는 금액 폭([TOLERANCE_PERCENT])을 걸지 않는다.
 * 이 구분이 없으면 정작 달마다 액수가 달라지는 것들(관리비·전기·학원비)만 골라서 빠진다.
 *
 * Android 에 의존하지 않아 단위 테스트로 고정한다.
 */
object RecurringCosts {

    /** 몇 주기에서 보여야 고정비로 보나. 두 번이면 «되풀이»라고 말할 수 있는 최소다. */
    const val MIN_CYCLES = 2

    /** 돌아보는 주기 수. 넉 달 치를 들고 있지만 판정은 최근 셋만 본다. */
    const val LOOK_BACK_CYCLES = 3

    /**
     * **알림에서만 본 것**의 금액이 이만큼까지 흔들려도 같은 고정비로 본다.
     *
     * 관리비·통신비는 달마다 조금씩 다르고, 그걸 «다른 항목»으로 갈라 버리면 정작 찾아야 할
     * 것들을 못 찾는다. 반대로 폭을 더 넓히면 커피값과 밥값이 한 덩어리가 된다.
     *
     * 손으로 적은 기록에는 이 폭을 걸지 않는다 — 아래 [of] 설명 참고.
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

    /**
     * **되풀이를 아직 확인 못 했을 때**의 최소 금액. 보통보다 훨씬 높다.
     *
     * 한 주기밖에 없으면 「같은 것이 또 나갔다」를 볼 방법이 없다. 그때도 빈 화면을 주느니
     * 큰 금액부터 늘어놓고 사람이 고르게 하는 편이 낫다 — 자기 월세가 무엇인지는 사용자가
     * 안다. 다만 「한 번 나간 17,000원」을 고정비 후보라고 내미는 것은 외식 한 번과 구별이
     * 안 되므로, 이 모드에서는 문턱을 높여 큰 것만 보여 준다.
     */
    const val SINGLE_MIN_AMOUNT = 30_000L

    /** 한 번에 제안하는 최대 줄 수. 넘치면 큰 것부터. */
    const val MAX_CANDIDATES = 12

    /**
     * 되풀이를 찾을 때 보는 주기들 — **진행 중인 주기 + 끝난 주기 [LOOK_BACK_CYCLES] 개.**
     *
     * 진행 중인 주기를 함께 보는 이유는 그러지 않으면 **이번 달에 적은 것이 통째로 버려지기**
     * 때문이다. 이달 중순에 앱을 깔고 한 달을 쓴 사람은 데이터의 절반이 진행 중인 주기에 있는데,
     * 그걸 빼면 「한 주기뿐」이 되어 아무것도 못 찾는다.
     *
     * 진행 중인 주기를 넣어도 판정이 느슨해지지는 않는다. 칸이 하나 늘 뿐이고 «서로 다른 주기에
     * 두 번»이라는 조건은 그대로다 — 이번 달 월세가 아직 안 나갔으면 그 칸이 비어 있을 뿐,
     * 앞 주기들로 여전히 걸린다.
     */
    fun lookback(today: LocalDate, payDay: Int): List<BudgetCycle> =
        listOf(Payday.cycleOf(today, payDay)) + recentCycles(today, payDay)

    /**
     * [today] 가 속한 주기를 뺀, 바로 앞 [count] 개의 **끝난 주기**.
     * 결산·리포트가 «막 끝난 주기»를 가리킬 때 쓴다.
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

        val grouped: Map<String, List<Pair<Int, MoneyEvent>>> =
            placed.groupBy { normalize(it.second.name) }

        // **자료가 몇 주기에 걸쳐 있나**로 갈린다. 「되풀이를 못 찾았다」와 「되풀이를 볼 수가
        // 없었다」는 다른 말이다. 두 주기 이상 있었는데 못 찾았다면 그건 진짜 답이므로
        // 짐작을 대신 내밀지 않는다 — 근거 있는 목록에 짐작을 섞으면 사용자가 둘을 구별하지
        // 못하고, 그러면 근거 있는 쪽까지 못 믿게 된다.
        val cyclesWithData: Int = placed.map { it.first }.distinct().size
        if (cyclesWithData >= MIN_CYCLES) {
            return grouped
                .mapNotNull { (_, entries) -> candidateOf(entries) }
                .sortedByDescending { it.amount }
                .take(MAX_CANDIDATES)
        }

        // 한 주기뿐이라 되풀이를 볼 방법이 없었다. 빈 화면을 주느니 큰 금액부터 늘어놓고
        // 사람이 고르게 한다 — 자기 월세가 무엇인지는 사용자가 안다.
        return grouped
            .mapNotNull { (_, entries) -> singleOf(entries) }
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
        val steady: Boolean = high * 100L <= low * (100L + TOLERANCE_PERCENT)

        // 사람이 적은 제목이면 이름만으로 충분하다. 앱이 짐작한 이름에는 금액의 뒷받침이 필요하다.
        val fromRecord: Boolean = entries.any { it.second.fromRecord }
        if (!steady && !fromRecord) return null

        val latest: MoneyEvent = entries.maxByOrNull { it.second.date }?.second ?: return null

        // 금액이 안정적이면 가장 최근 값(오른 보험료가 다음 달에 나간다). 흔들리면 평균 —
        // «지금 값»이라 할 만한 것이 없을 때 마지막 달을 집으면 그 달이 유난히 많았을 뿐일 수 있다.
        val amount: Long = if (steady) latest.amount else average(amounts)
        if (amount < MIN_AMOUNT) return null

        return FixedCostCandidate(
            name = latest.name.trim(),
            amount = amount,
            cycles = cycleCount,
            fromRecord = fromRecord,
            averaged = !steady,
        )
    }

    /**
     * 되풀이를 확인하지 못한 한 줄. **큰 금액**이라는 것 말고는 근거가 없다.
     *
     * 한 주기에 여러 번 나오는 것은 여기서도 뺀다 — 자주 가는 가게는 금액이 커도 고정비가
     * 아니고, 그 조건이 이 느슨한 모드에서 목록을 지키는 유일한 줄이다.
     */
    private fun singleOf(entries: List<Pair<Int, MoneyEvent>>): FixedCostCandidate? {
        val perCycle: Map<Int, Int> = entries.groupingBy { it.first }.eachCount()
        if (perCycle.values.any { it > MAX_PER_CYCLE }) return null

        val latest: MoneyEvent = entries.maxByOrNull { it.second.date }?.second ?: return null
        if (latest.amount < SINGLE_MIN_AMOUNT) return null

        return FixedCostCandidate(
            name = latest.name.trim(),
            amount = latest.amount,
            cycles = entries.map { it.first }.distinct().size,
            fromRecord = entries.any { it.second.fromRecord },
            averaged = false,
            repeated = false,
        )
    }

    /** 반올림한 평균. 예산에 들어갈 숫자라 내림으로 조금씩 모자라게 잡지 않는다. */
    private fun average(amounts: List<Long>): Long =
        (amounts.sum() + amounts.size / 2) / amounts.size

    /**
     * 이름을 견주기 좋게 다듬는다. 띄어쓰기만 없앤다 — 「우리카드 우아한형제들」과
     * 「우리카드우아한형제들」은 같은 곳이다. 그 이상 손대면(숫자·기호 제거) 「KT 5G」와
     * 「KT」가 한 덩어리가 되어 없는 고정비를 만들어 낸다.
     */
    fun normalize(raw: String): String = raw.filterNot { it.isWhitespace() }
}
