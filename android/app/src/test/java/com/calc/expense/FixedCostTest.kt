package com.calc.expense

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 월급 − 고정비 = 이번 달 챌린지 금액. 이 계산이 틀리면 예산·하루치·곳간·등급·챌린지가
 * 통째로 틀리므로 형식마다 못 박아 둔다.
 */
class FixedCostTest {

    private val sample = FixedCostPlan(
        monthlyIncome = 3_000_000L,
        items = listOf(
            FixedCostItem("월세", 600_000L),
            FixedCostItem("대출이자", 180_000L),
            FixedCostItem("보험", 95_000L),
            FixedCostItem("통신", 55_000L),
            FixedCostItem("구독", 13_500L),
        ),
    )

    @Test
    fun `월급에서 고정비를 뺀 값이 챌린지 금액이다`() {
        assertEquals(943_500L, sample.fixedTotal)
        assertEquals(2_056_500L, sample.recommended)
        assertTrue(sample.canRecommend)
        assertFalse(sample.isOverIncome)
    }

    @Test
    fun `고정비가 없으면 월급이 그대로 챌린지 금액이다`() {
        val plan = FixedCostPlan(monthlyIncome = 2_000_000L)
        assertEquals(0L, plan.fixedTotal)
        assertEquals(2_000_000L, plan.recommended)
    }

    @Test
    fun `고정비가 월급을 넘으면 0 이고 화면에 알린다`() {
        // 음수를 예산으로 넣으면 하루치가 음수가 되어 화면의 모든 숫자가 무너진다.
        val plan = sample.copy(monthlyIncome = 900_000L)

        assertEquals(0L, plan.recommended)
        assertTrue(plan.isOverIncome)
        assertFalse(plan.canRecommend)
    }

    @Test
    fun `고정비가 월급과 같아도 추천하지 않는다`() {
        val plan = FixedCostPlan(1_000_000L, listOf(FixedCostItem("월세", 1_000_000L)))

        assertEquals(0L, plan.recommended)
        assertTrue(plan.isOverIncome)
    }

    @Test
    fun `월급을 모르면 아무 말도 못 한다`() {
        val plan = FixedCostPlan(monthlyIncome = 0L, items = listOf(FixedCostItem("월세", 600_000L)))

        assertFalse(plan.canRecommend)
        assertEquals(0L, plan.recommended)
        // 항목이 있으니 «건너뛴 사람»은 아니다.
        assertFalse(plan.isEmpty)
    }

    @Test
    fun `아무것도 안 채우면 비어 있다`() {
        assertTrue(FixedCostPlan().isEmpty)
    }

    @Test
    fun `음수 금액은 합계를 늘리지 않는다`() {
        // 빼기가 더하기가 되면 안 된다.
        val plan = FixedCostPlan(1_000_000L, listOf(FixedCostItem("월세", 600_000L), FixedCostItem("환급", -100_000L)))

        assertEquals(600_000L, plan.fixedTotal)
        assertEquals(400_000L, plan.recommended)
    }

    // ── 목록 다듬기 ────────────────────────────────────────────────────────────

    @Test
    fun `이름이 비었거나 금액이 0 인 줄은 버린다`() {
        // 화면에서 «+ 항목 추가»만 누르고 안 채운 줄.
        val cleaned = FixedCosts.clean(
            listOf(
                FixedCostItem("월세", 600_000L),
                FixedCostItem("   ", 50_000L),
                FixedCostItem("보험", 0L),
            ),
        )

        assertEquals(listOf("월세"), cleaned.map { it.name })
    }

    @Test
    fun `이름 앞뒤 공백을 떼고 길이를 자른다`() {
        val cleaned = FixedCosts.clean(listOf(FixedCostItem("  아주아주아주아주아주긴이름입니다  ", 1_000L)))

        assertEquals(FixedCosts.MAX_NAME_LENGTH, cleaned[0].name.length)
    }

    @Test
    fun `상한을 넘으면 앞에서부터만 남긴다`() {
        val many = (1..FixedCosts.MAX_ITEMS + 5).map { FixedCostItem("항목$it", 1_000L) }

        assertEquals(FixedCosts.MAX_ITEMS, FixedCosts.clean(many).size)
        assertEquals("항목1", FixedCosts.clean(many).first().name)
    }

    @Test
    fun `같은 이름을 두 번 넣어도 막지 않는다`() {
        // 보험이 둘일 수 있고, 그게 실수인지 앱은 알 수 없다.
        val cleaned = FixedCosts.clean(listOf(FixedCostItem("보험", 50_000L), FixedCostItem("보험", 30_000L)))

        assertEquals(2, cleaned.size)
        assertEquals(80_000L, FixedCostPlan(items = cleaned).fixedTotal)
    }

    @Test
    fun `미리 깔아 두는 줄은 금액이 비어 있다`() {
        // 짐작한 금액이 예산에 들어가면 그 뒤 모든 숫자가 그만큼 틀린다.
        val rows: List<FixedCostItem> = FixedCosts.suggestedRows()

        assertEquals(FixedCosts.SUGGESTED, rows.map { it.name })
        assertTrue(rows.all { it.amount == 0L })
        // 그래서 그대로 저장하면 한 줄도 남지 않는다.
        assertTrue(FixedCosts.clean(rows).isEmpty())
    }

    // ── 직렬화 ─────────────────────────────────────────────────────────────────

    @Test
    fun `JSON 왕복에도 값이 그대로다`() {
        assertEquals(sample, FixedCostCodec.decode(FixedCostCodec.encode(sample)))
    }

    @Test
    fun `빈 값이나 깨진 JSON 은 빈 계획이다`() {
        assertEquals(FixedCostPlan(), FixedCostCodec.decode(null))
        assertEquals(FixedCostPlan(), FixedCostCodec.decode(""))
        assertEquals(FixedCostPlan(), FixedCostCodec.decode("{깨진"))
    }

    @Test
    fun `해석할 수 없는 항목만 건너뛴다`() {
        val raw = """{"income":2000000,"items":[{"name":"월세","amount":600000},{"amount":1000},{"name":"보험","amount":0}]}"""

        val plan: FixedCostPlan = FixedCostCodec.decode(raw)

        assertEquals(2_000_000L, plan.monthlyIncome)
        assertEquals(listOf("월세"), plan.items.map { it.name })
    }

    @Test
    fun `음수 월급은 0 으로 읽는다`() {
        assertEquals(0L, FixedCostCodec.decode("""{"income":-500}""").monthlyIncome)
    }
}
