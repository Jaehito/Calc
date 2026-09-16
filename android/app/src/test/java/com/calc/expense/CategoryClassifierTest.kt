package com.calc.expense

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
    fun `긴 낱말이 짧은 낱말을 이긴다`() {
        // 「코스트코 음식」에는 「코스트코」와 「음식」이 함께 들어 있다. 「음식」은 거의 모든
        // 지출에 붙을 수 있는 말이고 「코스트코」는 한 곳을 가리킨다 — 구체적인 쪽이 이겨야 한다.
        assertEquals("마트", CategoryClassifier.classify("코스트코 음식", all))
        assertEquals("교통", CategoryClassifier.classify("추석 버스비", all))
    }

    @Test
    fun `아이 물건은 육아로 분류된다`() {
        // 한 달에 십만 원 단위로 나가는데 예전에는 전부 «없음»이었다.
        assertEquals("육아", CategoryClassifier.classify("기저귀", all))
        assertEquals("육아", CategoryClassifier.classify("세아 이유식", all))
        assertEquals("육아", CategoryClassifier.classify("물티슈3개", all))
        assertEquals("육아", CategoryClassifier.classify("선호 놀이치료", all))
    }

    @Test
    fun `육아 칩이 없으면 생활이나 식비로 내려간다`() {
        // 같은 낱말을 여러 칸에 둔 것이 곧 대비책이다. 없는 칩을 켤 수는 없다.
        val noBaby: List<String> = all.filterNot { it == "육아" }

        assertEquals("생활", CategoryClassifier.classify("기저귀", noBaby))
        assertEquals("식비", CategoryClassifier.classify("이유식", noBaby))
    }

    @Test
    fun `이름 앞에 사람 이름이 붙어도 알아본다`() {
        // 손으로 적을 때 「루스 점심」·「세아 병원」처럼 누구 것인지 앞에 붙인다.
        assertEquals("식비", CategoryClassifier.classify("루스 점심", all))
        assertEquals("교통", CategoryClassifier.classify("루스 교통비", all))
        assertEquals("생활", CategoryClassifier.classify("세아 샴푸", all))
    }

    @Test
    fun `수량이 뒤에 붙어도 알아본다`() {
        assertEquals("식비", CategoryClassifier.classify("라면 4개", all))
        assertEquals("식비", CategoryClassifier.classify("닭가슴살 5개", all))
        assertEquals("식비", CategoryClassifier.classify("컵라면 2개", all))
    }

    @Test
    fun `빵은 간식이고 빵집은 카페다`() {
        // 긴 낱말이 이기므로 「빵집」이 「빵」을 누른다.
        assertEquals("간식", CategoryClassifier.classify("빵", all))
        assertEquals("카페", CategoryClassifier.classify("빵집", all))
    }

    @Test
    fun `과일과 간식을 가른다`() {
        assertEquals("과일", CategoryClassifier.classify("바나나", listOf("과일", "간식", "마트")))
        assertEquals("간식", CategoryClassifier.classify("초코바", all))
        assertEquals("간식", CategoryClassifier.classify("아이스크림", all))
    }

    @Test
    fun `술은 술 이름과 마시러 가는 곳으로 잡는다`() {
        val withSul: List<String> = all + "술"

        assertEquals("술", CategoryClassifier.classify("소주 2병", withSul))
        assertEquals("술", CategoryClassifier.classify("맥주", withSul))
        assertEquals("술", CategoryClassifier.classify("퇴근 후 술집", withSul))
        assertEquals("술", CategoryClassifier.classify("이자카야", withSul))
        assertEquals("술", CategoryClassifier.classify("노래방", withSul))
    }

    @Test
    fun `술이 든 다른 낱말은 술로 보지 않는다`() {
        // 「술」을 낱말 하나로 두면 기술·미술·수술·예술이 전부 걸린다.
        // 그래서 술 이름과 장소 이름으로만 잡는다.
        val withSul: List<String> = all + "술"

        assertNotEquals("술", CategoryClassifier.classify("미술관", withSul))
        assertNotEquals("술", CategoryClassifier.classify("수술비", withSul))
        assertNotEquals("술", CategoryClassifier.classify("예술의전당", withSul))
        assertNotEquals("술", CategoryClassifier.classify("기술서적", withSul))
    }

    @Test
    fun `술 칩이 없으면 식비나 문화로 내려간다`() {
        assertEquals("식비", CategoryClassifier.classify("소주", all))
        assertEquals("식비", CategoryClassifier.classify("이자카야", all))
        assertEquals("문화", CategoryClassifier.classify("노래방", all))
    }

    @Test
    fun `칩 목록에 없는 카테고리는 추천하지 않는다`() {
        val onlyFood: List<String> = listOf("식비", "기타")
        assertEquals("식비", CategoryClassifier.classify("점심", onlyFood))
        assertNull(CategoryClassifier.classify("커피", onlyFood))
    }
}
