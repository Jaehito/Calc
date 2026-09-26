package com.calc.expense

/**
 * 첫 시작 파이프라인의 단계.
 *
 * 처음 쓰는 사람은 [BUDGET] 금액 한 칸에서 시작해 [NOTIFY] 를 지나 홈으로 간다. 계산해 보고 싶은
 * 사람은 [BUDGET] 의 링크로 [INCOME] → [FIXED] → [RESULT] 를 돌고, 배우자에게 코드를 받은
 * 사람은 [JOIN] 에 들렀다 [BUDGET] 으로 돌아온다.
 *
 * 설정·홈의 «고정비로 계산하기»로 들어오면 [INCOME] 부터 시작하고 [NOTIFY] 는 건너뛴다 —
 * 계산하러 온 사람에게 알림을 또 묻지 않는다.
 */
enum class OnboardingStep {
    /** 한 달에 얼마 쓸 거예요? — 처음 쓰는 사람의 첫 화면, 그리고 «직접 고치기»가 도착하는 곳. */
    BUDGET,

    /** 한 달에 얼마 버세요? */
    INCOME,

    /** 매달 그냥 나가는 돈. */
    FIXED,

    /** 이만큼 쓸 수 있어요 — 계산 결과와 동의 버튼. */
    RESULT,

    /** 배우자에게 받은 가정 코드 넣기. */
    JOIN,

    /** 잠금화면 알림 켜기. 예산을 정한 뒤 한 번만. */
    NOTIFY,
}

/**
 * 첫 시작의 규칙. Android 에 의존하지 않아 단위 테스트로 고정한다.
 */
object Onboarding {

    /**
     * 첫 시작에 이 파이프라인을 보여줄지.
     *
     * **한 번 물어봤으면 다시 묻지 않는다** — 건너뛴 것도 «물어봤다»로 친다. 앱을 열 때마다 다시
     * 들이밀면 그건 건너뛰기가 아니다. 다시 하고 싶은 사람은 홈·설정의 «고정비로 계산하기»로
     * 직접 들어온다.
     *
     * 예산이 이미 있으면 묻지 않는다 — 옛 버전에서 설정 화면에 예산을 직접 넣어 둔 사람이
     * 업데이트했다고 온보딩을 다시 볼 이유가 없다.
     */
    fun shouldShow(wasAsked: Boolean, hasBudget: Boolean): Boolean = !wasAsked && !hasBudget

    /** 첫 화면. 처음 쓰는 사람은 금액 한 칸, 계산하러 온 사람은 월급부터. */
    fun firstStep(firstRun: Boolean): OnboardingStep =
        if (firstRun) OnboardingStep.BUDGET else OnboardingStep.INCOME

    /** 예산을 정한 뒤 알림 단계를 보여줄지. 처음 쓰는 사람에게, 알림이 아직 꺼져 있을 때만. */
    fun asksNotification(firstRun: Boolean, notificationOn: Boolean): Boolean = firstRun && !notificationOn

    /**
     * 뒤로가기가 갈 단계. null 이면 화면을 나간다.
     *
     * 시작한 단계에서 뒤로가면 나간다. [BUDGET] 과 [INCOME] 은 서로의 «앞»이 된다 — 어느 쪽에서
     * 시작했느냐에 따라 다른 쪽은 거기서 한 번 들어온 곳이다. [NOTIFY] 는 예산을 이미 저장한 뒤라
     * 되돌아갈 곳이 없다 — 뒤로가면 «나중에»와 같다(null).
     */
    fun previous(step: OnboardingStep, first: OnboardingStep): OnboardingStep? = when (step) {
        first -> null
        OnboardingStep.BUDGET -> OnboardingStep.INCOME
        OnboardingStep.INCOME -> OnboardingStep.BUDGET
        OnboardingStep.FIXED -> OnboardingStep.INCOME
        OnboardingStep.RESULT -> OnboardingStep.FIXED
        OnboardingStep.JOIN -> OnboardingStep.BUDGET
        OnboardingStep.NOTIFY -> null
    }
}
