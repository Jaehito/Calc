package com.calc.expense

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
