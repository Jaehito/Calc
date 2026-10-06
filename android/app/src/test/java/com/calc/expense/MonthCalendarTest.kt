package com.calc.expense

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

class MonthCalendarTest {

    private val october: YearMonth = YearMonth.of(2026, 10)
    private val today: LocalDate = LocalDate.of(2026, 10, 6)

    @Test
    fun `1일 앞을 빈칸으로 채운다 — 일요일 시작`() {
        // 2026년 10월 1일은 목요일 → 일·월·화·수 네 칸이 빈다.
        val cells = MonthCalendar.cells(october, emptyMap(), { 0L }, today, DayOfWeek.SUNDAY)
        assertNull(cells[3].day)
        assertEquals(LocalDate.of(2026, 10, 1), cells[4].day)
    }

    @Test
    fun `월요일 시작이면 빈칸이 셋이다`() {
        val cells = MonthCalendar.cells(october, emptyMap(), { 0L }, today, DayOfWeek.MONDAY)
        assertNull(cells[2].day)
        assertEquals(LocalDate.of(2026, 10, 1), cells[3].day)
    }

    @Test
    fun `칸 수는 늘 7의 배수다`() {
        val cells = MonthCalendar.cells(october, emptyMap(), { 0L }, today, DayOfWeek.SUNDAY)
        assertEquals(0, cells.size % 7)
        assertEquals(35, cells.size)
    }

    @Test
    fun `하루치를 넘긴 날만 넘김으로 칠한다`() {
        val totals = mapOf(LocalDate.of(2026, 10, 1) to 31_200L, LocalDate.of(2026, 10, 2) to 9_800L)
        val cells = MonthCalendar.cells(october, totals, { 20_000L }, today, DayOfWeek.SUNDAY)
        val first = cells.first { it.day == LocalDate.of(2026, 10, 1) }
        val second = cells.first { it.day == LocalDate.of(2026, 10, 2) }
        assertTrue(first.over)
        assertEquals(31_200L, first.spent)
        assertFalse(second.over)
    }

    @Test
    fun `하루치가 없으면 넘김을 따지지 않는다`() {
        val totals = mapOf(LocalDate.of(2026, 10, 1) to 31_200L)
        val cells = MonthCalendar.cells(october, totals, { 0L }, today, DayOfWeek.SUNDAY)
        assertFalse(cells.first { it.day == LocalDate.of(2026, 10, 1) }.over)
    }

    @Test
    fun `오늘과 앞날을 가린다`() {
        val cells = MonthCalendar.cells(october, emptyMap(), { 0L }, today, DayOfWeek.SUNDAY)
        assertTrue(cells.first { it.day == today }.isToday)
        assertTrue(cells.first { it.day == LocalDate.of(2026, 10, 7) }.isFuture)
        assertFalse(cells.first { it.day == today }.isFuture)
    }
}
