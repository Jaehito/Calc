package com.calc.expense

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PaydayTest {

    private fun d(y: Int, m: Int, day: Int): LocalDate = LocalDate.of(y, m, day)

    @Test
    fun `월급날 1일이면 달력 월과 같다`() {
        val c = Payday.cycleOf(d(2026, 8, 15), 1)

        assertEquals(d(2026, 8, 1), c.start)
        assertEquals(d(2026, 9, 1), c.endExclusive)
        assertEquals(d(2026, 8, 31), c.lastDay)
        assertEquals(31, c.length)
    }

    @Test
    fun `월급날 이후면 이번 달 월급날부터 시작한다`() {
        val c = Payday.cycleOf(d(2026, 8, 27), 25)

        assertEquals(d(2026, 8, 25), c.start)
        assertEquals(d(2026, 9, 25), c.endExclusive)
        assertEquals(d(2026, 9, 24), c.lastDay)
        assertEquals(31, c.length)
    }

    @Test
    fun `월급날 전이면 지난 달 월급날부터 시작한다`() {
        val c = Payday.cycleOf(d(2026, 9, 3), 25)

        assertEquals(d(2026, 8, 25), c.start)
        assertEquals(d(2026, 9, 25), c.endExclusive)
    }

    @Test
    fun `월급날 당일은 새 주기의 첫날이다`() {
        val c = Payday.cycleOf(d(2026, 9, 25), 25)

        assertEquals(d(2026, 9, 25), c.start)
        assertEquals(d(2026, 10, 25), c.endExclusive)
    }

    @Test
    fun `31일 지정인데 2월이면 말일로 당겨진다`() {
        // 없는 날짜를 만들지 않는다
        assertEquals(d(2026, 2, 28), Payday.dayIn(2026, 2, 31))
        assertEquals(d(2028, 2, 29), Payday.dayIn(2028, 2, 31)) // 윤년

        val c = Payday.cycleOf(d(2026, 2, 15), 31)
        assertEquals(d(2026, 1, 31), c.start)
        assertEquals(d(2026, 2, 28), c.endExclusive)
        assertEquals(28, c.length)
    }

    @Test
    fun `말일로 당겨진 날도 주기의 시작으로 잡힌다`() {
        val c = Payday.cycleOf(d(2026, 2, 28), 31)

        assertEquals(d(2026, 2, 28), c.start)
        assertEquals(d(2026, 3, 31), c.endExclusive)
    }

    @Test
    fun `주기가 해를 넘어간다`() {
        val c = Payday.cycleOf(d(2026, 12, 30), 25)

        assertEquals(d(2026, 12, 25), c.start)
        assertEquals(d(2027, 1, 25), c.endExclusive)
    }

    @Test
    fun `범위 밖 값은 1과 31 사이로 눌린다`() {
        assertEquals(1, Payday.normalize(0))
        assertEquals(1, Payday.normalize(-5))
        assertEquals(31, Payday.normalize(99))
        assertEquals(25, Payday.normalize(25))
    }

    @Test
    fun `포함 여부는 시작일부터 마지막날까지다`() {
        val c = Payday.cycleOf(d(2026, 8, 27), 25)

        assertTrue(c.contains(d(2026, 8, 25)))
        assertTrue(c.contains(d(2026, 9, 24)))
        assertFalse(c.contains(d(2026, 8, 24)))
        assertFalse(c.contains(d(2026, 9, 25)))
    }

    @Test
    fun `남은 날은 오늘을 포함한다`() {
        val c = Payday.cycleOf(d(2026, 8, 27), 25)

        // 8/27 부터 9/24 까지 = 29일
        assertEquals(29, c.daysLeftFrom(d(2026, 8, 27)))
        assertEquals(1, c.daysLeftFrom(d(2026, 9, 24)))
        assertEquals(0, c.daysLeftFrom(d(2026, 9, 25)))
        // 주기 시작 전이면 주기 전체
        assertEquals(c.length, c.daysLeftFrom(d(2026, 8, 1)))
    }

    @Test
    fun `연속한 주기는 빈틈도 겹침도 없다`() {
        var day = d(2026, 1, 1)
        var previous: BudgetCycle? = null

        while (day.isBefore(d(2027, 6, 1))) {
            val c = Payday.cycleOf(day, 25)
            assertTrue("$day 가 자기 주기에 없다", c.contains(day))
            if (previous != null && previous.start != c.start) {
                assertEquals("주기 사이에 틈이 있다", previous.endExclusive, c.start)
            }
            previous = c
            day = day.plusDays(1)
        }
    }
}
