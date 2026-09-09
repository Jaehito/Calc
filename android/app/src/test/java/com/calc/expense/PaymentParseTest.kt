package com.calc.expense

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 실제 카드사·간편결제 알림 문구 형식으로 고정한다. 파싱이 조용히 틀리면 수집함이
 * 엉뚱한 금액을 기록하자고 들이밀게 되므로, 형식마다 한 건씩 못 박아 둔다.
 */
class PaymentParseTest {

    private val categories: List<String> = Categories.DEFAULT

    @Test
    fun `신한 카드 문자 — 결제액과 누적액을 가른다`() {
        val text = "[Web발신]\n신한체크(1234)승인\n홍길동\n12,000원\n09/05 14:23\n스타벅스코엑스점\n누적1,234,567원"
        val found = PaymentParse.parse(null, text, categories)
        assertEquals(12_000L, found?.amount)
        assertEquals("스타벅스코엑스점", found?.merchant)
        assertEquals("카페", found?.category)
    }

    @Test
    fun `NH 체크카드 한 줄 형식`() {
        val found = PaymentParse.parse("NH농협", "NH체크카드 승인 12,000원 09/05 14:23 스타벅스", categories)
        assertEquals(12_000L, found?.amount)
        assertEquals("스타벅스", found?.merchant)
        assertEquals("카페", found?.category)
    }

    @Test
    fun `카카오페이 — 조사를 떼어 가맹점만 남긴다`() {
        val found = PaymentParse.parse("카카오페이", "스타벅스에서 12,000원을 결제했어요", categories)
        assertEquals(12_000L, found?.amount)
        assertEquals("스타벅스", found?.merchant)
    }

    @Test
    fun `토스 — 제목이 앱 이름이어도 본문에서 가맹점을 찾는다`() {
        val found = PaymentParse.parse("토스", "스타벅스 12,000원 결제", categories)
        assertEquals(12_000L, found?.amount)
        assertEquals("스타벅스", found?.merchant)
    }

    @Test
    fun `마트 결제 — 카테고리까지 짐작한다`() {
        val found = PaymentParse.parse(null, "KB국민카드 45,000원 일시불 승인 09/05 이마트성수점", categories)
        assertEquals(45_000L, found?.amount)
        assertEquals("이마트성수점", found?.merchant)
        assertEquals("마트", found?.category)
    }

    @Test
    fun `약국 결제 — 건강으로 짐작한다`() {
        val found = PaymentParse.parse(null, "삼성카드 승인 8,500원 09/05 다나약국", categories)
        assertEquals(8_500L, found?.amount)
        assertEquals("다나약국", found?.merchant)
        assertEquals("건강", found?.category)
    }

    @Test
    fun `규칙에 없는 가맹점은 카테고리를 비워 둔다`() {
        val found = PaymentParse.parse(null, "우리카드 승인 30,000원 09/05 성수철물점", categories)
        assertEquals(30_000L, found?.amount)
        assertEquals("성수철물점", found?.merchant)
        assertEquals("", found?.category)
    }

    @Test
    fun `가맹점 이름에 총이 들어가도 금액을 거르지 않는다`() {
        // NOT_SPENT 를 «총» 으로 두면 «총각네야채가게» 때문에 결제액이 통째로 걸러졌다.
        val found = PaymentParse.parse(null, "현대카드 승인 12,000원 09/05 총각네야채가게", categories)
        assertEquals(12_000L, found?.amount)
    }

    @Test
    fun `누적 잔액만 있는 알림은 후보가 아니다`() {
        assertNull(PaymentParse.parse("은행", "누적 1,234,567원", categories))
        assertNull(PaymentParse.parse("카드", "잔액 500,000원", categories))
    }

    @Test
    fun `금액이 없으면 후보가 아니다`() {
        assertNull(PaymentParse.parse("메시지", "오늘 저녁에 결제하자", categories))
    }

    @Test
    fun `빈 알림은 후보가 아니다`() {
        assertNull(PaymentParse.parse(null, null, categories))
        assertNull(PaymentParse.parse("", "   ", categories))
    }

