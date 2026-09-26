package com.calc.expense

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * 최고 기록 하나. [from]~[to] 가 그 기록이 세워진 기간이다(하루짜리면 둘이 같다).
 *
 * @param value 아낀 돈·일수·한 주 합계. 무엇인지는 [PersonalBests] 의 자리가 정한다
 * @param purse 곳간별로 센 기록(무지출)이면 어느 곳간인지
 * @param spent 「가장 많이 아낀 날」에 실제로 쓴 돈
 */
data class Best(
    val value: Long,
    val from: LocalDate,
    val to: LocalDate,
    val purse: Purse? = null,
    val spent: Long = 0L,
)

/** 나의 기록 다섯. 비어 있으면 아직 그 기록이 설 만큼 쓰지 않은 것이다. */
data class PersonalBests(
    /** 하루치에서 가장 많이 남긴 날. */
    val savedDay: Best? = null,
    /** 하루치 안에서 이어 쓴 최장 일수. */
    val keepRun: Best? = null,
    /** 곳간을 0원으로 이어 둔 최장 일수. 곳간이 여럿이면 곳간별로 세고 더 긴 쪽이다. */
    val noSpendRun: Best? = null,
    /** 월~일 한 주 합계가 가장 적었던 주. **작을수록** 좋다. */
    val cheapestWeek: Best? = null,
    /** 하루도 빠짐없이 적은 최장 일수. */
    val recordRun: Best? = null,
)

/** 더해지기만 하는 횟수. */
data class Tallies(
    val sDays: Int = 0,
    val noSpendDays: Int = 0,
    val keptDays: Int = 0,
    val keptWeeks: Int = 0,
    val recordedDays: Int = 0,
    val comebacks: Int = 0,
    val tidyCycles: Int = 0,
) {
    fun of(tally: Tally): Int = when (tally) {
        Tally.S_DAYS -> sDays
        Tally.NO_SPEND_DAYS -> noSpendDays
        Tally.KEPT_WEEKS -> keptWeeks
        Tally.RECORDED_DAYS -> recordedDays
        Tally.COMEBACKS -> comebacks
        Tally.TIDY_CYCLES -> tidyCycles
    }
}

/** 도감 한 벌. [blooms] 는 핀 꽃과 핀 날이다. */
data class DogamResult(
    val blooms: Map<Plant, LocalDate> = emptyMap(),
    val bests: PersonalBests = PersonalBests(),
    val tallies: Tallies = Tallies(),
)

/**
 * 도감 계산에 필요한 것. 전부 값이라 Android 없이 테스트한다.
 *
 * @param rows 처음부터 오늘까지의 지출 줄(연결된 곳간 전부)
 * @param purses 세는 곳간. 앱에서는 개인 곳간 하나만 넘긴다([DogamRepository]). 여럿이면
 *   금액은 합쳐 세고, 무지출은 곳간마다 따로 센다
 * @param monthlyBudgets 곳간별 한 주기 예산. 하루치는 여기서 거꾸로 낸다
 */
data class DogamInput(
    val today: LocalDate,
    val rows: List<PursedRow>,
    val purses: List<Purse>,
    val monthlyBudgets: Map<Purse, Long>,
    val payDay: Int,
    val hasFixedCosts: Boolean,
)

/**
 * 지난 기록으로 도감을 채운다.
 *
 * **몇 가지 약속.**
 * - 오늘은 아직 끝나지 않았으므로 금액 쪽은 어제까지만 본다. 적는 습관만 오늘을 넣는다.
 * - 처음 쓴 첫 주([FIRST_WEEK_DAYS])는 **금액 쪽에서 뺀다.** 막 쓰기 시작한 때는 덜 적혀서
 *   아낀 돈이 부풀고, 그 날이 앞으로 깰 수 없는 유령 기록이 된다. 적은 날 수는 덜 적어도
 *   부풀지 않으므로 적는 습관은 첫날부터 센다.
 * - 기록이 없는 날은 0원으로 본다(곳간 정산과 같다). 다만 **앞뒤로 적은 날 사이의 짧은 틈**
 *   ([MAX_GAP_DAYS] 일 이하)만 그렇다. 그보다 긴 틈은 앱을 안 쓴 것으로 보고 세지 않는다 —
 *   안 그러면 한동안 잊고 지낸 날들이 전부 「무지출」「하루치 지킴」으로 쌓인다.
 * - 하루치는 **지금 예산**으로 거슬러 계산한다. 예산 이력은 저장돼 있지 않다.
 * - S 등급은 적은 날만 매긴다([GradeRepository.day] 와 같다).
 * - 다시 일어서기는 하루치를 넘긴 **바로 다음 날**만 본다. 그 사이에 기록 없는 긴 틈이 있으면 치지 않는다.
 */
