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
}