    @Test
    fun `가맹점을 못 찾아도 금액이 있으면 후보로 남긴다`() {
        // 이름 후보가 전부 노이즈뿐인 경우 — 사용자가 수집함에서 직접 적는다.
        val found = PaymentParse.parse(null, "카드 승인 9,000원", categories)
        assertEquals(9_000L, found?.amount)
        assertEquals("", found?.merchant)
    }

    @Test
    fun `띄어쓰기 없이 붙은 누적액도 거른다`() {
        val found = PaymentParse.parse(null, "체크카드승인 7,700원 09/05 김밥천국 누적89,000원", categories)
        assertEquals(7_700L, found?.amount)
        assertEquals("김밥천국", found?.merchant)
        assertEquals("식비", found?.category)
    }

    // ── 실측으로 물린 형식들 (문구 10건을 돌려 5건이 오인이었다) ───────────────────

    @Test
    fun `카드사명과 뒷자리가 가맹점을 밀어내지 않는다`() {
        // 예전엔 «가장 긴 토큰» 규칙 탓에 «국민9876»(7자)이 «배달의민족»을 이겼다.
        val found = PaymentParse.parse(
            "KB Pay",
            "국민9876 승인\n홍길동님 43,000원\n09/07 19:02\n배달의민족",
            categories,
        )
        assertEquals(43_000L, found?.amount)
        assertEquals("배달의민족", found?.merchant)
    }

    @Test
    fun `우리카드 뒷자리보다 짧은 가맹점도 살아남는다`() {
        val found = PaymentParse.parse(
            "15885000",
            "우리4321 승인\n홍길동 3,000원\n09/07 09:20\n메가커피",
            categories,
        )
        assertEquals(3_000L, found?.amount)
        assertEquals("메가커피", found?.merchant)
    }

    @Test
    fun `현대카드 푸시 — 한 줄에 뒷자리와 가맹점이 함께 와도 자리로 고른다`() {
        val found = PaymentParse.parse("현대카드", "현대2580 승인 32,400원\n09/07 20:15 이마트", categories)
        assertEquals(32_400L, found?.amount)
        assertEquals("이마트", found?.merchant)
    }

    @Test
    fun `알림 제목의 앱 이름을 가맹점으로 쓰지 않는다`() {
        // 제목 «배달의민족»이 본문의 «교촌치킨»을 밀어내던 문제.
        val found = PaymentParse.parse("배달의민족", "결제완료 24,000원\n가게 교촌치킨 성수점", categories)
        assertEquals(24_000L, found?.amount)
        assertEquals("교촌치킨", found?.merchant)
    }

    @Test
    fun `주문번호를 가맹점으로 읽지 않는다`() {
        val found = PaymentParse.parse(
            "카카오페이",
            "올리브영 결제 18,700원\n주문번호 20260907113355",
            categories,
        )
        assertEquals(18_700L, found?.amount)
        assertEquals("올리브영", found?.merchant)
    }

    @Test
    fun `법인 접두사를 떼어낸다`() {
        val found = PaymentParse.parse(
            "삼성카드",
            "일시불 승인\n홍*동님\n5,500원\n09/07 12:34\n(주)스타벅스커피코리아",
            categories,
        )
        assertEquals(5_500L, found?.amount)
        assertEquals("스타벅스커피코리아", found?.merchant)
    }

    @Test
    fun `계좌 출금 — 잔액 줄을 건너뛰고 적요를 읽는다`() {
        val found = PaymentParse.parse(
            "NH농협",
            "NH농협 09/07 15:23\n출금 300,000원\n잔액 1,234,567원\n관리비",
            categories,
        )
        assertEquals(300_000L, found?.amount)
        assertEquals("관리비", found?.merchant)
    }

