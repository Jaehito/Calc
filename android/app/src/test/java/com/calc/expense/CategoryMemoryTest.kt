package com.calc.expense

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「내가 한 번 정한 것」을 기억하는 규칙. 여기가 흔들리면 기록 화면이 매번 «없음»으로
 * 떨어지거나, 반대로 엉뚱한 카테고리를 켜 놓고 사용자가 매번 되돌리게 된다.
 */
class CategoryMemoryTest {

    private val chips: List<String> = listOf("없음", "카페", "식비", "마트", "교통", "문화")

    @Test
    fun `한 번 정해 주면 다음부터 기억한다`() {
        // 낱말 규칙이 모르는 이름이다. 규칙을 늘리는 대신 사용자가 정한 것을 기억한다.
        val memory: Map<String, String> = CategoryMemories.put(emptyMap(), "우아한형제들", "식비")

        assertEquals("식비", CategoryMemories.lookup(memory, "우아한형제들", chips))
    }

    @Test
    fun `띄어쓰기와 대소문자는 무시한다`() {
        val memory: Map<String, String> = CategoryMemories.put(emptyMap(), "GS25", "마트")

        assertEquals("마트", CategoryMemories.lookup(memory, "gs 25", chips))
    }

    @Test
    fun `가게 이름이 길어져도 알아본다`() {
        // 「스타벅스」로 정해 뒀으면 「스타벅스 강남점」도 같은 곳이다.
        val memory: Map<String, String> = CategoryMemories.put(emptyMap(), "스타벅스", "카페")

        assertEquals("카페", CategoryMemories.lookup(memory, "스타벅스 강남점", chips))
    }

    @Test
    fun `다시 정하면 새 값으로 바뀐다`() {
        var memory: Map<String, String> = CategoryMemories.put(emptyMap(), "스타벅스", "카페")
        memory = CategoryMemories.put(memory, "스타벅스", "식비")

        assertEquals(1, memory.size)
        assertEquals("식비", CategoryMemories.lookup(memory, "스타벅스", chips))
    }

    @Test
    fun `없음은 기억하지 않는다`() {
        // «없음»은 사용자가 고른 것이 아니라 아직 안 고른 것이다.
        assertTrue(CategoryMemories.put(emptyMap(), "무언가", "").isEmpty())
        assertTrue(CategoryMemories.put(emptyMap(), "", "식비").isEmpty())
    }

    @Test
    fun `칩에서 사라진 카테고리는 켜지 않는다`() {
        // 사용자가 설정에서 칩 이름을 바꿨다. 없는 칩을 켤 수는 없다.
        val memory: Map<String, String> = CategoryMemories.put(emptyMap(), "병원", "건강")

        assertNull(CategoryMemories.lookup(memory, "병원", chips))
    }

    @Test
    fun `모르는 이름에는 아무 말도 하지 않는다`() {
        val memory: Map<String, String> = CategoryMemories.put(emptyMap(), "스타벅스", "카페")

        assertNull(CategoryMemories.lookup(memory, "한글문구사", chips))
        assertNull(CategoryMemories.lookup(memory, "", chips))
    }

    @Test
    fun `한 글자짜리 기억은 아무 데나 걸리지 않는다`() {
        // 「차」를 기억해 뒀다고 「자동차보험」·「주차」까지 그 카테고리가 되면 안 된다.
        val memory: Map<String, String> = CategoryMemories.put(emptyMap(), "차", "카페")

        assertNull(CategoryMemories.lookup(memory, "주차장", chips))
        // 정확히 같은 이름이면 그대로 쓴다.
        assertEquals("카페", CategoryMemories.lookup(memory, "차", chips))
    }

    @Test
    fun `부분 일치는 최근에 정한 것부터 본다`() {
        // 「메가커피」를 카페로 쓰다가 「메가커피 성수」를 식비로 바꿔 적었다면 최근 것이 이긴다.
        var memory: Map<String, String> = CategoryMemories.put(emptyMap(), "메가커피", "카페")
        memory = CategoryMemories.put(memory, "메가커피성수", "식비")

        assertEquals("식비", CategoryMemories.lookup(memory, "메가커피 성수점", chips))
    }

    @Test
    fun `상한을 넘으면 오래 안 쓴 것부터 버린다`() {
        var memory: Map<String, String> = emptyMap()
        for (i in 0 until CategoryMemories.MAX_ENTRIES + 5) {
            memory = CategoryMemories.put(memory, "가게$i", "식비")
        }

        assertEquals(CategoryMemories.MAX_ENTRIES, memory.size)
        assertTrue("가게0" !in memory.keys)
        assertTrue("가게${CategoryMemories.MAX_ENTRIES + 4}" in memory.keys)
    }

    @Test
    fun `다시 쓰면 뒤로 가서 오래 남는다`() {
        var memory: Map<String, String> = CategoryMemories.put(emptyMap(), "오래된곳", "카페")
        for (i in 0 until CategoryMemories.MAX_ENTRIES - 1) {
            memory = CategoryMemories.put(memory, "가게$i", "식비")
        }
        // 상한 직전에 한 번 더 쓴다 — 이제 가장 최근 것이 된다.
        memory = CategoryMemories.put(memory, "오래된곳", "카페")
        for (i in 0 until 5) {
            memory = CategoryMemories.put(memory, "새가게$i", "식비")
        }

        assertEquals("카페", CategoryMemories.lookup(memory, "오래된곳", chips))
    }

    @Test
    fun `저장했다 읽으면 순서까지 그대로다`() {
        var memory: Map<String, String> = CategoryMemories.put(emptyMap(), "스타벅스", "카페")
        memory = CategoryMemories.put(memory, "이마트", "마트")
        memory = CategoryMemories.put(memory, "지하철", "교통")

        val back: Map<String, String> = CategoryMemoryCodec.decode(CategoryMemoryCodec.encode(memory))

        assertEquals(memory, back)
        assertEquals(listOf("스타벅스", "이마트", "지하철"), back.keys.toList())
    }

    @Test
    fun `읽지 못한 글자는 빈 기억이 된다`() {
        assertTrue(CategoryMemoryCodec.decode("이건 JSON 이 아니다").isEmpty())
        assertTrue(CategoryMemoryCodec.decode(null).isEmpty())
    }
}
