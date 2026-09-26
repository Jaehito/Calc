package com.calc.expense

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DogamTest {

    private fun day(d: Int): LocalDate = LocalDate.of(2026, 9, d)

    private var nextId = 0

    private fun row(purse: Purse, d: Int, amount: Long, category: String = "식비"): PursedRow =
        PursedRow(purse, ExpenseRow("r${nextId++}", "점심", amount, day(d), category))

    /** 월급날 1일, 개인 곳간만, 9월(30일) 예산 300,000원 → 하루치 10,000원. */
    private fun personalOnly(rows: List<PursedRow>, today: Int, fixed: Boolean = false): DogamResult =
        Dogam.evaluate(
            DogamInput(
                today = day(today),
                rows = rows,
                purses = listOf(Purse.PERSONAL),
                monthlyBudgets = mapOf(Purse.PERSONAL to 300_000L),
                payDay = 1,
                hasFixedCosts = fixed,
            ),
        )

    /** 9/1~9/20 매일 3,000원(첫날만 100원). 오늘은 9/21. */
    private fun steadyMonth(): DogamResult {
        val rows = ArrayList<PursedRow>()
        rows.add(row(Purse.PERSONAL, 1, 100L))
        for (d in 2..20) rows.add(row(Purse.PERSONAL, d, 3_000L))
        return personalOnly(rows, today = 21)
    }

    @Test
    fun `기록이 없으면 도감도 비어 있다`() {
        val result: DogamResult = personalOnly(emptyList(), today = 21)

        assertTrue(result.blooms.isEmpty())
        assertNull(result.bests.savedDay)
        assertEquals(Tallies(), result.tallies)
    }

    @Test
    fun `첫 기록 날 새싹이, 7일째 로즈마리가 핀다`() {
        val result: DogamResult = steadyMonth()

        assertEquals(day(1), result.blooms[Plant.SPROUT])
        assertEquals(day(7), result.blooms[Plant.ROSEMARY])
        assertFalse(Plant.FORGET_ME_NOT in result.blooms)
        assertEquals(Best(20L, day(1), day(20)), result.bests.recordRun)
    }

    @Test
    fun `처음 쓴 첫 주는 금액 기록에서 빠진다`() {
        val result: DogamResult = steadyMonth()

        // 9/1 은 100원만 적혀 9,900원을 아낀 날이지만 첫 주라 세지 않는다.
        assertEquals(Best(7_000L, day(8), day(8), spent = 3_000L), result.bests.savedDay)
        assertEquals(day(8), result.blooms[Plant.DAISY])
        assertEquals(13, result.tallies.sDays)
    }

    @Test
    fun `S 다섯 번째 날 해바라기가 핀다`() {
        assertEquals(day(12), steadyMonth().blooms[Plant.SUNFLOWER])
    }

    @Test
    fun `하루치를 3일·7일 이어 지키면 세잎클로버와 매화가 핀다`() {
        val result: DogamResult = steadyMonth()

        assertEquals(day(10), result.blooms[Plant.CLOVER])
        assertEquals(day(14), result.blooms[Plant.PLUM])
        assertFalse(Plant.BAMBOO in result.blooms)
        assertEquals(Best(13L, day(8), day(20)), result.bests.keepRun)
        assertEquals(13, result.tallies.keptDays)
    }

    @Test
    fun `하루치를 넘긴 날 이어 지킴이 끊긴다`() {
        val rows = ArrayList<PursedRow>()
        for (d in 1..20) rows.add(row(Purse.PERSONAL, d, if (d == 10) 15_000L else 3_000L))
        val result: DogamResult = personalOnly(rows, today = 21)

        assertEquals(Best(10L, day(11), day(20)), result.bests.keepRun)
        assertEquals(day(13), result.blooms[Plant.CLOVER])
    }

    @Test
    fun `월~일 한 주를 지키면 메리골드가 일요일에 핀다`() {
        val result: DogamResult = steadyMonth()

        // 첫 주 다음 첫 월요일은 9/14. 9/14~9/20 이 한 주다.
        assertEquals(day(20), result.blooms[Plant.MARIGOLD])
        assertEquals(1, result.tallies.keptWeeks)
        assertEquals(Best(21_000L, day(14), day(20)), result.bests.cheapestWeek)
    }

    @Test
    fun `10건 넘게 적은 주기에 미분류가 없으면 민트가 오늘 핀다`() {
        assertEquals(day(21), steadyMonth().blooms[Plant.MINT])
    }

    @Test
    fun `미분류가 하나라도 있으면 민트는 피지 않는다`() {
        val rows = ArrayList<PursedRow>()
        for (d in 1..20) rows.add(row(Purse.PERSONAL, d, 3_000L, category = if (d == 5) "" else "식비"))

        assertFalse(Plant.MINT in personalOnly(rows, today = 21).blooms)
    }

    @Test
    fun `고정비를 정해 두면 몬스테라가 핀다`() {
        val rows: List<PursedRow> = listOf(row(Purse.PERSONAL, 1, 3_000L))

        assertEquals(day(2), personalOnly(rows, today = 2, fixed = true).blooms[Plant.MONSTERA])
        assertFalse(Plant.MONSTERA in personalOnly(rows, today = 2, fixed = false).blooms)
    }

    @Test
    fun `무지출은 곳간별로 센다 — 공용에서 썼어도 개인이 0원이면 무지출의 날이다`() {
        val rows = ArrayList<PursedRow>()
        for (d in 1..12) {
            rows.add(row(Purse.SHARED, d, 5_000L))
            if (d !in 9..11) rows.add(row(Purse.PERSONAL, d, 2_000L))
        }
        val result: DogamResult = Dogam.evaluate(
            DogamInput(
                today = day(13),
                rows = rows,
                purses = listOf(Purse.PERSONAL, Purse.SHARED),
                monthlyBudgets = mapOf(Purse.PERSONAL to 300_000L, Purse.SHARED to 300_000L),
                payDay = 1,
                hasFixedCosts = false,
            ),
        )

        assertEquals(Best(3L, day(9), day(11), purse = Purse.PERSONAL), result.bests.noSpendRun)
        assertEquals(3, result.tallies.noSpendDays)
        assertEquals(day(9), result.blooms[Plant.LAVENDER])
    }

    @Test
    fun `짧은 틈은 0원으로 세고 긴 틈은 세지 않는다`() {
        val rows = ArrayList<PursedRow>()
        for (d in 1..10) rows.add(row(Purse.PERSONAL, d, 3_000L))
        rows.add(row(Purse.PERSONAL, 13, 3_000L)) // 9/11·9/12 두 날 틈 → 무지출로 센다
        rows.add(row(Purse.PERSONAL, 20, 3_000L)) // 9/14~9/19 여섯 날 틈 → 앱을 안 쓴 것
        val result: DogamResult = personalOnly(rows, today = 21)

        assertEquals(2, result.tallies.noSpendDays)
        assertEquals(Best(6L, day(8), day(13)), result.bests.keepRun)
        assertEquals(7, result.tallies.keptDays)
        // 긴 틈이 낀 주는 한 주로 치지 않는다.
        assertEquals(0, result.tallies.keptWeeks)
    }

    @Test
    fun `덧대도 줄어드는 것은 없다`() {
        val stored = DogamResult(
            blooms = mapOf(Plant.DAISY to day(5)),
            bests = PersonalBests(
                savedDay = Best(9_000L, day(5), day(5), spent = 1_000L),
                cheapestWeek = Best(10_000L, day(1), day(7)),
            ),
            tallies = Tallies(sDays = 30, noSpendDays = 1),
        )
        val fresh = DogamResult(
            blooms = mapOf(Plant.DAISY to day(8), Plant.SUNFLOWER to day(12)),
            bests = PersonalBests(
                savedDay = Best(7_000L, day(8), day(8), spent = 3_000L),
                cheapestWeek = Best(21_000L, day(14), day(20)),
                keepRun = Best(13L, day(8), day(20)),
            ),
            tallies = Tallies(sDays = 13, noSpendDays = 2, keptDays = 13, keptWeeks = 1),
        )

        val merged: DogamResult = Dogam.merge(stored, fresh)

        assertEquals(day(5), merged.blooms[Plant.DAISY])
        assertEquals(day(12), merged.blooms[Plant.SUNFLOWER])
        assertEquals(9_000L, merged.bests.savedDay?.value)
        assertEquals(10_000L, merged.bests.cheapestWeek?.value)
        assertEquals(13L, merged.bests.keepRun?.value)
        assertEquals(Tallies(sDays = 30, noSpendDays = 2, keptDays = 13, keptWeeks = 1), merged.tallies)
    }

    @Test
    fun `다음에 필 꽃은 횟수를 가장 많이 채운 꽃이다`() {
        val result = DogamResult(
            blooms = mapOf(Plant.DAISY to day(1), Plant.LAVENDER to day(2)),
            tallies = Tallies(sDays = 3, noSpendDays = 4),
        )

        assertEquals(Plant.LILY_OF_THE_VALLEY, Dogam.next(result))
    }

    @Test
    fun `횟수로 세는 꽃이 다 피면 아직 안 핀 첫 꽃, 전부 피면 없다`() {
        val counted: Map<Plant, LocalDate> = Plant.entries.filter { it.tally != null }.associateWith { day(1) }
        assertEquals(Plant.SPROUT, Dogam.next(DogamResult(blooms = counted)))

        val all: Map<Plant, LocalDate> = Plant.entries.associateWith { day(1) }
        assertNull(Dogam.next(DogamResult(blooms = all)))
    }

    @Test
    fun `최근 사흘 안에 세운 기록만 신기록이다`() {
        assertTrue(Dogam.isFresh(Best(3L, day(1), day(18)), day(21)))
        assertFalse(Dogam.isFresh(Best(3L, day(1), day(17)), day(21)))
        assertFalse(Dogam.isFresh(null, day(21)))
    }

    @Test
    fun `기록은 한 줄로 저장했다 그대로 되돌린다`() {
        val best = Best(2L, day(12), day(13), purse = Purse.PERSONAL, spent = 0L)

        assertEquals(best, BestCodec.decode(BestCodec.encode(best)))
        assertNull(BestCodec.encode(null))
        assertNull(BestCodec.decode("망가진 값"))
    }

    @Test
    fun `꽃말과 조사를 붙인다`() {
        assertEquals("꽃말 「침묵」", Plant.LAVENDER.meaningText)
        assertEquals("뜻 「구멍 난 잎」", Plant.MONSTERA.meaningText)
        assertEquals("이", Plant.subjectParticle("새싹"))
        assertEquals("가", Plant.subjectParticle("라벤더"))
        assertEquals("가", Plant.subjectParticle("S"))
    }

    @Test
    fun `꽃 열여섯 송이, 저장 키가 겹치지 않고 선반마다 셋 이하다`() {
        assertEquals(16, Plant.entries.size)
        assertEquals(Plant.entries.size, Plant.entries.map { it.key }.toSet().size)
        for (shelf in Shelf.entries) assertTrue(Plant.on(shelf).size in 1..3)
    }
}
