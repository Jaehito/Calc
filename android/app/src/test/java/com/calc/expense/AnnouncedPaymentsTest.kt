package com.calc.expense

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnnouncedPaymentsTest {

    private val minute: Long = 60_000L

    @Test
    fun `같은 결제가 두 알림으로 오면 한 번만 알린다`() {
        // 카드사 앱 알림 뒤 카카오페이 알림이 몇 초 늦게 온다.
        val announced = AnnouncedPayments()
        assertTrue(announced.tryClaim(12_000L, 0L))
        assertFalse(announced.tryClaim(12_000L, 8_000L))
    }

    @Test
    fun `금액이 다르면 따로 알린다`() {
        val announced = AnnouncedPayments()
        assertTrue(announced.tryClaim(12_000L, 0L))
        assertTrue(announced.tryClaim(4_500L, minute))
    }

    @Test
    fun `5분이 지나면 같은 금액도 새 결제로 본다`() {
        // 수집함이 합치는 기준과 같다.
        val announced = AnnouncedPayments()
        assertTrue(announced.tryClaim(4_500L, 0L))
        assertTrue(announced.tryClaim(4_500L, 6 * minute))
    }

    @Test
    fun `늦게 온 알림이 시각상 먼저여도 같은 결제다`() {
        // 문자 알림의 시각이 앱 푸시보다 이를 수 있다.
        val announced = AnnouncedPayments()
        assertTrue(announced.tryClaim(9_900L, 10_000L))
        assertFalse(announced.tryClaim(9_900L, 2_000L))
    }
}
