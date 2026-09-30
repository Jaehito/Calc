package com.calc.expense

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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

    private fun hex(color: Int): String = "#%06X".format(color and 0xFFFFFF)

    /** 그림 파일에 든 선 색(한 가지뿐이어야 한다). */
    private fun strokeColors(name: String): Set<String> =
        Regex("""android:strokeColor="(#[0-9A-Fa-f]{6})"""")
            .findAll(File(drawables, "$name.xml").readText())
            .map { it.groupValues[1].uppercase() }
            .toSet()

    @Test fun `그림 선 색이 코드의 자리 색 규칙과 같다`() {
        val places: Map<String, Int> = categoryFiles.entries.associate { (stored, file) -> file to IconHues.category(stored) } + mapOf(
            "dogam_ic_coin" to IconHues.SAVED_DAY, "dogam_ic_shield" to IconHues.KEEP_RUN, "dogam_ic_moon" to IconHues.NO_SPEND,
            "dogam_ic_week" to IconHues.CHEAP_WEEK, "dogam_ic_pen" to IconHues.RECORD_RUN, "ic_report" to IconHues.REPORT,
        )
        for ((file, hue) in places) {
            assertEquals(file, setOf(hex(IconHues.ink(hue))), strokeColors(file))
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

    @Test fun `바탕은 옅고 선은 짙다`() {
        val hue: Int = IconHues.category("식비")
        assertNotEquals(hue, IconHues.back(hue))
        assertTrue(brightness(IconHues.back(hue)) > brightness(hue))
        assertTrue(brightness(IconHues.ink(hue)) < brightness(hue))
    }

    private fun brightness(c: Int): Int = ((c shr 16) and 0xFF) + ((c shr 8) and 0xFF) + (c and 0xFF)
}
