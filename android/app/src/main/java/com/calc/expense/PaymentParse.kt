package com.calc.expense

/**
 * 알림 하나에서 뽑아낸 «결제로 보이는 것» 한 건. **아직 기록이 아니다** —
 * 수집함에서 사용자가 확인·수정해야 비로소 기록된다.
 *
 * @param amount 결제 금액. 누적·잔액은 걸러낸 값이다
 * @param merchant 짐작한 가맹점 이름. 못 찾으면 빈 문자열 — 그러면 사용자가 직접 적는다
 * @param category [CategoryClassifier] 가 가맹점 이름에서 짐작한 카테고리. 없으면 빈 문자열
 */
data class PaymentCandidate(
    val amount: Long,
    val merchant: String,
    val category: String,
)

/**
 * 결제 알림에서 금액·가맹점을 뽑아낸다.
 *
 * [PaymentDetector] 가 «결제인가»만 판정한다면 여기는 «얼마를 어디서»까지 읽는다. 카드사·은행·
 * 간편결제마다 문구가 달라 완벽한 파싱은 불가능하다 — 그래서 **짐작이고, 사용자가 고칠 수 있게**
 * 수집함에 담는다. 자동으로 기록하지 않는 이유가 이것이다.
 *
 * Android 에 의존하지 않아 단위 테스트로 고정한다.
 */
object PaymentParse {

    /** «숫자[,숫자]원» 꼴. 금액 후보를 전부 찾는다. */
    private val AMOUNT = Regex("([0-9][0-9,]*)\\s*원")

    /**
     * 금액 바로 앞에 이런 말이 붙어 있으면 결제액이 아니다.
     * 카드 문자는 «12,000원 ... 누적1,234,567원» 처럼 결제액과 누적액을 함께 싣는다.
     */
    private val NOT_SPENT = listOf("누적", "잔액", "합계", "총액", "한도", "포인트", "적립", "잔여")

    /** 금액 앞 몇 글자까지 [NOT_SPENT] 를 찾을지. «누적1,234,567원» 처럼 붙어 오기도 한다. */
    private const val LOOKBEHIND = 8

    /** 가맹점이 아닌 게 분명한 낱말. 토큰이 이 중 하나를 담고 있으면 후보에서 뺀다. */
    private val NOISE = listOf(
        "web발신", "발신", "승인", "취소", "결제", "출금", "이체", "입금", "송금", "구매", "사용",
        "지출", "청구", "인출", "일시불", "할부", "체크", "신용", "카드", "누적", "잔액", "합계",
        "한도", "포인트", "적립", "잔여", "은행", "페이", "고객", "안내", "알림", "정상",
    )

    /** «09/05», «14:23», «2026-09-05» 같은 날짜·시각 토큰. */
    private val DATE_TIME = Regex("^[0-9]{1,4}[/.:\\-][0-9]{1,2}([/.:\\-][0-9]{1,4})?$")

    /** 숫자·기호만 있는 토큰 (마스킹된 카드번호 «(1234)», «****» 등). */
    private val SYMBOLS_ONLY = Regex("^[0-9,()\\[\\]{}*\\-_.:/]+$")

    /** 이름 뒤에 붙어 오는 조사. «스타벅스에서» 를 «스타벅스» 로 되돌린다. */
    private val PARTICLES = listOf("에서는", "에서", "에게", "으로", "이랑", "에", "로", "은", "는", "이", "가", "을", "를", "와", "과", "의")

    /** 토큰 양끝에서 떼어낼 기호. */
    private const val TRIM_CHARS = "[]()·,.…-~!?\"'"

    /**
     * 알림 본문에서 결제 한 건을 읽는다. 금액을 못 찾으면 null — 금액 없는 알림은 후보가 아니다.
     *
     * @param categories 지금 쓰는 카테고리 칩 목록. 여기 없는 카테고리는 짐작하지 않는다
     */
    fun parse(title: String?, text: String?, categories: List<String>): PaymentCandidate? {
        val body: String = ((title ?: "") + "\n" + (text ?: "")).trim()
        if (body.isEmpty()) return null

        val amount: Long = readAmount(body) ?: return null
        val merchant: String = readMerchant(body)
        val category: String = CategoryClassifier.classify(merchant, categories).orEmpty()
        return PaymentCandidate(amount = amount, merchant = merchant, category = category)
    }

    /**
     * 결제 금액. 여러 금액이 있으면 «누적·잔액» 류를 뺀 **첫 번째**를 결제액으로 본다 —
     * 카드 문자는 결제액을 먼저, 누적액을 뒤에 싣는 것이 보통이다.
     */
    fun readAmount(body: String): Long? {
        for (match in AMOUNT.findAll(body)) {
            val start: Int = match.range.first
            val from: Int = maxOf(0, start - LOOKBEHIND)
            val before: String = body.substring(from, start)
            if (NOT_SPENT.any { before.contains(it) }) continue

            val digits: String = match.groupValues[1].replace(",", "")
            val value: Long = digits.toLongOrNull() ?: continue
            if (value > 0L) return value
        }
        return null
    }

    /**
     * 가맹점 이름을 짐작한다.
     *
     * 노이즈(카드사·승인·날짜·금액·마스킹 번호)를 걷어낸 뒤 **가장 긴 토큰**을 고른다. 길이가
     * 같으면 뒤쪽을 고른다 — 카드 문자는 가맹점을 사람 이름 뒤에 두는 경우가 많다.
     * 못 찾으면 빈 문자열이고, 그러면 수집함에서 사용자가 직접 적는다.
     */
    fun readMerchant(body: String): String {
        var best: String = ""
        for (raw in body.split(' ', '\n', '\t')) {
            val token: String = clean(raw)
            if (token.isEmpty()) continue
            if (token.length < 2) continue
            if (SYMBOLS_ONLY.matches(token)) continue
            if (DATE_TIME.matches(token)) continue
            if (token.contains("원") && token.any { it.isDigit() }) continue

            val lower: String = token.lowercase()
            if (NOISE.any { lower.contains(it) }) continue

            // 같은 길이면 뒤에 온 것으로 바꾼다 (>= 비교).
            if (token.length >= best.length) best = token
        }
        return best
    }

    /** 토큰 양끝의 기호와 뒤에 붙은 조사를 떼어낸다. */
    private fun clean(raw: String): String {
        val trimmed: String = raw.trim().trim { it in TRIM_CHARS }
        if (trimmed.isEmpty()) return ""

        for (particle in PARTICLES) {
            if (!trimmed.endsWith(particle)) continue
            val stem: String = trimmed.dropLast(particle.length)
            // 조사를 떼고 나서도 이름이라 할 만큼 남아야 한다 — «이마트» 를 «이마» 로 만들지 않는다.
            if (stem.length >= 2) return stem
        }
        return trimmed
    }
}
