package com.calc.expense

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChallengeStandingsTest {

    private fun byUid(standings: List<Standing>, uid: String): Standing =
        standings.first { it.uid == uid }

    @Test
    fun `예산 대비 덜 쓴 사람이 1위`() {
        val standings = ChallengeStandings.rank(
            listOf(
                MemberWeek("a", "재호", spent = 288_000L, budget = 350_000L), // 82%
                MemberWeek("b", "지영", spent = 201_000L, budget = 350_000L), // 57%
            ),
        )

        assertEquals(1, byUid(standings, "b").rank)
        assertTrue(byUid(standings, "b").isLeader)
        assertEquals(2, byUid(standings, "a").rank)
        assertFalse(byUid(standings, "a").isLeader)
    }

    @Test
    fun `총액이 더 커도 예산이 크면 앞설 수 있다`() {
        // 30만 쓴 사람(예산 100만, 30%)이 10만 쓴 사람(예산 20만, 50%)보다 앞선다
        val standings = ChallengeStandings.rank(
            listOf(
                MemberWeek("rich", "소득많은친구", spent = 300_000L, budget = 1_000_000L),
                MemberWeek("thin", "빠듯한친구", spent = 100_000L, budget = 200_000L),
            ),
        )

        assertEquals(1, byUid(standings, "rich").rank)
        assertEquals(2, byUid(standings, "thin").rank)
    }

    @Test
    fun `사용률이 같으면 덜 쓴 금액이 앞선다`() {
        // 둘 다 50% 지만 절대액이 작은 쪽이 앞
        val standings = ChallengeStandings.rank(
            listOf(
                MemberWeek("big", "큰예산", spent = 250_000L, budget = 500_000L),
                MemberWeek("small", "작은예산", spent = 100_000L, budget = 200_000L),
            ),
        )

        assertEquals(1, byUid(standings, "small").rank)
        assertEquals(2, byUid(standings, "big").rank)
    }

    @Test
    fun `완전히 같으면 동률 등수`() {
        val standings = ChallengeStandings.rank(
            listOf(
                MemberWeek("a", "가", spent = 100_000L, budget = 200_000L),
                MemberWeek("b", "나", spent = 100_000L, budget = 200_000L),
                MemberWeek("c", "다", spent = 180_000L, budget = 200_000L),
            ),
        )

        // 동률 둘이 공동 1위, 그 다음은 3위(경쟁식 1,1,3)
        assertEquals(1, byUid(standings, "a").rank)
        assertEquals(1, byUid(standings, "b").rank)
        assertTrue(byUid(standings, "a").isLeader)
        assertTrue(byUid(standings, "b").isLeader)
        assertEquals(3, byUid(standings, "c").rank)
    }

    @Test
    fun `예산이 없는 사람은 맨 뒤로 간다`() {
        val standings = ChallengeStandings.rank(
            listOf(
                MemberWeek("none", "예산없음", spent = 10_000L, budget = 0L),
                MemberWeek("has", "예산있음", spent = 340_000L, budget = 350_000L), // 97%
            ),
        )

        // 97% 를 썼어도 예산을 정한 쪽이 앞, 예산 없는 쪽이 뒤
        assertEquals(1, byUid(standings, "has").rank)
        assertEquals(2, byUid(standings, "none").rank)
        assertFalse(byUid(standings, "none").hasBudget)
        assertEquals(0, byUid(standings, "none").percent)
    }

    @Test
    fun `반올림해 같아 보여도 실제 차이로 등수를 가른다`() {
        // 100,000/300,000 = 33.33% 와 100,400/300,000 = 33.47% → 둘 다 반올림 33% 지만
        // 실제로는 다르므로 등수는 갈려야 한다(반올림 동률로 뭉개지 않는다)
        val standings = ChallengeStandings.rank(
            listOf(
                MemberWeek("a", "가", spent = 100_000L, budget = 300_000L),
                MemberWeek("b", "나", spent = 100_400L, budget = 300_000L),
            ),
        )

        assertEquals(33, byUid(standings, "a").percent)
        assertEquals(33, byUid(standings, "b").percent)
        assertEquals(1, byUid(standings, "a").rank)
        assertEquals(2, byUid(standings, "b").rank)
    }

    @Test
    fun `혼자면 1위`() {
        val standings = ChallengeStandings.rank(
            listOf(MemberWeek("solo", "나", spent = 50_000L, budget = 200_000L)),
        )

        assertEquals(1, standings.size)
        assertEquals(1, standings[0].rank)
        assertTrue(standings[0].isLeader)
        assertEquals(25, standings[0].percent)
    }

    @Test
    fun `아무도 없으면 빈 목록`() {
        assertTrue(ChallengeStandings.rank(emptyList()).isEmpty())
    }
}
