package com.calc.expense

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class IconHuesTest {

    private val drawables = File("src/main/res/drawable")

    private val categoryFiles = mapOf(
        CategoryBreakdown.UNCATEGORIZED to "ic_cat_uncategorized", "식비" to "ic_cat_food", "카페" to "ic_cat_cafe",
        "간식" to "ic_cat_snack", "마트" to "ic_cat_mart", "교통" to "ic_cat_transport", "생활" to "ic_cat_household",
        "건강" to "ic_cat_health", "육아" to "ic_cat_kids", "문화" to "ic_cat_culture", "패션" to "ic_cat_fashion",
        "주거" to "ic_cat_housing", "기타" to "ic_cat_other",
    )


    /** 그림 파일에 든 짙은 선(#15352B)의 굵기들. */
    private fun inkWidths(name: String): Set<String> =
        Regex("""android:strokeColor="#15352B" android:strokeWidth="([0-9.]+)"""")
            .findAll(File(drawables, "$name.xml").readText())
            .map { it.groupValues[1] }
            .toSet()

    @Test fun `작은 칸 그림은 모두 짙은 선 1_3 한 가지 굵기다`() {
        val files: List<String> = categoryFiles.values.toList() +
            listOf("dogam_ic_coin", "dogam_ic_shield", "dogam_ic_moon", "dogam_ic_week", "dogam_ic_pen", "ic_report")
        for (file in files) {
            assertEquals(file, setOf("1.3"), inkWidths(file))
        }
    }

    @Test fun `홈 세 줄은 서로 다른 색이다`() {
        assertEquals(3, setOf(IconHues.DAILY, IconHues.GOTGAN, IconHues.SPENT).size)
    }

    @Test fun `직접 만든 카테고리는 기본 카테고리와 겹치지 않는 색을 늘 같게 받는다`() {
        val base: Set<Int> = categoryFiles.keys.map { IconHues.category(it) }.toSet()
        for (name in listOf("술", "반려동물", "경조사", "Gym")) {
            val hue: Int = IconHues.category(name)
            assertFalse(name, hue in base)
            assertEquals(hue, IconHues.category(name))
        }
    }

    @Test fun `첫 글자 색은 자리 색보다 짙다`() {
        val hue: Int = IconHues.category("식비")
        assertTrue(brightness(IconHues.ink(hue)) < brightness(hue))
    }

    private fun brightness(c: Int): Int = ((c shr 16) and 0xFF) + ((c shr 8) and 0xFF) + (c and 0xFF)
}
