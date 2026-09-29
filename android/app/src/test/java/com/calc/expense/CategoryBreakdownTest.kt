package com.calc.expense

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryBreakdownTest {

    @Test
    fun `큰 것부터 정렬하고 퍼센트를 매긴다`() {
        val slices = CategoryBreakdown.of(
            mapOf("식비" to 60_000L, "교통" to 30_000L, "구독" to 10_000L),
        )
        assertEquals(listOf("식비", "교통", "구독"), slices.map { it.name })
        assertEquals(listOf(60, 30, 10), slices.map { it.percent })
        assertEquals(100_000L, CategoryBreakdown.total(mapOf("식비" to 60_000L, "교통" to 30_000L, "구독" to 10_000L)))
    }

    @Test
    fun `이름이 빈 카테고리는 미분류로 묶는다`() {
        val slices = CategoryBreakdown.of(mapOf("" to 5_000L, "   " to 3_000L, "식비" to 2_000L))
        assertEquals("미분류", slices.first().name)
        assertEquals(8_000L, slices.first { it.name == "미분류" }.amount)
    }

    @Test
    fun `0 이하나 빈 입력은 빈 목록이다`() {
        assertTrue(CategoryBreakdown.of(emptyMap()).isEmpty())
        assertTrue(CategoryBreakdown.of(mapOf("식비" to 0L)).isEmpty())
    }

    @Test
    fun `큰 것 다섯 개만 두고 나머지는 한 줄로 묶는다`() {
        val slices = CategoryBreakdown.of(
            mapOf(
                "식비" to 469_620L, "" to 107_860L, "육아" to 89_000L, "생활" to 64_070L,
                "패션" to 47_160L, "건강" to 31_400L, "간식" to 27_690L, "카페" to 2_600L,
            ),
        )
        val rows = CategoryBreakdown.topWithRest(slices)

        assertEquals(listOf("식비", "미분류", "육아", "생활", "패션", null), rows.map { it.name })
        val rest = rows.last()
        assertTrue(rest.isRest)
        assertEquals(31_400L + 27_690L + 2_600L, rest.amount)
        assertEquals(listOf("건강", "간식", "카페"), rest.restNames)
        assertEquals(7, rest.percent)
    }

    @Test
    fun `묶을 것이 하나뿐이면 묶지 않는다`() {
        val slices = CategoryBreakdown.of(
            mapOf("a" to 60L, "b" to 50L, "c" to 40L, "d" to 30L, "e" to 20L, "f" to 10L),
        )
        val rows = CategoryBreakdown.topWithRest(slices)
        assertEquals(6, rows.size)
        assertTrue(rows.none { it.isRest })
    }
}
