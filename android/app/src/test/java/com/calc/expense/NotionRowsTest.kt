package com.calc.expense

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class NotionRowsTest {

    private fun page(vararg rows: String, hasMore: Boolean = false, cursor: String? = null): JSONObject {
        val results = rows.joinToString(",")
        val tail = if (cursor == null) "null" else "\"$cursor\""
        return JSONObject("""{"results":[$results],"has_more":$hasMore,"next_cursor":$tail}""")
    }

    private fun row(date: String?, amount: String): String {
        val dateJson = if (date == null) """{"date":null}""" else """{"date":{"start":"$date"}}"""
        return """{"properties":{"날짜":$dateJson,"금액":{"number":$amount}}}"""
    }

    private fun fold(page: JSONObject): Map<LocalDate, Long> {
        val into = LinkedHashMap<LocalDate, Long>()
        NotionRows.accumulate(page, "날짜", "금액", into)
        return into
    }

    @Test
    fun `같은 날 여러 건은 합산된다`() {
        val totals = fold(
            page(
                row("2026-08-27", "4500"),
                row("2026-08-27", "7900"),
                row("2026-08-26", "15000"),
            )
        )

        assertEquals(2, totals.size)
        assertEquals(12_400L, totals[LocalDate.of(2026, 8, 27)])
        assertEquals(15_000L, totals[LocalDate.of(2026, 8, 26)])
    }

    @Test
    fun `시각이 붙은 날짜도 읽는다`() {
        val totals = fold(page(row("2026-08-27T15:21:00.000+09:00", "4500")))
        assertEquals(4_500L, totals[LocalDate.of(2026, 8, 27)])
    }

    @Test
    fun `금액이 비어 있는 행은 건너뛴다`() {
        // 0 으로 세면 없는 지출이 생긴다
        val totals = fold(page(row("2026-08-27", "null"), row("2026-08-27", "4500")))
        assertEquals(4_500L, totals[LocalDate.of(2026, 8, 27)])
    }

    @Test
    fun `날짜가 비어 있는 행은 건너뛴다`() {
        val totals = fold(page(row(null, "4500")))
        assertEquals(0, totals.size)
    }

    @Test
    fun `속성 이름이 다르면 그 행만 건너뛴다`() {
        val odd = """{"properties":{"Date":{"date":{"start":"2026-08-27"}},"Amount":{"number":9999}}}"""
        val totals = fold(page(odd, row("2026-08-27", "4500")))

        // 한 행 때문에 한 달치가 통째로 날아가면 안 된다
        assertEquals(4_500L, totals[LocalDate.of(2026, 8, 27)])
    }

    @Test
    fun `날짜 형식이 깨져도 나머지는 살아남는다`() {
        val totals = fold(page(row("어제", "9999"), row("2026-08-27", "4500")))
        assertEquals(1, totals.size)
        assertEquals(4_500L, totals[LocalDate.of(2026, 8, 27)])
    }

    @Test
    fun `소수점 금액은 반올림한다`() {
        val totals = fold(page(row("2026-08-27", "4500.6")))
        assertEquals(4_501L, totals[LocalDate.of(2026, 8, 27)])
    }

    @Test
    fun `결과가 없으면 빈 맵이다`() {
        assertEquals(0, fold(page()).size)
        assertEquals(0, fold(JSONObject("{}")).size)
    }

    @Test
    fun `여러 페이지를 하나로 접는다`() {
        val into = LinkedHashMap<LocalDate, Long>()
        NotionRows.accumulate(page(row("2026-08-27", "4500")), "날짜", "금액", into)
        NotionRows.accumulate(page(row("2026-08-27", "7900")), "날짜", "금액", into)

        assertEquals(12_400L, into[LocalDate.of(2026, 8, 27)])
    }

    @Test
    fun `다음 페이지 커서를 읽는다`() {
        assertEquals("abc", NotionRows.nextCursor(page(hasMore = true, cursor = "abc")))
        assertNull(NotionRows.nextCursor(page(hasMore = false, cursor = "abc")))
        // has_more 가 true 인데 커서가 없으면 멈춘다 — 무한 루프를 만들지 않는다
        assertNull(NotionRows.nextCursor(page(hasMore = true, cursor = null)))
    }

    @Test
    fun `카테고리별로 금액을 합친다`() {
        val page = org.json.JSONObject(
            """
            {"results":[
              {"properties":{
                "금액":{"number":6600},
                "카테고리":{"select":{"name":"식비"}}
              }},
              {"properties":{
                "금액":{"number":3000},
                "카테고리":{"select":{"name":"식비"}}
              }},
              {"properties":{
                "금액":{"number":9900},
                "카테고리":{"select":{"name":"교육"}}
              }},
              {"properties":{
                "금액":{"number":400},
                "카테고리":{"select":null}
              }}
            ]}
            """.trimIndent(),
        )
        val into = LinkedHashMap<String, Long>()
        NotionRows.accumulateByCategory(page, "카테고리", "금액", into)

        assertEquals(9_600L, into["식비"])
        assertEquals(9_900L, into["교육"])
        // 카테고리 없는 행은 빈 문자열 키로 모인다
        assertEquals(400L, into[""])
    }

}
