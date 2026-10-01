package com.calc.expense

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TreeGrowthTest {

    private val today: LocalDate = LocalDate.of(2026, 10, 10)

    @Test fun `준 물로 단계를 정한다`() {
        assertEquals(0, TreeGrowth.stageOf(0))
        assertEquals(0, TreeGrowth.stageOf(2))
        assertEquals(1, TreeGrowth.stageOf(3))
        assertEquals(2, TreeGrowth.stageOf(10))
        assertEquals(6, TreeGrowth.stageOf(299))
        assertEquals(7, TreeGrowth.stageOf(300))
        assertEquals(7, TreeGrowth.stageOf(10_000))
    }

    @Test fun `다음 모습까지 남은 물과 막대`() {
        assertEquals(3, TreeGrowth.toNext(0))
        assertEquals(7, TreeGrowth.toNext(3))
        assertEquals(0.5f, TreeGrowth.progress(75), 0.0001f)
        assertNull(TreeGrowth.toNext(300))
        assertEquals(1f, TreeGrowth.progress(320), 0.0001f)
    }

    @Test fun `다 자란 뒤 30방울마다 꽃잎이 흩날린다`() {
        assertFalse(TreeGrowth.petalsAt(300))
        assertFalse(TreeGrowth.petalsAt(329))
        assertTrue(TreeGrowth.petalsAt(330))
        assertTrue(TreeGrowth.petalsAt(360))
        assertFalse(TreeGrowth.petalsAt(30))
    }

    private fun settle(
        state: TreeState,
        recordedDays: Set<LocalDate>,
        grades: Map<LocalDate, Grade> = emptyMap(),
    ): Pair<WaterGift, LocalDate> =
        WaterRules.settle(state, today, { it in recordedDays }, { grades[it] })

    private fun daysAgo(n: Long): LocalDate = today.minusDays(n)

    @Test fun `A 등급 이상인 날은 2, B 는 없다`() {
        val state = TreeState(plantedOn = daysAgo(30), settledThrough = daysAgo(4))
        val days = setOf(daysAgo(3), daysAgo(2), daysAgo(1))
        val (gift, through) = settle(state, days, mapOf(daysAgo(3) to Grade.S, daysAgo(2) to Grade.A, daysAgo(1) to Grade.B))
        assertEquals(WaterGift(goodDays = 2, quietDays = 0), gift)
        assertEquals(4, gift.total)
        assertEquals(daysAgo(1), through)
    }

    @Test fun `기록한 날 뒤로 이틀까지만 안 쓴 날로 친다`() {
        val state = TreeState(plantedOn = daysAgo(30), settledThrough = daysAgo(5))
        // 4일 전 적고, 3·2·1일 전은 비었다 → 3·2일 전만 안 쓴 날
        val (gift, _) = settle(state, setOf(daysAgo(5), daysAgo(4)))
        assertEquals(WaterGift(goodDays = 0, quietDays = 2), gift)
        assertEquals(6, gift.total)
    }

    @Test fun `한 번도 적지 않았으면 안 쓴 날이 아니다`() {
        val state = TreeState(plantedOn = daysAgo(30), settledThrough = daysAgo(4))
        val (gift, _) = settle(state, emptySet())
        assertTrue(gift.isEmpty)
    }

    @Test fun `이미 센 날과 심기 전 날은 다시 세지 않는다`() {
        val recorded = setOf(daysAgo(6), daysAgo(5), daysAgo(4), daysAgo(3), daysAgo(2), daysAgo(1))
        val grades = recorded.associateWith { Grade.S }
        val settled = TreeState(plantedOn = daysAgo(30), settledThrough = daysAgo(2))
        assertEquals(1, settle(settled, recorded, grades).first.goodDays)
        val planted = TreeState(plantedOn = daysAgo(2), settledThrough = null)
        assertEquals(2, settle(planted, recorded, grades).first.goodDays)
    }

    @Test fun `오래 만에 열어도 7일까지만 거슬러 본다`() {
        val recorded: Set<LocalDate> = (1L..20L).map { daysAgo(it) }.toSet()
        val grades = recorded.associateWith { Grade.A }
        val state = TreeState(plantedOn = daysAgo(60), settledThrough = daysAgo(40))
        assertEquals(7, settle(state, recorded, grades).first.goodDays)
    }

    @Test fun `심지 않았으면 보너스가 없다`() {
        val (gift, through) = settle(TreeState(), setOf(daysAgo(1)), mapOf(daysAgo(1) to Grade.S))
        assertTrue(gift.isEmpty)
        assertEquals(daysAgo(1), through)
    }

    @Test fun `계정 백업과 오가는 모양`() {
        val state = TreeState(water = 4, given = 57, plantedOn = daysAgo(20), settledThrough = daysAgo(1))
        assertEquals(state, TreeState.fromMap(state.toMap()))
        assertNull(TreeState.fromMap(null))
        assertNull(TreeState.fromMap(mapOf("water" to 3L)))
    }

    @Test fun `되찾은 나무와 합치면 준 물은 큰 쪽, 남은 물은 더한다`() {
        val local = TreeState(water = 2, given = 0, plantedOn = today, settledThrough = daysAgo(1))
        val remote = TreeState(water = 5, given = 120, plantedOn = daysAgo(90), settledThrough = daysAgo(3))
        val merged: TreeState = TreeState.merge(local, remote)
        assertEquals(7, merged.water)
        assertEquals(120, merged.given)
        assertEquals(daysAgo(90), merged.plantedOn)
        assertEquals(daysAgo(1), merged.settledThrough)
    }
}
