package com.calc.expense

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusTextTest {

    private val snapshot = LedgerSnapshot(
        dailyRate = 30_000L,
        vault = 23_400L,
        todaySpent = 12_400L,
        monthSpent = 387_600L,
        monthlyBudget = 930_000L,
    )

    @Test
    fun `오늘 쓸 수 있는 돈이 접힌 줄에 들어간다`() {
        val lines = StatusText.recorded("커피", 4_500L, snapshot, "오후 3:21")

        // 대부분은 펼치지 않는다. 가장 중요한 숫자가 한 줄에 있어야 한다
        assertEquals("✓ 커피 4,500 · 오늘 쓸 수 있는 돈 41,000", lines.summary)
    }

    @Test
    fun `펼치면 숫자가 어떻게 나왔는지 보인다`() {
        val lines = StatusText.recorded("커피", 4_500L, snapshot, "오후 3:21")

        assertEquals(
            "✓ 커피 4,500원 기록됨 · 오후 3:21\n\n" +
                "오늘 쓸 수 있는 돈 41,000원\n" +
                "하루치 30,000 + 곳간 23,400 − 오늘 12,400",
            lines.detail,
        )
    }

    @Test
    fun `초과하면 음수 대신 초과액으로 말한다`() {
        val over = snapshot.copy(vault = 0L, todaySpent = 34_200L)
        val lines = StatusText.recorded("장보기", 28_700L, over, "오후 7:05")

        assertEquals("✓ 장보기 28,700 · 오늘 4,200 초과", lines.summary)
        assertTrue(lines.detail.contains("오늘 4,200원 초과"))
        // 벌이 아니라 조정이라는 걸 알려준다
        assertTrue(lines.detail.contains("남은 날에 나눠 조정됩니다"))
    }

    @Test
    fun `예산이 없으면 기록만 알리고 안내한다`() {
        val lines = StatusText.recorded("커피", 4_500L, null, "오후 3:21")

        assertEquals("✓ 커피 4,500원 기록됨 · 오후 3:21", lines.summary)
        assertTrue(lines.detail.contains("월 예산"))
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
            StatusText.recorded("커피", 4_500L, snapshot, "오후 3:21").detail,
            StatusText.recorded("장보기", 90_000L, snapshot.copy(vault = 0L, todaySpent = 90_000L), "오후 7:05").detail,
            StatusText.overview(snapshot),
        )

        for (text in texts) {
            for (banned in listOf("!", "잘", "실패", "밀린", "연속", "달성")) {
                assertTrue("금지 표현 '$banned' 이 들어 있다: $text", !text.contains(banned))
            }
        }
    }

    @Test
    fun `설정 화면 현황에 이번 달 진행이 보인다`() {
        assertEquals(
            "오늘 쓸 수 있는 돈  41,000원\n" +
                "하루치 30,000 + 곳간 23,400 − 오늘 12,400\n" +
                "이번 달 387,600 / 930,000",
            StatusText.overview(snapshot),
        )
    }

    @Test
    fun `예산이 없으면 현황 대신 안내가 나온다`() {
        assertEquals("월 예산을 정하면 오늘 쓸 수 있는 돈이 여기에 표시됩니다.", StatusText.overview(null))
    }
}
