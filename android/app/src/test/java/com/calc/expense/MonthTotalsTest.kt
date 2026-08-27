package com.calc.expense

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class MonthTotalsTest {

    @Test
    fun `저장하고 다시 읽으면 같은 값이다`() {
        val totals = mapOf(
            LocalDate.of(2026, 8, 26) to 15_000L,
            LocalDate.of(2026, 8, 27) to 12_400L,
        )

        assertEquals(totals, MonthTotals.decode(MonthTotals.encode(totals)))
    }

    @Test
    fun `0원인 날은 저장하지 않는다`() {
        val totals = mapOf(
            LocalDate.of(2026, 8, 26) to 0L,
            LocalDate.of(2026, 8, 27) to 12_400L,
        )

        val back = MonthTotals.decode(MonthTotals.encode(totals))
        assertEquals(1, back.size)
        assertEquals(12_400L, back[LocalDate.of(2026, 8, 27)])
    }

    @Test
    fun `비어 있으면 빈 맵이다`() {
        assertEquals(0, MonthTotals.decode(null).size)
        assertEquals(0, MonthTotals.decode("").size)
        assertEquals(0, MonthTotals.decode("   ").size)
        assertEquals(0, MonthTotals.encode(emptyMap()).let { MonthTotals.decode(it) }.size)
    }

    @Test
    fun `깨진 값은 예외 대신 빈 맵이 된다`() {
        // 캐시는 파생 데이터다. 앱이 못 켜지는 것보다 Notion 에서 다시 받는 편이 낫다
        assertEquals(0, MonthTotals.decode("{잘림").size)
        assertEquals(0, MonthTotals.decode("[]").size)
    }

    @Test
    fun `날짜가 아닌 키는 건너뛴다`() {
        val back = MonthTotals.decode("""{"어제":5000,"2026-08-27":12400}""")
        assertEquals(1, back.size)
        assertEquals(12_400L, back[LocalDate.of(2026, 8, 27)])
    }

    @Test
    fun `음수도 그대로 보존한다`() {
        // 환불 같은 조정이 들어올 수 있다. 여기서 판단하지 않는다
        val totals = mapOf(LocalDate.of(2026, 8, 27) to -3_000L)
        assertEquals(totals, MonthTotals.decode(MonthTotals.encode(totals)))
    }
}
