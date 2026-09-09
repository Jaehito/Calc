package com.calc.expense

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ExpenseHistoryTest {

    // ---- ExpenseHistoryGrouping (날짜별 묶기) ----

    private fun row(name: String, date: LocalDate, amount: Long): ExpenseRow =
        ExpenseRow(id = name, name = name, amount = amount, date = date, category = "")

    @Test
    fun `최신 날짜부터, 같은 날은 큰 금액부터 묶는다`() {
        val groups = ExpenseHistoryGrouping.groupByDay(
            listOf(
                row("주차", LocalDate.of(2026, 8, 27), 3_000L),
                row("외식", LocalDate.of(2026, 8, 26), 61_000L),
                row("장보기", LocalDate.of(2026, 8, 27), 28_700L),
            ),
        )

        // 8/27 이 먼저(최신), 8/26 이 다음
        assertEquals(LocalDate.of(2026, 8, 27), groups[0].date)
        assertEquals(LocalDate.of(2026, 8, 26), groups[1].date)
        // 8/27 하루 합계 = 31,700, 큰 금액(장보기)이 먼저
        assertEquals(31_700L, groups[0].total)
        assertEquals("장보기", groups[0].rows[0].name)
        assertEquals("주차", groups[0].rows[1].name)
    }

    @Test
    fun `전체 합계는 모든 행의 합이다`() {
        val rows = listOf(
            row("a", LocalDate.of(2026, 8, 27), 3_000L),
            row("b", LocalDate.of(2026, 8, 26), 61_000L),
        )
        assertEquals(64_000L, ExpenseHistoryGrouping.total(rows))
    }

    @Test
    fun `행이 없으면 빈 묶음이다`() {
        assertTrue(ExpenseHistoryGrouping.groupByDay(emptyList()).isEmpty())
        assertEquals(0L, ExpenseHistoryGrouping.total(emptyList()))
    }
}
