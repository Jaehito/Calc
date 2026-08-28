package com.calc.expense

/**
 * 알림 하나가 «결제»인지 판정한다. **금액을 읽지 않는다** — 얼마인지는 관심 없고,
 * «무언가 결제됐다»는 신호만 본다. 그 판단만으로 «적었어?» 리마인더를 예약한다.
 *
 * 은행·카드·간편결제 앱마다 문구가 달라 특정 앱에 매지 않는다. 대신 두 조건을 함께 본다:
 * 결제를 뜻하는 낱말이 있고, 금액처럼 보이는 «숫자+원» 이 있어야 한다. 둘을 함께 걸어야
 * 채팅에 섞인 «결제하자» 같은 말을 결제로 오인하지 않는다.
 *
 * Android 에 의존하지 않아 단위 테스트로 고정한다.
 */
object PaymentDetector {

    /** 결제·출금을 뜻하는 낱말. 금액과 함께 있을 때만 결제로 본다. */
    private val KEYWORDS = listOf(
        "결제", "승인", "출금", "이체", "인출", "청구", "송금", "사용", "지출", "구매",
    )

    /** «12,000원», «3000 원» 처럼 숫자에 원이 붙은 꼴. */
    private val AMOUNT = Regex("[0-9][0-9,]*\\s*원")

    fun isPayment(title: String?, text: String?): Boolean {
        val body: String = ((title ?: "") + " " + (text ?: "")).trim()
        if (body.isEmpty()) return false
        if (!AMOUNT.containsMatchIn(body)) return false
        return KEYWORDS.any { body.contains(it) }
    }
}
