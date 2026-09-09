package com.calc.expense

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * 상시 카드를 다시 올리는 시각 규칙. 낮에는 한 시간마다, 밤에는 쉰다.
 */
class CardRefreshTimeTest {

    @Test
    fun `낮에는 한 시간 뒤`() {
        val delay: Duration = CardRefreshTime.untilNextRefresh(LocalDateTime.of(2026, 9, 9, 14, 20))
        assertEquals(Duration.ofHours(1), delay)
    }

    @Test
    fun `밤으로 넘어가면 아침 8시로 민다`() {
        // 21시 30분의 한 시간 뒤는 22시 30분 — 무음 구간이라 자정을 넘겨 아침으로.
        val delay: Duration = CardRefreshTime.untilNextRefresh(LocalDateTime.of(2026, 9, 9, 21, 30))
        assertEquals(Duration.ofHours(10) + Duration.ofMinutes(30), delay)
    }

    @Test
    fun `한밤중에 예약해도 다음 아침 한 번뿐이다`() {
        // 밤새 한 시간마다 깨어나 아무것도 안 하고 다시 예약하는 낭비를 없앤다.
        val delay: Duration = CardRefreshTime.untilNextRefresh(LocalDateTime.of(2026, 9, 9, 3, 0))
        assertEquals(Duration.ofHours(5), delay)
    }

    @Test
    fun `아침 7시 반은 8시 반으로 — 이미 무음이 끝나 있다`() {
        val delay: Duration = CardRefreshTime.untilNextRefresh(LocalDateTime.of(2026, 9, 9, 7, 30))
        assertEquals(Duration.ofHours(1), delay)
    }

    @Test
    fun `밤에 깨어난 워커는 카드를 올리지 않는다`() {
        assertFalse(CardRefreshTime.canRefresh(LocalTime.of(23, 0)))
        assertFalse(CardRefreshTime.canRefresh(LocalTime.of(3, 0)))
        assertTrue(CardRefreshTime.canRefresh(LocalTime.of(8, 0)))
        assertTrue(CardRefreshTime.canRefresh(LocalTime.of(21, 59)))
    }

    @Test
    fun `무음 구간은 결제 리마인더와 같은 규칙을 쓴다`() {
        // 두 곳이 따로 정하면 «밤» 의 뜻이 갈린다.
        assertEquals(!ReminderPolicy.isQuietHour(LocalTime.of(22, 0)), CardRefreshTime.canRefresh(LocalTime.of(22, 0)))
    }
}
