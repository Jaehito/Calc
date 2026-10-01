package com.calc.expense

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class RecordHighlightTest {

    private val today: LocalDate = LocalDate.of(2026, 10, 1)

    private fun best(value: Long, to: LocalDate) = Best(value = value, from = to.minusDays(value), to = to)

    @Test fun `기록이 하나도 없으면 고르지 않는다`() {
        assertNull(RecordHighlight.of(PersonalBests(), today))
    }

    @Test fun `요즘 세운 기록이 있으면 그중 가장 최근 것`() {
        val bests = PersonalBests(
            recordRun = best(30, today.minusDays(20)),
            keepRun = best(12, today.minusDays(2)),
            savedDay = best(38_400, today.minusDays(1)),
        )
        assertEquals(RecordKind.SAVED_DAY, RecordHighlight.of(bests, today)?.kind)
    }

    @Test fun `요즘 세운 기록이 없으면 기록 최장 연속`() {
        val bests = PersonalBests(
            keepRun = best(12, today.minusDays(40)),
            recordRun = best(30, today.minusDays(20)),
        )
        assertEquals(RecordKind.RECORD_RUN, RecordHighlight.of(bests, today)?.kind)
    }

    @Test fun `기록 최장 연속도 없으면 있는 것 중 첫째`() {
        val bests = PersonalBests(cheapestWeek = best(214_000, today.minusDays(30)))
        assertEquals(RecordKind.CHEAPEST_WEEK, RecordHighlight.of(bests, today)?.kind)
    }
}
