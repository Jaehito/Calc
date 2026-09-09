package com.calc.expense

/**
 * 첫 시작 파이프라인의 단계. 순서가 곧 화면 순서다.
 *
 * [INCOME] → [FIXED] → [RESULT] 가 기본 길이고, 어느 단계에서든 «나중에»를 누르면
 * [BUDGET] 한 칸으로 빠진다. [BUDGET] 에서 «고정비로 계산하기»를 누르면 [INCOME] 으로 되돌아온다.
 */
enum class OnboardingStep {
    /** 한 달에 얼마 버세요? */
    INCOME,

    /** 매달 그냥 나가는 돈. */
    FIXED,

    /** 이만큼 쓸 수 있어요 — 계산 결과와 동의 버튼. */
    RESULT,

    /** 건너뛴 사람이 보는 금액 한 칸, 그리고 «직접 고치기»가 도착하는 곳. */
    BUDGET,
}

/**
 * 첫 시작에 이 파이프라인을 보여줄지.
 *
 * **한 번 물어봤으면 다시 묻지 않는다** — 건너뛴 것도 «물어봤다»로 친다. 앱을 열 때마다 다시
 * 들이밀면 그건 건너뛰기가 아니다. 다시 하고 싶은 사람은 홈·설정의 «고정비로 계산하기»로
 * 직접 들어온다.
 *
 * 예산이 이미 있으면 묻지 않는다 — 옛 버전에서 설정 화면에 예산을 직접 넣어 둔 사람이
 * 업데이트했다고 온보딩을 다시 볼 이유가 없다.
 *
 * Android 에 의존하지 않아 단위 테스트로 고정한다.
 */
object Onboarding {

    fun shouldShow(wasAsked: Boolean, hasBudget: Boolean): Boolean = !wasAsked && !hasBudget
}
