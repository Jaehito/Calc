package com.calc.expense

/**
 * 하단 결제 팝업을 [gapMs] 에 한 번만 띄운다.
 *
 * 카드사 앱과 카카오페이가 같은 결제를 몇 초 차이로 각각 알리면 팝업이 두 번 떴다 — 첫 팝업을 눌러
 * 적는 사이 둘째 팝업이 키보드 자리를 가렸다. 금액으로 같은 결제를 가리는 대신 시간으로만 막는다:
 * 막힌 결제도 수집함에는 그대로 담긴다.
 *
 * Android 에 의존하지 않아 단위 테스트로 고정한다.
 */
class PopupThrottle(private val gapMs: Long) {

    private var lastShownAt: Long = Long.MIN_VALUE

    /** 띄워도 되면 지금을 기억하고 true. 마지막으로 띄운 지 [gapMs] 가 안 됐으면 false. */
    @Synchronized
    fun tryAcquire(now: Long): Boolean {
        if (lastShownAt != Long.MIN_VALUE && now - lastShownAt in 0 until gapMs) return false
        lastShownAt = now
        return true
    }
}
