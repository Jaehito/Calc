package com.calc.expense

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GradingTest {

    @Test
    fun `정확히 50퍼센트면 S다`() {
        assertEquals(Grade.S, Grading.of(spent = 5_000L, budget = 10_000L))
    }

    @Test
    fun `50퍼센트를 살짝 넘으면 A다`() {
        assertEquals(Grade.A, Grading.of(spent = 5_001L, budget = 10_000L))
    }

    @Test
    fun `정확히 80퍼센트면 A다`() {
        assertEquals(Grade.A, Grading.of(spent = 8_000L, budget = 10_000L))
    }

    @Test
    fun `80퍼센트를 살짝 넘으면 B다`() {
        assertEquals(Grade.B, Grading.of(spent = 8_001L, budget = 10_000L))
    }

    @Test
    fun `정확히 예산만큼 쓰면 B다`() {
        assertEquals(Grade.B, Grading.of(spent = 10_000L, budget = 10_000L))
    }

    @Test
    fun `예산을 살짝 넘으면 C다`() {
        assertEquals(Grade.C, Grading.of(spent = 10_001L, budget = 10_000L))
    }

    @Test
    fun `정확히 130퍼센트면 C다`() {
        assertEquals(Grade.C, Grading.of(spent = 13_000L, budget = 10_000L))
    }

    @Test
    fun `130퍼센트를 넘으면 D다`() {
        assertEquals(Grade.D, Grading.of(spent = 13_001L, budget = 10_000L))
    }

    @Test
    fun `안 쓴 날은 S다`() {
        assertEquals(Grade.S, Grading.of(spent = 0L, budget = 10_000L))
    }

    @Test
    fun `예산이 0이면 매기지 않는다`() {
        assertNull(Grading.of(spent = 0L, budget = 0L))
        assertNull(Grading.of(spent = 5_000L, budget = -1L))
    }

    @Test
    fun `음수 지출은 0으로 바닥을 둔다`() {
        // 지웠다 되돌리는 과정에서 순간적으로 음수 합계가 잡히더라도 S 아래로 떨어지지 않는다.
        assertEquals(Grade.S, Grading.of(spent = -3_000L, budget = 10_000L))
    }
}