object Dogam {

    const val FIRST_WEEK_DAYS = 7L
    const val MAX_GAP_DAYS = 2L
    const val TIDY_MIN_ROWS = 10

    /** 신기록 표시를 붙이는 기간. 이 안에 세워진(또는 이어지는) 기록이다. */
    const val FRESH_DAYS = 3L

    fun evaluate(input: DogamInput): DogamResult {
        val today: LocalDate = input.today
        val rows: List<PursedRow> = input.rows.filter { !it.row.date.isAfter(today) }
        if (rows.isEmpty()) return DogamResult()

        val spentByDay: Map<LocalDate, Map<Purse, Long>> = rows
            .groupBy { it.row.date }
            .mapValues { (_, list) -> list.groupBy { it.purse }.mapValues { (_, l) -> l.sumOf { it.row.amount } } }
        val recorded: Set<LocalDate> = spentByDay.keys
        val first: LocalDate = recorded.min()
        val active: Set<LocalDate> = activeDays(recorded)

        val blooms = LinkedHashMap<Plant, LocalDate>()
        fun bloom(plant: Plant, day: LocalDate) {
            if (!blooms.containsKey(plant)) blooms[plant] = day
        }

        bloom(Plant.SPROUT, first)

        // 적는 습관 — 첫날부터 오늘까지. 이어진 날과, 끊겨도 이어서 세는 누적 날을 함께 센다.
        val recordStreak = Streak()
        var recordedDays = 0
        var day: LocalDate = first
        while (!day.isAfter(today)) {
            if (day in recorded) {
                recordedDays++
                bloomTally(Tally.RECORDED_DAYS, recordedDays, day, ::bloom)
                recordStreak.hit(day)
                if (recordStreak.length == 7) bloom(Plant.ROSEMARY, day)
                if (recordStreak.length == 30) bloom(Plant.FORGET_ME_NOT, day)
            } else {
                recordStreak.miss()
            }
            day = day.plusDays(1)
        }

        // 금액 쪽 — 첫 주를 빼고 어제까지.
        val moneyStart: LocalDate = first.plusDays(FIRST_WEEK_DAYS)
        val end: LocalDate = today.minusDays(1)
        fun budgetOn(d: LocalDate): Long {
            val cycle: BudgetCycle = Payday.cycleOf(d, input.payDay)
            return input.purses.sumOf { Budget.baseRate(input.monthlyBudgets[it] ?: 0L, cycle) }
        }
        fun totalOn(d: LocalDate): Long = spentByDay[d]?.values?.sum() ?: 0L

        var savedDay: Best? = null
        var sDays = 0
        var noSpendDays = 0
        var keptDays = 0
        var comebacks = 0
        var overYesterday = false
        val keep = Streak()
        val quiet: Map<Purse, Streak> = input.purses.associateWith { Streak(it) }

        day = moneyStart
        while (!day.isAfter(end)) {
            val budget: Long = budgetOn(day)
            if (day !in active || budget <= 0L) {
                overYesterday = false
                keep.miss()
                quiet.values.forEach { it.miss() }
                day = day.plusDays(1)
                continue
            }

            val byPurse: Map<Purse, Long> = spentByDay[day] ?: emptyMap()
            val total: Long = byPurse.values.sum()

            val saved: Long = budget - total
            if (saved > 0L && (savedDay == null || saved > savedDay.value)) {
                savedDay = Best(saved, day, day, spent = total)
            }

            if (day in recorded && Grading.of(total, budget) == Grade.S) {
                sDays++
                bloomTally(Tally.S_DAYS, sDays, day, ::bloom)
            }

            if (total <= budget && overYesterday) {
                comebacks++
                bloomTally(Tally.COMEBACKS, comebacks, day, ::bloom)
            }
            overYesterday = total > budget

            if (total <= budget) {
                keptDays++
                keep.hit(day)
                if (keep.length == 3) bloom(Plant.CLOVER, day)
                if (keep.length == 7) bloom(Plant.PLUM, day)
                if (keep.length == 14) bloom(Plant.BAMBOO, day)
            } else {
                keep.miss()
            }

            var anyQuiet = false
            for ((purse, streak) in quiet) {
                if ((byPurse[purse] ?: 0L) == 0L) {
                    streak.hit(day)
                    anyQuiet = true
                } else {
                    streak.miss()
                }
            }
            if (anyQuiet) {
                noSpendDays++
                bloomTally(Tally.NO_SPEND_DAYS, noSpendDays, day, ::bloom)
            }

            day = day.plusDays(1)
        }

        // 월~일 한 주. 이레가 전부 셀 수 있는 날이어야 한다.
        var keptWeeks = 0
        var cheapestWeek: Best? = null
        var monday: LocalDate = moneyStart.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY))
        while (!monday.plusDays(6).isAfter(end)) {
            val week: List<LocalDate> = (0L..6L).map { monday.plusDays(it) }
            val budgets: List<Long> = week.map { budgetOn(it) }
            if (week.all { it in active } && budgets.all { it > 0L }) {
                val sunday: LocalDate = week.last()
                val spent: Long = week.sumOf { totalOn(it) }
                if (spent <= budgets.sum()) {
                    keptWeeks++
                    bloomTally(Tally.KEPT_WEEKS, keptWeeks, sunday, ::bloom)
                }
                if (cheapestWeek == null || spent < cheapestWeek.value) {
                    cheapestWeek = Best(spent, monday, sunday)
                }
            }
            monday = monday.plusDays(7)
        }

        // 한 주기. 첫 주가 지난 뒤 시작해 어제까지 끝난 주기만, 빈틈 없이 센다.
        var cycle: BudgetCycle = Payday.cycleOf(first, input.payDay)
        while (!cycle.lastDay.isAfter(end)) {
            val days: List<LocalDate> = daysOf(cycle.start, cycle.lastDay)
            if (!cycle.start.isBefore(moneyStart) && days.all { it in active }) {
                val budget: Long = days.sumOf { budgetOn(it) }
                if (budget > 0L && days.sumOf { totalOn(it) } <= budget) bloom(Plant.MONEY_TREE, cycle.lastDay)
            }
            cycle = Payday.cycleOf(cycle.endExclusive, input.payDay)
        }

        // 정리. 이번 주기도 오늘까지 본다 — 정리는 지금 해 두면 지금 핀다.
        var tidyCycles = 0
        cycle = Payday.cycleOf(first, input.payDay)
        while (!cycle.start.isAfter(today)) {
            val inCycle: List<PursedRow> = rows.filter { cycle.contains(it.row.date) }
            if (inCycle.size >= TIDY_MIN_ROWS && inCycle.all { it.row.category.isNotBlank() }) {
                tidyCycles++
                bloomTally(Tally.TIDY_CYCLES, tidyCycles, if (cycle.lastDay.isAfter(today)) today else cycle.lastDay, ::bloom)
            }
            cycle = Payday.cycleOf(cycle.endExclusive, input.payDay)
        }

        if (input.hasFixedCosts) bloom(Plant.MONSTERA, today)

        val noSpendRun: Best? = quiet.values
            .mapNotNull { it.best }
            .fold(null as Best?) { acc, b -> if (acc == null || b.value > acc.value) b else acc }

        return DogamResult(
            blooms = blooms,
            bests = PersonalBests(
                savedDay = savedDay,
                keepRun = keep.best,
                noSpendRun = noSpendRun,
                cheapestWeek = cheapestWeek,
                recordRun = recordStreak.best,
            ),
            tallies = Tallies(
                sDays = sDays,
                noSpendDays = noSpendDays,
                keptDays = keptDays,
                keptWeeks = keptWeeks,
                recordedDays = recordedDays,
                comebacks = comebacks,
                tidyCycles = tidyCycles,
            ),
        )
    }

    /**
     * 저장해 둔 도감에 새로 계산한 것을 덧댄다. **줄어드는 것은 하나도 없다.**
     *
     * 예산을 낮추거나 지난 기록을 지우면 다시 계산한 값이 전보다 작아질 수 있다. 그래도
     * 한 번 핀 꽃, 한 번 세운 기록, 한 번 쌓인 횟수는 그대로 둔다 — 이 탭이 있는 이유가 그것이다.
     */
    fun merge(stored: DogamResult, fresh: DogamResult): DogamResult {
        val blooms = LinkedHashMap<Plant, LocalDate>()
        for (plant in Plant.entries) {
            val a: LocalDate? = stored.blooms[plant]
            val b: LocalDate? = fresh.blooms[plant]
            val kept: LocalDate = when {
                a == null -> b
                b == null -> a
                b.isBefore(a) -> b
                else -> a
            } ?: continue
            blooms[plant] = kept
        }
        return DogamResult(
            blooms = blooms,
            bests = PersonalBests(
                savedDay = higher(stored.bests.savedDay, fresh.bests.savedDay),
                keepRun = higher(stored.bests.keepRun, fresh.bests.keepRun),
                noSpendRun = higher(stored.bests.noSpendRun, fresh.bests.noSpendRun),
                cheapestWeek = lower(stored.bests.cheapestWeek, fresh.bests.cheapestWeek),
                recordRun = higher(stored.bests.recordRun, fresh.bests.recordRun),
            ),
            tallies = Tallies(
                sDays = maxOf(stored.tallies.sDays, fresh.tallies.sDays),
                noSpendDays = maxOf(stored.tallies.noSpendDays, fresh.tallies.noSpendDays),
                keptDays = maxOf(stored.tallies.keptDays, fresh.tallies.keptDays),
                keptWeeks = maxOf(stored.tallies.keptWeeks, fresh.tallies.keptWeeks),
                recordedDays = maxOf(stored.tallies.recordedDays, fresh.tallies.recordedDays),
                comebacks = maxOf(stored.tallies.comebacks, fresh.tallies.comebacks),
                tidyCycles = maxOf(stored.tallies.tidyCycles, fresh.tallies.tidyCycles),
            ),
        )
    }

    /**
     * 「다음에 필 꽃」. 횟수로 세는 꽃 중 **가장 가까운 것**(채운 비율이 가장 큰 것)이다.
     * 그런 꽃이 다 피었으면 아직 안 핀 첫 꽃을, 전부 피었으면 null 을 돌려준다.
     */
    fun next(result: DogamResult): Plant? {
        val waiting: List<Plant> = Plant.entries.filter { it !in result.blooms }
        val counted: Plant? = waiting
            .filter { it.tally != null && it.target > 0 }
            .maxByOrNull { result.tallies.of(it.tally!!).toDouble() / it.target }
        return counted ?: waiting.firstOrNull()
    }

    /** 이 기록이 방금 세워졌거나 지금 이어지고 있나. */
    fun isFresh(best: Best?, today: LocalDate): Boolean =
        best != null && !best.to.isBefore(today.minusDays(FRESH_DAYS))

    /** 기록이 있는 날과, 그 사이의 짧은 틈. */
    private fun activeDays(recorded: Set<LocalDate>): Set<LocalDate> {
        val active = HashSet<LocalDate>(recorded)
        val sorted: List<LocalDate> = recorded.sorted()
        for (i in 1 until sorted.size) {
            val gap: Long = sorted[i].toEpochDay() - sorted[i - 1].toEpochDay() - 1L
            if (gap in 1L..MAX_GAP_DAYS) {
                for (k in 1L..gap) active.add(sorted[i - 1].plusDays(k))
            }
        }
        return active
    }

    private fun bloomTally(tally: Tally, count: Int, day: LocalDate, bloom: (Plant, LocalDate) -> Unit) {
        for (plant in Plant.entries) {
            if (plant.tally == tally && plant.target == count) bloom(plant, day)
        }
    }

    private fun daysOf(from: LocalDate, to: LocalDate): List<LocalDate> {
        val out = ArrayList<LocalDate>()
        var d: LocalDate = from
        while (!d.isAfter(to)) {
            out.add(d)
            d = d.plusDays(1)
        }
        return out
    }

    /** 같으면 먼저 세운 쪽을 남긴다 — 날짜가 괜히 바뀌지 않게. */
    private fun higher(a: Best?, b: Best?): Best? = when {
        a == null -> b
        b == null -> a
        b.value > a.value -> b
        else -> a
    }

    private fun lower(a: Best?, b: Best?): Best? = when {
        a == null -> b
        b == null -> a
        b.value < a.value -> b
        else -> a
    }

    /** 이어지는 날 세기. 끊겨도 [best] 는 남는다. */
    private class Streak(private val purse: Purse? = null) {
        var length: Int = 0
            private set
        private var start: LocalDate? = null
        var best: Best? = null
            private set

        fun hit(day: LocalDate) {
            if (length == 0) start = day
            length++
            val current: Best? = best
            if (current == null || length > current.value) {
                best = Best(length.toLong(), start ?: day, day, purse = purse)
            }
        }

        fun miss() {
            length = 0
            start = null
        }
    }
}

/** [Best] 를 한 줄 문자열로. 폰에 저장할 때만 쓴다. */
object BestCodec {

    fun encode(best: Best?): String? {
        if (best == null) return null
        return listOf(
            best.value.toString(),
            best.from.toString(),
            best.to.toString(),
            best.purse?.key.orEmpty(),
            best.spent.toString(),
        ).joinToString("|")
    }

    fun decode(raw: String?): Best? {
        if (raw.isNullOrBlank()) return null
        val parts: List<String> = raw.split("|")
        if (parts.size < 5) return null
        return try {
            Best(
                value = parts[0].toLong(),
                from = LocalDate.parse(parts[1]),
                to = LocalDate.parse(parts[2]),
                purse = Purse.entries.firstOrNull { it.key == parts[3] },
                spent = parts[4].toLong(),
            )
        } catch (_: Exception) {
            null
        }
    }
}
