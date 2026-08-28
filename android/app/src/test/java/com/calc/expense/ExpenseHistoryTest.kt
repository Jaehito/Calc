package com.calc.expense

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ExpenseHistoryTest {

    // ---- NotionRows.readRows (행 파싱) ----

    private fun rowJson(id: String, name: String, date: String?, amount: String, category: String?): String {
        val dateJson = if (date == null) """{"date":null}""" else """{"date":{"start":"$date"}}"""
        val catJson = if (category == null) """{"select":null}""" else """{"select":{"name":"$category"}}"""
        return """
            {"id":"$id","properties":{
              "이름":{"title":[{"plain_text":"$name"}]},
              "날짜":$dateJson,
              "금액":{"number":$amount},
              "카테고리":$catJson
            }}
        """.trimIndent()
    }

    private fun readRows(vararg rows: String, categoryProp: String = "카테고리"): List<ExpenseRow> {
        val page = JSONObject("""{"results":[${rows.joinToString(",")}]}""")
        val into = ArrayList<ExpenseRow>()
        NotionRows.readRows(page, "이름", "금액", "날짜", categoryProp, into)
        return into
    }

    @Test
    fun `행에서 이름과 금액과 날짜와 카테고리를 읽는다`() {
        val rows = readRows(rowJson("p1", "장보기", "2026-08-27", "28700", "식료품"))

        assertEquals(1, rows.size)
        assertEquals("p1", rows[0].id)
        assertEquals("장보기", rows[0].name)
        assertEquals(28_700L, rows[0].amount)
        assertEquals(LocalDate.of(2026, 8, 27), rows[0].date)
        assertEquals("식료품", rows[0].category)
    }

    @Test
    fun `카테고리가 없으면 빈 문자열이다`() {
        val rows = readRows(rowJson("p1", "택시", "2026-08-27", "5900", null))
        assertEquals("", rows[0].category)
    }

    @Test
    fun `카테고리 속성을 안 쓰면 읽지 않는다`() {
        val rows = readRows(rowJson("p1", "택시", "2026-08-27", "5900", "교통"), categoryProp = "")
        assertEquals("", rows[0].category)
    }

    @Test
    fun `날짜나 금액이 없는 행은 건너뛴다`() {
        val rows = readRows(
            rowJson("p1", "날짜없음", null, "5000", "생활"),
            rowJson("p2", "금액없음", "2026-08-27", "null", "생활"),
            rowJson("p3", "정상", "2026-08-27", "4500", "간식"),
        )
        assertEquals(1, rows.size)
        assertEquals("정상", rows[0].name)
    }

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
