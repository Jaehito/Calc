package com.calc.expense

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentDetectorTest {

    @Test
    fun `결제 낱말과 금액이 함께 있으면 결제로 본다`() {
        assertTrue(PaymentDetector.isPayment("KB국민카드", "12,000원 승인 스타벅스"))
        assertTrue(PaymentDetector.isPayment("카카오페이", "결제 3,500원 편의점"))
        assertTrue(PaymentDetector.isPayment("신한", "출금 50000원"))
        assertTrue(PaymentDetector.isPayment(null, "토스 45,000원 이체 완료"))
    }

    @Test
    fun `금액이 없으면 결제로 보지 않는다`() {
        // 채팅에 섞인 결제라는 말은 금액이 없어 걸리지 않는다
        assertFalse(PaymentDetector.isPayment("친구", "이거 결제해서 보내줘"))
        assertFalse(PaymentDetector.isPayment("공지", "카드 승인 관련 안내입니다"))
    }

    @Test
    fun `결제 낱말이 없으면 금액이 있어도 결제로 보지 않는다`() {
        assertFalse(PaymentDetector.isPayment("날씨", "오늘 강수량 30원 어쩌구"))
        assertFalse(PaymentDetector.isPayment("뉴스", "환율 1,300원 돌파"))
    }

    @Test
    fun `빈 알림은 결제가 아니다`() {
        assertFalse(PaymentDetector.isPayment(null, null))
        assertFalse(PaymentDetector.isPayment("", ""))
    }
}
