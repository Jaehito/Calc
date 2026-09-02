package com.calc.expense

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CategoryClassifierTest {

    private val all: List<String> = Categories.DEFAULT

    @Test
    fun `커피는 카페로 분류된다`() {
        assertEquals("카페", CategoryClassifier.classify("커피", all))
        assertEquals("카페", CategoryClassifier.classify("스타벅스 아메리카노", all))
    }

    @Test
    fun `점심 저녁은 식비로 분류된다`() {
        assertEquals("식비", CategoryClassifier.classify("점심", all))
        assertEquals("식비", CategoryClassifier.classify("치킨", all))
    }

    @Test
    fun `장보기는 마트로 분류된다`() {
        assertEquals("마트", CategoryClassifier.classify("이마트 장보기", all))
        assertEquals("마트", CategoryClassifier.classify("gs25", all))
    }

    @Test
    fun `택시 주유는 교통으로 분류된다`() {
        assertEquals("교통", CategoryClassifier.classify("택시", all))
        assertEquals("교통", CategoryClassifier.classify("주유소", all))
    }

    @Test
    fun `약국은 건강으로 분류된다`() {
        // 실제 결제 알림에 자주 나오는 형태
        assertEquals("건강", CategoryClassifier.classify("다나약국", all))
    }

    @Test
    fun `대소문자를 가리지 않는다`() {
        assertEquals("마트", CategoryClassifier.classify("GS25", all))
        assertEquals("교통", CategoryClassifier.classify("KTX", all))
    }

    @Test
    fun `맞는 규칙이 없으면 null이라 없음을 유지한다`() {
        assertNull(CategoryClassifier.classify("정체불명의지출", all))
        assertNull(CategoryClassifier.classify("", all))
    }

    @Test
    fun `기타는 자동 분류되지 않는다`() {
        // «기타» 는 규칙이 없다 — 어떤 이름도 기타로 자동 분류되지 않는다
        assertNull(CategoryClassifier.classify("아무거나", all))
    }

    @Test
    fun `사용자가 칩 이름을 바꾸면 그 규칙은 꺼진다`() {
        val renamed: List<String> = all.filterNot { it == "카페" } + "커피값"
        assertNull(CategoryClassifier.classify("커피", renamed))
    }

    @Test
    fun `칩 목록에 없는 카테고리는 추천하지 않는다`() {
        val onlyFood: List<String> = listOf("식비", "기타")
        assertEquals("식비", CategoryClassifier.classify("점심", onlyFood))
        assertNull(CategoryClassifier.classify("커피", onlyFood))
    }
}
