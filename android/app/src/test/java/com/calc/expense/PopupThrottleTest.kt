package com.calc.expense

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PopupThrottleTest {

    @Test
    fun `10초 안에 온 둘째 팝업은 막는다`() {
        val throttle = PopupThrottle(10_000L)
        assertTrue(throttle.tryAcquire(1_000L))
        assertFalse(throttle.tryAcquire(4_000L))
        assertFalse(throttle.tryAcquire(10_999L))
    }

    @Test
    fun `10초가 지나면 다시 띄운다`() {
        val throttle = PopupThrottle(10_000L)
        assertTrue(throttle.tryAcquire(1_000L))
        assertTrue(throttle.tryAcquire(11_000L))
    }

    @Test
    fun `막힌 팝업은 시간을 다시 세지 않는다`() {
        // 막힌 결제가 잇따라 와도 첫 팝업 기준 10초 뒤에는 다시 뜬다.
        val throttle = PopupThrottle(10_000L)
        assertTrue(throttle.tryAcquire(0L))
        assertFalse(throttle.tryAcquire(6_000L))
        assertTrue(throttle.tryAcquire(10_000L))
    }

    @Test
    fun `첫 팝업은 언제든 뜬다`() {
        assertTrue(PopupThrottle(10_000L).tryAcquire(0L))
    }
}
