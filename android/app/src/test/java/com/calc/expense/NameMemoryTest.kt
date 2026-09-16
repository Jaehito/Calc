package com.calc.expense

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「수집함에서 고친 이름」 기억. 여기가 흔들리면 같은 가게를 볼 때마다 같은 수정을
 * 되풀이하거나, 반대로 엉뚱한 결제가 남의 이름을 뒤집어쓴다.
 */
class NameMemoryTest {

    @Test
    fun `한 번 고치면 다음부터 그 이름으로 온다`() {
        val memory: Map<String, String> =
            NameMemories.put(emptyMap(), "우리카드 우아한형제들", "배달")

        assertEquals("배달", NameMemories.lookup(memory, "우리카드 우아한형제들"))
    }

    @Test
    fun `띄어쓰기와 대소문자는 무시한다`() {
        val memory: Map<String, String> = NameMemories.put(emptyMap(), "신한은행 HUAMAN CAR", "자동차 할부")

        assertEquals("자동차 할부", NameMemories.lookup(memory, "신한은행 huamancar"))
    }

    @Test
    fun `부분 일치는 쓰지 않는다`() {
        // 카테고리 기억과 갈리는 지점이다. 「신한은행」을 「월세」로 기억해 둔 상태에서 부분
        // 일치를 허용하면 신한은행에서 나간 모든 결제가 월세가 되고, 그 이름 그대로
        // 결제 기록에 쌓여 고정비 찾기까지 망가진다.
        val memory: Map<String, String> = NameMemories.put(emptyMap(), "신한은행", "월세")

        assertNull(NameMemories.lookup(memory, "신한은행 스타벅스"))
        assertEquals("월세", NameMemories.lookup(memory, "신한은행"))
    }

    @Test
    fun `고치지 않았으면 기억하지 않는다`() {
        // 같은 이름을 같은 이름으로 바꾸는 줄이 상한을 채우면 정작 필요한 기억이 밀려난다.
        assertTrue(NameMemories.put(emptyMap(), "스타벅스", "스타벅스").isEmpty())
        assertTrue(NameMemories.put(emptyMap(), "스타벅스", " 스타 벅스 ").isEmpty())
    }

    @Test
    fun `빈 이름은 기억하지 않는다`() {
        assertTrue(NameMemories.put(emptyMap(), "", "배달").isEmpty())
        assertTrue(NameMemories.put(emptyMap(), "우리카드 우아한형제들", "  ").isEmpty())
        assertNull(NameMemories.lookup(emptyMap(), ""))
    }

    @Test
    fun `다시 고치면 새 이름으로 바뀐다`() {
        var memory: Map<String, String> = NameMemories.put(emptyMap(), "우리카드 우아한형제들", "배달")
        memory = NameMemories.put(memory, "우리카드 우아한형제들", "배달의민족")

        assertEquals(1, memory.size)
        assertEquals("배달의민족", NameMemories.lookup(memory, "우리카드 우아한형제들"))
    }

    @Test
    fun `상한을 넘으면 오래 안 쓴 것부터 버린다`() {
        var memory: Map<String, String> = emptyMap()
        for (i in 0 until NameMemories.MAX_ENTRIES + 5) {
            memory = NameMemories.put(memory, "가맹점$i", "내이름$i")
        }

        assertEquals(NameMemories.MAX_ENTRIES, memory.size)
        assertNull(NameMemories.lookup(memory, "가맹점0"))
        assertEquals("내이름${NameMemories.MAX_ENTRIES + 4}", NameMemories.lookup(memory, "가맹점${NameMemories.MAX_ENTRIES + 4}"))
    }

    @Test
    fun `저장했다 읽으면 순서까지 그대로다`() {
        var memory: Map<String, String> = NameMemories.put(emptyMap(), "우리카드 우아한형제들", "배달")
        memory = NameMemories.put(memory, "신한은행 HUAMAN CAR", "자동차 할부")

        val back: Map<String, String> = NameMemoryCodec.decode(NameMemoryCodec.encode(memory))

        assertEquals(memory, back)
        assertEquals(memory.keys.toList(), back.keys.toList())
    }

    @Test
    fun `읽지 못한 글자는 빈 기억이 된다`() {
        assertTrue(NameMemoryCodec.decode("이건 JSON 이 아니다").isEmpty())
        assertTrue(NameMemoryCodec.decode(null).isEmpty())
    }
}
