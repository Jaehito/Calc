package com.calc.expense

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class ReminderPolicyTest {

    @Test
    fun `밤 10시부터 아침 8시까지는 무음이다`() {
        assertTrue(ReminderPolicy.isQuietHour(LocalTime.of(22, 0)))
        assertTrue(ReminderPolicy.isQuietHour(LocalTime.of(3, 30)))
        assertTrue(ReminderPolicy.isQuietHour(LocalTime.of(7, 59)))
        assertFalse(ReminderPolicy.isQuietHour(LocalTime.of(8, 0)))
        assertFalse(ReminderPolicy.isQuietHour(LocalTime.of(21, 59)))
        assertFalse(ReminderPolicy.isQuietHour(LocalTime.of(14, 0)))
    }

    @Test
    fun `기록이 없고 낮이고 상한 아래면 보낸다`() {
        assertTrue(ReminderPolicy.shouldRemind(recordedAfterPayment = false, quiet = false, todayCount = 0))
        assertTrue(ReminderPolicy.shouldRemind(recordedAfterPayment = false, quiet = false, todayCount = 2))
    }

    @Test
    fun `이미 적었으면 보내지 않는다`() {
        assertFalse(ReminderPolicy.shouldRemind(recordedAfterPayment = true, quiet = false, todayCount = 0))
    }

    @Test
    fun `무음 구간이면 보내지 않는다`() {
        assertFalse(ReminderPolicy.shouldRemind(recordedAfterPayment = false, quiet = true, todayCount = 0))
    }

    @Test
    fun `하루 세 번을 채우면 더 보내지 않는다`() {
        assertFalse(ReminderPolicy.shouldRemind(recordedAfterPayment = false, quiet = false, todayCount = 3))
        assertFalse(ReminderPolicy.shouldRemind(recordedAfterPayment = false, quiet = false, todayCount = 4))
    }
}
