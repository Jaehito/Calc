package com.calc.expense

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 카테고리 하나를 이름으로 묶는 규칙. 이 화면이 이 앱의 «하위 카테고리»라,
 * 여기서 잘못 묶으면 사용자가 스무 번 간 편의점을 스무 번 고치게 된다.
 */
class CategoryDetailTest {

    private fun row(
        id: String,
        name: String,
        amount: Long,
        category: String,
        date: String = "2026-09-05",
        purse: Purse = Purse.PERSONAL,
    ) = PursedRow(
        purse = purse,
        row = ExpenseRow(id = id, name = name, amount = amount, date = LocalDate.parse(date), category = category),
    )

    @Test
    fun `같은 이름을 한 덩어리로 묶는다`() {
        val detail: CategoryDetail = CategoryDetails.of(
            listOf(
                row("1", "우아한형제들", 16_900L, "식비"),
                row("2", "우아한형제들", 23_000L, "식비"),
                row("3", "김밥천국", 9_000L, "식비"),
            ),
            "식비",
        )

        assertEquals(2, detail.groups.size)
        assertEquals("우아한형제들", detail.groups[0].name)
        assertEquals(39_900L, detail.groups[0].total)
        assertEquals(2, detail.groups[0].count)
    }

    @Test
    fun `띄어쓰기가 달라도 같은 덩어리다`() {
        // 고정비 찾기와 같은 잣대를 쓴다 — 화면에서 한 줄로 보이는 것이
        // 고정비 후보로도 한 줄로 잡혀야 한다.
        val detail: CategoryDetail = CategoryDetails.of(
            listOf(
                row("1", "우리카드 우아한형제들", 16_900L, "식비"),
                row("2", "우리카드우아한형제들", 23_000L, "식비"),
            ),
            "식비",
        )

        assertEquals(1, detail.groups.size)
        assertEquals(39_900L, detail.groups[0].total)
    }

    @Test
    fun `가장 최근에 적은 이름으로 보여준다`() {
        // 이름을 고쳤으면 새 이름이 보여야 한다.
        val detail: CategoryDetail = CategoryDetails.of(
            listOf(
                row("1", "우리카드 우아한형제들", 16_900L, "식비", date = "2026-09-01"),
                row("2", "배달", 23_000L, "식비", date = "2026-09-20"),
            ),
            "식비",
        )

        assertEquals("배달", detail.groups[0].name)
    }

    @Test
    fun `다른 카테고리는 섞지 않는다`() {
        val detail: CategoryDetail = CategoryDetails.of(
            listOf(
                row("1", "우아한형제들", 16_900L, "식비"),
                row("2", "스타벅스", 5_500L, "카페"),
            ),
            "식비",
        )

        assertEquals(1, detail.groups.size)
        assertEquals(16_900L, detail.total)
    }

    @Test
    fun `미분류는 빈 카테고리로 찾는다`() {
        // 저장된 값은 빈 문자열이고 「미분류」는 보여줄 때의 이름이다.
        val detail: CategoryDetail = CategoryDetails.of(
            listOf(
                row("1", "쿠팡", 34_000L, ""),
                row("2", "올리브영", 27_000L, ""),
                row("3", "스타벅스", 5_500L, "카페"),
            ),
            "",
        )

        assertEquals(2, detail.groups.size)
        assertEquals(61_000L, detail.total)
        assertEquals(CategoryBreakdown.UNCATEGORIZED, detail.label)
    }

    @Test
    fun `큰 금액부터 보여준다`() {
        val detail: CategoryDetail = CategoryDetails.of(
            listOf(
                row("1", "김밥천국", 9_000L, "식비"),
                row("2", "우아한형제들", 39_900L, "식비"),
                row("3", "서브웨이", 18_000L, "식비"),
            ),
            "식비",
        )

        assertEquals(listOf("우아한형제들", "서브웨이", "김밥천국"), detail.groups.map { it.name })
    }

    @Test
    fun `곳간이 달라도 한 덩어리로 묶고 곳간은 줄마다 기억한다`() {
        // 옮겨 쓸 때 어느 곳간의 문서인지 알아야 한다.
        val detail: CategoryDetail = CategoryDetails.of(
            listOf(
                row("1", "이마트", 40_000L, "마트", purse = Purse.PERSONAL),
                row("2", "이마트", 60_000L, "마트", purse = Purse.SHARED),
            ),
            "마트",
        )

        assertEquals(1, detail.groups.size)
        assertEquals(100_000L, detail.groups[0].total)
        assertEquals(setOf(Purse.PERSONAL, Purse.SHARED), detail.groups[0].rows.map { it.purse }.toSet())
    }

    @Test
    fun `이름이 비어도 덩어리를 잃지 않는다`() {
        // 파싱이 이름을 못 읽은 줄들도 한 덩어리로 모아 한 번에 분류할 수 있어야 한다.
        val detail: CategoryDetail = CategoryDetails.of(
            listOf(row("1", "", 3_000L, ""), row("2", "  ", 4_000L, "")),
            "",
        )

        assertEquals(1, detail.groups.size)
        assertEquals("이름 없음", detail.groups[0].name)
        assertEquals(7_000L, detail.total)
    }

    @Test
    fun `빈 목록이면 비어 있다고 말한다`() {
        val detail: CategoryDetail = CategoryDetails.of(emptyList(), "식비")

        assertTrue(detail.isEmpty)
        assertEquals(0L, detail.total)
        assertEquals(0, detail.count)
    }
}
