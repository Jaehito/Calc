package com.calc.expense

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 첫 시작 파이프라인을 언제 보여줄지. 이 규칙이 흔들리면 «건너뛰기»가 건너뛰기가 아니게 된다.
 */
class OnboardingTest {

    @Test
    fun `처음 쓰는 사람에게는 보여준다`() {
        assertTrue(Onboarding.shouldShow(wasAsked = false, hasBudget = false))
    }

    @Test
    fun `건너뛴 사람에게 다시 묻지 않는다`() {
        // 앱을 열 때마다 다시 들이밀면 그건 건너뛰기가 아니다.
        assertFalse(Onboarding.shouldShow(wasAsked = true, hasBudget = false))
    }

    @Test
    fun `예산이 이미 있으면 묻지 않는다`() {
        // 옛 버전에서 설정에 예산을 직접 넣어 둔 사람이 업데이트했다고 온보딩을 볼 이유가 없다.
        assertFalse(Onboarding.shouldShow(wasAsked = false, hasBudget = true))
    }

    @Test
    fun `둘 다면 당연히 안 보여준다`() {
        assertFalse(Onboarding.shouldShow(wasAsked = true, hasBudget = true))
    }
}
