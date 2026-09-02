package com.calc.expense

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoriesTest {

    @Test
    fun `기본 목록이 있다`() {
        assertTrue(Categories.DEFAULT.isNotEmpty())
        assertTrue(Categories.DEFAULT.contains("식비"))
    }

    @Test
    fun `쉼표와 줄바꿈으로 나눈다`() {
        assertEquals(listOf("식비", "카페", "교통"), Categories.parse("식비, 카페\n교통"))
    }

    @Test
    fun `앞뒤 공백과 빈 항목을 없앤다`() {
        assertEquals(listOf("식비", "교통"), Categories.parse("  식비 , , 교통 ,"))
    }

    @Test
    fun `중복은 첫 등장만 남긴다`() {
        assertEquals(listOf("식비", "카페"), Categories.parse("식비, 카페, 식비"))
    }

    @Test
    fun `이름은 상한 길이에서 자른다`() {
        val long: String = "가".repeat(30)
        assertEquals(Categories.MAX_NAME_LENGTH, Categories.parse(long)[0].length)
    }

    @Test
    fun `개수 상한을 넘지 않는다`() {
        val many: String = (1..50).joinToString(",") { "c$it" }
        assertEquals(Categories.MAX_COUNT, Categories.parse(many).size)
    }

    @Test
    fun `format 은 쉼표로 잇는다`() {
        assertEquals("식비, 카페", Categories.format(listOf("식비", "카페")))
    }

    @Test
    fun `format 과 parse 는 왕복한다`() {
        val list = listOf("식비", "카페", "교통")
        assertEquals(list, Categories.parse(Categories.format(list)))
    }
}
