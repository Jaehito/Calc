package com.calc.expense

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class StatusTextTest {

    /** 월급날 25일 · 오늘 8/27 → 목표일 9/24, 남은 29일 중 22일 가정. */
    private val personal = LedgerSnapshot(
        purse = Purse.PERSONAL,
        label = "개인",
        dailyRate = 30_000L,
        vault = 23_400L,
        todaySpent = 12_400L,
        cycleSpent = 387_600L,
        monthlyBudget = 930_000L,
        targetDay = LocalDate.of(2026, 9, 24),
        daysLeft = 22,
    )

    @Test
    fun `오늘 쓸 수 있는 돈이 접힌 줄에 들어간다`() {
        val lines = StatusText.recorded("커피", 4_500L, personal, "오후 3:21")

        // 대부분은 펼치지 않는다. 가장 중요한 숫자가 한 줄에 있어야 한다
        assertEquals("✓ 커피 4,500 · 오늘 쓸 수 있는 돈 41,000", lines.summary)
    }

    @Test
    fun `목표일까지 쓸 수 있는 돈과 남은 날을 함께 말한다`() {
        // 930,000 − 387,600 = 542,400 · 542,400 / 22 = 24,654
        assertEquals(
            "9월 24일까지 542,400원 · 남은 22일 (하루 24,654)",
            StatusText.untilTarget(personal),
        )
    }

    @Test
    fun `주기 예산을 이미 넘겼으면 초과액으로 말한다`() {
        val over = personal.copy(cycleSpent = 962_000L)

        assertEquals("9월 24일까지 32,000원 초과 · 남은 22일", StatusText.untilTarget(over))
    }

    @Test
    fun `마지막 날이면 남은 날이 1이다`() {
        val lastDay = personal.copy(daysLeft = 1, cycleSpent = 900_000L)

        assertEquals("9월 24일까지 30,000원 · 남은 1일 (하루 30,000)", StatusText.untilTarget(lastDay))
    }

    @Test
    fun `곳간이 하나뿐이면 이름을 붙이지 않는다`() {
        val lines = StatusText.recorded("커피", 4_500L, personal, "오후 3:21", showPurse = false)

        assertTrue(!lines.summary.contains("개인"))
        assertTrue(!lines.detail.contains("개인"))
    }

    @Test
    fun `곳간이 둘이면 어느 곳간인지 붙인다`() {
        val lines = StatusText.recorded("커피", 4_500L, personal, "오후 3:21", showPurse = true)

        assertEquals("✓ 커피 4,500 · 개인 오늘 41,000", lines.summary)
        assertTrue(lines.detail.contains("개인 오늘 쓸 수 있는 돈 41,000원"))
    }

    @Test
    fun `펼치면 숫자가 어떻게 나왔는지 보인다`() {
        val lines = StatusText.recorded("커피", 4_500L, personal, "오후 3:21")

        assertEquals(
            "✓ 커피 4,500원 기록됨 · 오후 3:21\n\n" +
                "오늘 쓸 수 있는 돈 41,000원\n" +
                "하루치 30,000 + 곳간 23,400 − 오늘 12,400\n\n" +
                "9월 24일까지 542,400원 · 남은 22일 (하루 24,654)",
            lines.detail,
        )
    }

    @Test
    fun `초과하면 음수 대신 초과액으로 말한다`() {
        val over = personal.copy(purse = Purse.SHARED, label = "공용", vault = 0L, todaySpent = 34_200L)
        val lines = StatusText.recorded("장보기", 28_700L, over, "오후 7:05", showPurse = true)

        assertEquals("✓ 장보기 28,700 · 공용 오늘 4,200 초과", lines.summary)
        assertTrue(lines.detail.contains("공용 오늘 4,200원 초과"))
        // 벌이 아니라 조정이라는 걸 알려준다
        assertTrue(lines.detail.contains("남은 날에 나눠 조정됩니다"))
        // 초과한 날에도 목표일까지의 여유는 그대로 보여준다
        assertTrue(lines.detail.contains("9월 24일까지"))
    }

    @Test
    fun `예산이 없으면 기록만 알리고 안내한다`() {
        val lines = StatusText.recorded("커피", 4_500L, null, "오후 3:21", showPurse = true)

        assertEquals("✓ 커피 4,500원 기록됨 · 오후 3:21", lines.summary)
        assertTrue(lines.detail.contains("예산"))
    }

    @Test
    fun `연속으로 적으면 몇 건째인지 붙는다`() {
        // 한 건만 적었을 때는 셀 것이 없으니 개수를 붙이지 않는다
        assertEquals("✓ 양파 5,500", StatusText.entered("양파", 5_500L, 1))
        assertEquals("✓ 쓰레기봉투 400 · 2건째", StatusText.entered("쓰레기봉투", 400L, 2))
        assertEquals("✓ 장난감 1,200 · 3건째", StatusText.entered("장난감", 1_200L, 3))
    }

    @Test
    fun `실패는 접힌 줄과 펼친 본문이 같다`() {
        val lines = StatusText.failed("금액을 찾을 수 없습니다", "오후 3:21")

        assertEquals("✗ 금액을 찾을 수 없습니다 · 오후 3:21", lines.summary)
        assertEquals(lines.summary, lines.detail)
    }

    @Test
    fun `채점하는 표현을 쓰지 않는다`() {
        val texts = listOf(
            StatusText.recorded("커피", 4_500L, personal, "오후 3:21").detail,
            StatusText.recorded(
                "장보기", 90_000L,
                personal.copy(vault = 0L, todaySpent = 90_000L), "오후 7:05",
            ).detail,
            StatusText.overview(listOf(personal)),
            StatusText.entered("양파", 5_500L, 2),
        )

        for (text in texts) {
            for (banned in listOf("!", "잘", "실패", "밀린", "연속", "달성")) {
                assertTrue("금지 표현 '$banned' 이 들어 있다: $text", !text.contains(banned))
            }
        }
    }

    @Test
    fun `현황에 곳간마다 한 덩어리씩 나온다`() {
        val shared = LedgerSnapshot(
            purse = Purse.SHARED,
            label = "공용",
            dailyRate = 50_000L,
            vault = 64_000L,
            todaySpent = 34_200L,
            cycleSpent = 1_120_000L,
            monthlyBudget = 1_550_000L,
            targetDay = LocalDate.of(2026, 9, 24),
            daysLeft = 22,
        )

        assertEquals(
            "개인 · 오늘 쓸 수 있는 돈  41,000원\n" +
                "하루치 30,000 + 곳간 23,400 − 오늘 12,400\n" +
                "9월 24일까지 542,400원 · 남은 22일 (하루 24,654)\n\n" +
                "공용 · 오늘 쓸 수 있는 돈  79,800원\n" +
                "하루치 50,000 + 곳간 64,000 − 오늘 34,200\n" +
                "9월 24일까지 430,000원 · 남은 22일 (하루 19,545)",
            StatusText.overview(listOf(personal, shared)),
        )
    }

    @Test
    fun `이름을 바꾸면 알림과 현황에 그 이름이 나온다`() {
        val renamed = personal.copy(label = "재호 용돈")

        val lines = StatusText.recorded("커피", 4_500L, renamed, "오후 3:21", showPurse = true)
        assertEquals("✓ 커피 4,500 · 재호 용돈 오늘 41,000", lines.summary)
        assertTrue(StatusText.overview(listOf(renamed)).startsWith("재호 용돈 · 오늘 쓸 수 있는 돈"))
    }

    @Test
    fun `곳간이 없으면 현황 대신 안내가 나온다`() {
        assertEquals(
            "DB를 연결하고 예산을 정하면 오늘 쓸 수 있는 돈이 여기에 표시됩니다.",
            StatusText.overview(emptyList()),
        )
    }
}
