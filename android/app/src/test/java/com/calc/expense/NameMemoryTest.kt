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
        // 카테고리 기억과 갈리는 지점이다. 「신한은행 관리비」를 「월세」로 기억해 둔 상태에서
        // 부분 일치를 허용하면 그 이름으로 시작하는 결제가 전부 월세가 되고, 그 이름 그대로
        // 결제 기록에 쌓여 고정비 찾기까지 망가진다.
        val memory: Map<String, String> = NameMemories.put(emptyMap(), "신한은행 관리비", "월세")

        assertNull(NameMemories.lookup(memory, "신한은행 관리비 스타벅스"))
        assertEquals("월세", NameMemories.lookup(memory, "신한은행 관리비"))
    }

    @Test
    fun `카드사 이름만 읽힌 결제는 기억하지 않는다`() {
        // 가맹점을 못 읽은 결제는 전부 「우리카드」가 된다. 하나를 고쳐 기억하면
        // 그 카드의 못 읽은 결제가 전부 그 이름으로 뜬다.
        assertTrue(NameMemories.put(emptyMap(), "우리카드", "선호 칫솔").isEmpty())
        assertTrue(NameMemories.put(emptyMap(), "신한은행", "월세").isEmpty())
        assertTrue(NameMemories.put(emptyMap(), "KB 국민", "점심").isEmpty())
    }

    @Test
    fun `쇼핑몰은 기억하지 않는다`() {
        // 「우리카드 쿠팡」을 한 번 「선호 칫솔」로 고쳤더니 쿠팡 결제가 전부 「선호 칫솔」이 됐다.
        assertTrue(NameMemories.put(emptyMap(), "우리카드 쿠팡", "선호 칫솔").isEmpty())
        assertTrue(NameMemories.put(emptyMap(), "신한카드 쿠페이", "기저귀").isEmpty())
        assertTrue(NameMemories.put(emptyMap(), "네이버페이 스마트스토어", "양말").isEmpty())
        assertTrue(NameMemories.put(emptyMap(), "현대카드 11번가", "충전기").isEmpty())
    }

    @Test
    fun `이미 쌓인 쇼핑몰 기억은 찾아도 안 나오고 걷어 낸다`() {
        // 옛 버전이 저장해 둔 줄. 읽는 쪽에서도 막아야 업데이트 즉시 멈춘다.
        val old: Map<String, String> = mapOf("우리카드쿠팡" to "선호 칫솔", "우리카드우아한형제들" to "배달")

        assertNull(NameMemories.lookup(old, "우리카드 쿠팡"))
        assertEquals(mapOf("우리카드우아한형제들" to "배달"), NameMemories.sanitize(old))
    }

    @Test
    fun `바뀐 이름이 열쇠가 된 줄은 한 번 걷어 낸다`() {
        // 옛 버전은 「선호 칫솔」로 뜬 걸 「기저귀」로 고치면 「선호칫솔 → 기저귀」를 적었다.
        val old: Map<String, String> = mapOf(
            "우리카드이마트" to "선호 칫솔",
            "선호칫솔" to "기저귀",
            "우리카드우아한형제들" to "배달",
        )

        val cleaned: Map<String, String> = NameMemories.sanitize(old, dropChains = true)

        assertEquals(setOf("우리카드이마트", "우리카드우아한형제들"), cleaned.keys)
        // 평소에는 건드리지 않는다 — 진짜 알림 이름과 겹칠 수 있어서다.
        assertEquals(old, NameMemories.sanitize(old))
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
