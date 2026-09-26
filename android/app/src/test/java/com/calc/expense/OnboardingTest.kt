package com.calc.expense

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    @Test
    fun `처음 쓰는 사람은 금액 한 칸, 계산하러 온 사람은 월급부터`() {
        assertEquals(OnboardingStep.BUDGET, Onboarding.firstStep(firstRun = true))
        assertEquals(OnboardingStep.INCOME, Onboarding.firstStep(firstRun = false))
    }

    @Test
    fun `알림은 처음 쓰는 사람에게 꺼져 있을 때만 묻는다`() {
        assertTrue(Onboarding.asksNotification(firstRun = true, notificationOn = false))
        assertFalse(Onboarding.asksNotification(firstRun = true, notificationOn = true))
        // 설정에서 계산하러 온 사람에게 알림을 또 묻지 않는다.
        assertFalse(Onboarding.asksNotification(firstRun = false, notificationOn = false))
    }

    @Test
    fun `시작한 단계에서 뒤로가면 나간다`() {
        assertNull(Onboarding.previous(OnboardingStep.BUDGET, first = OnboardingStep.BUDGET))
        assertNull(Onboarding.previous(OnboardingStep.INCOME, first = OnboardingStep.INCOME))
    }

    @Test
    fun `금액 한 칸과 월급은 서로의 앞이다`() {
        // 금액 한 칸에서 시작해 계산하러 갔으면 월급에서 뒤로가면 금액 한 칸.
        assertEquals(OnboardingStep.BUDGET, Onboarding.previous(OnboardingStep.INCOME, first = OnboardingStep.BUDGET))
        // 월급에서 시작해 «나중에»로 금액 한 칸에 왔으면 뒤로가면 월급.
        assertEquals(OnboardingStep.INCOME, Onboarding.previous(OnboardingStep.BUDGET, first = OnboardingStep.INCOME))
    }

    @Test
    fun `코드 입력에서 뒤로가면 금액 한 칸, 알림 단계에서는 나간다`() {
        assertEquals(OnboardingStep.BUDGET, Onboarding.previous(OnboardingStep.JOIN, first = OnboardingStep.BUDGET))
        assertNull(Onboarding.previous(OnboardingStep.NOTIFY, first = OnboardingStep.BUDGET))
        assertEquals(OnboardingStep.FIXED, Onboarding.previous(OnboardingStep.RESULT, first = OnboardingStep.BUDGET))
    }
}