    @Test
    fun `사람 이름은 가맹점이 아니다`() {
        // 날짜·시각이 없어 길이 규칙으로 되돌아가는 형식. «홍길동님»(4자)이 이기면 안 된다.
        val found = PaymentParse.parse(null, "홍길동님 GS25 5,500원", categories)
        assertEquals(5_500L, found?.amount)
        assertEquals("GS25", found?.merchant)
    }

    // ── 은행명·사람 이름 (계좌 이체) ─────────────────────────────────────────────

    @Test
    fun `계좌 이체는 은행명을 사람 이름 앞에 붙인다`() {
        // 실제로 물린 것: 수집함에 «최재호» 만 남아 어느 계좌에서 나갔는지 알 수 없었다.
        val found = PaymentParse.parse(
            "1577-8000",
            "[Web발신]\n신한은행 09/09 09:14\n입금 1원\n최재호",
            categories,
        )
        assertEquals(1L, found?.amount)
        assertEquals("신한은행 최재호", found?.merchant)
        assertEquals("신한은행", found?.issuer)
    }

    @Test
    fun `카드 승인에는 은행명을 붙이지 않는다`() {
        // «신한 이마트» 는 이름을 더 나쁘게 만든다. 은행명은 계좌 거래에서만 뜻이 있다.
        val found = PaymentParse.parse("신한카드", "신한1234 승인 32,400원 09/07 20:15 이마트", categories)
        assertEquals("이마트", found?.merchant)
    }

    @Test
    fun `이체라도 사람 이름이 아니면 그대로 둔다`() {
        // «관리비»·«임대료» 같은 적요에 은행명을 붙일 이유가 없다.
        val found = PaymentParse.parse(
            "NH농협",
            "NH농협 09/07 15:23\n출금 300,000원\n잔액 1,234,567원\n관리비",
            categories,
        )
        assertEquals("관리비", found?.merchant)
    }

    @Test
    fun `이름을 못 읽으면 은행명이라도 남긴다`() {
        // 빈칸보다는 «카카오뱅크» 가 낫다 — 어디서 나간 돈인지는 알 수 있다.
        val found = PaymentParse.parse("카카오뱅크", "카드 승인 9,000원", categories)
        assertEquals(9_000L, found?.amount)
        assertEquals("카카오뱅크", found?.merchant)
    }

    @Test
    fun `은행 이름 자체가 가맹점을 밀어내지 않는다`() {
        val found = PaymentParse.parse(null, "카카오뱅크 승인 12,000원 09/05 14:23 메가커피", categories)
        assertEquals("메가커피", found?.merchant)
    }

    @Test
    fun `이미 은행명으로 시작하면 두 번 붙이지 않는다`() {
        assertEquals("신한은행 최재호", PaymentParse.nameFor("신한은행 최재호", "신한은행", "이체 1원"))
    }

    @Test
    fun `사람 이름 판정 — 성으로 시작하는 2~4글자만`() {
        assertTrue(PaymentParse.looksLikePerson("최재호"))
        assertTrue(PaymentParse.looksLikePerson("홍*동"))
        assertFalse(PaymentParse.looksLikePerson("관리비"))
        assertFalse(PaymentParse.looksLikePerson("임대료"))
        assertFalse(PaymentParse.looksLikePerson("스타벅스코엑스점"))
        assertFalse(PaymentParse.looksLikePerson("GS25"))
    }

    @Test
    fun `발신번호는 이름 노릇을 못 한다`() {
        assertTrue(PaymentParse.looksLikeNumber("1577-8000"))
        assertTrue(PaymentParse.looksLikeNumber("15885000"))
        assertTrue(PaymentParse.looksLikeNumber("010-1234-5678"))
        assertFalse(PaymentParse.looksLikeNumber("카카오뱅크"))
        assertFalse(PaymentParse.looksLikeNumber("KB Pay"))
    }

    @Test
    fun `제목만 있는 알림은 제목에서라도 찾는다`() {
        val found = PaymentParse.parse("스타벅스 5,500원", null, categories)
        assertEquals(5_500L, found?.amount)
        assertEquals("스타벅스", found?.merchant)
    }
}
