package com.calc.expense

/**
 * 하단 팝업·배너로 이미 알린 결제. **한 결제는 한 번만 알린다.**
 *
 * 카드사 앱과 카카오페이가 같은 결제를 각각 알리면 팝업이 두 번 떴다 — 첫 팝업을 눌러 수집함에서
 * 적는 사이 늦게 온 둘째 팝업이 키보드 자리에 또 떴다. 수집함([PendingPayments.add])이 둘을 하나로
 * 합치는 것과 같은 기준(금액이 같고 [PendingPayments.DUPLICATE_WINDOW_MINUTES] 분 안)으로 거른다.
 *
 * 프로세스 안에만 둔다 — 몇 분짜리 기억이라 저장할 까닭이 없다. Android 에 의존하지 않아 단위 테스트로 고정한다.
 */
class AnnouncedPayments {

    private val recent = ArrayDeque<Pair<Long, Long>>()

    /** 처음 보는 결제면 기억하고 true(알린다), 방금 알린 결제와 같으면 false. */
    @Synchronized
    fun tryClaim(amount: Long, at: Long): Boolean {
        recent.removeAll { (_, seen) -> at - seen > PendingPayments.DUPLICATE_WINDOW_MINUTES * MINUTE_MS }
        if (recent.any { (seenAmount, seen) -> PendingPayments.isSameMoment(seenAmount, seen, amount, at) }) return false
        recent.addLast(amount to at)
        while (recent.size > MAX_KEPT) recent.removeFirst()
        return true
    }

    private companion object {
        const val MINUTE_MS = 60L * 1000L
        const val MAX_KEPT = 20
    }
}
