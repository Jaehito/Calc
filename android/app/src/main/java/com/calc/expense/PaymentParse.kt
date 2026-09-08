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
        // «주문번호 20260907…» 의 앞 토큰. «번호» 가 들어간 낱말은 가맹점이 아니다.
        "번호",
    )

    /**
     * 카드사·은행 이름에 카드 뒷자리가 붙은 토큰. **가맹점보다 길어서 이름 자리를 뺏던 주범이다.**
     *
     * 실측(문구 10건): «국민9876»·«우리4321»·«현대2580» 이 각각 «배달의민족»·«메가커피»·«이마트»를
     * 밀어냈다. [NOISE] 에 «카드» 는 있었지만 은행 이름은 없어서 걸러지지 않았다.
     */
    private val ISSUER_TAIL = Regex(
        "^(국민|신한|우리|하나|농협|기업|씨티|수협|새마을|삼성|현대|롯데|비씨|BC|KB|NH|IBK|SC)" +
            "[0-9*]{2,4}$",
        RegexOption.IGNORE_CASE,
    )

    /** «09/05», «14:23», «2026-09-05» 같은 날짜·시각 토큰. */
    private val DATE_TIME = Regex("^[0-9]{1,4}[/.:\\-][0-9]{1,2}([/.:\\-][0-9]{1,4})?$")

    /** 숫자·기호만 있는 토큰 (마스킹된 카드번호 «(1234)», «****» 등). */
    private val SYMBOLS_ONLY = Regex("^[0-9,()\\[\\]{}*\\-_.:/]+$")

    /** 이름 뒤에 붙어 오는 조사. «스타벅스에서» 를 «스타벅스» 로 되돌린다. */
    private val PARTICLES = listOf("에서는", "에서", "에게", "으로", "이랑", "에", "로", "은", "는", "이", "가", "을", "를", "와", "과", "의")

    /** 법인 접두사. «(주)스타벅스커피코리아» 를 «스타벅스커피코리아» 로 되돌린다. */
    private val COMPANY_PREFIXES = listOf("(주)", "㈜", "(유)", "주식회사", "유한회사")

    /** 토큰 양끝에서 떼어낼 기호. */
    private const val TRIM_CHARS = "[]()·,.…-~!?\"'"

    /**
     * 알림에서 결제 한 건을 읽는다. 금액을 못 찾으면 null — 금액 없는 알림은 후보가 아니다.
     *
     * **금액은 제목까지 뒤지고 가맹점은 본문에서만 찾는다.** 제목은 앱 이름이나 문자 발신번호라
     * 가맹점이 아니다 — 배달앱 알림에서 제목 «배달의민족» 이 본문의 «교촌치킨» 을 밀어내던
     * 문제가 여기서 왔다. 다만 본문이 비어 있으면 제목이라도 본다(그것뿐이라서).
     *
     * @param categories 지금 쓰는 카테고리 칩 목록. 여기 없는 카테고리는 짐작하지 않는다
     */
    fun parse(title: String?, text: String?, categories: List<String>): PaymentCandidate? {
        val body: String = ((title ?: "") + "\n" + (text ?: "")).trim()
        if (body.isEmpty()) return null

        val amount: Long = readAmount(body) ?: return null
        val nameSource: String = (text ?: "").trim().ifEmpty { (title ?: "").trim() }
        val merchant: String = readMerchant(nameSource)
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
     * 가맹점 이름을 짐작한다. **길이가 아니라 자리로 고른다.**
     *
     * 국내 결제 문구는 «승인 / 금액 / 날짜·시각 / 가맹점» 순서가 압도적이라, 마지막 날짜·시각
     * 토큰 **다음에 오는 첫 쓸만한 토큰**이 거의 언제나 가맹점이다. 예전처럼 «가장 긴 토큰» 을
     * 고르면 카드사명+뒷자리(«현대2580»)나 사람 이름(«홍길동님»)이 이긴다.
     *
     * 날짜·시각이 없는 문구(간편결제 «…에서 …원을 결제했어요»)에서는 예전대로 가장 긴 토큰으로
     * 되돌아간다. 그 형식은 가맹점이 문장 앞에 오고 나머지가 노이즈라 길이 규칙이 잘 맞는다.
     *
     * 못 찾으면 빈 문자열이고, 그러면 수집함에서 사용자가 직접 적는다 —
     * **틀린 이름보다 빈칸이 낫다.** 틀린 이름은 그대로 노션에 들어가 되돌리기 번거롭다.
     */
    fun readMerchant(body: String): String {
        val tokens: List<String> = body.split(' ', '\n', '\t')

        val afterDateTime: String = firstUsableAfterDateTime(tokens)
        if (afterDateTime.isNotEmpty()) return afterDateTime

        var best = ""
        for (raw in tokens) {
            val token: String = usable(raw) ?: continue
            // 같은 길이면 뒤에 온 것으로 바꾼다 (>= 비교).
            if (token.length >= best.length) best = token
        }
        return best
    }

    /** 마지막 날짜·시각 토큰 뒤에서 처음 나오는 쓸만한 토큰. 없으면 빈 문자열. */
    private fun firstUsableAfterDateTime(tokens: List<String>): String {
        var lastDateTime = -1
        for (i in tokens.indices) {
            if (DATE_TIME.matches(tokens[i].trim().trim { it in TRIM_CHARS })) lastDateTime = i
        }
        if (lastDateTime < 0) return ""

        for (i in lastDateTime + 1 until tokens.size) {
            val token: String? = usable(tokens[i])
            if (token != null) return token
        }
        return ""
    }

    /** 가맹점 이름이 될 수 있는 토큰이면 다듬어서, 아니면 null. */
    private fun usable(raw: String): String? {
        val token: String = clean(raw)
        if (token.length < 2) return null
        if (SYMBOLS_ONLY.matches(token)) return null
        if (DATE_TIME.matches(token)) return null
        if (ISSUER_TAIL.matches(token)) return null
        // «홍길동님» 같은 사람 이름. 조사와 달리 떼어내지 않고 통째로 버린다 —
        // 이름을 떼어 봐야 가맹점이 아니다.
        if (token.endsWith("님")) return null
        if (token.contains("원") && token.any { it.isDigit() }) return null

        val lower: String = token.lowercase()
        if (NOISE.any { lower.contains(it) }) return null
        return token
    }

    /** 토큰 앞의 법인 접두사와 양끝 기호, 뒤에 붙은 조사를 떼어낸다. */
    private fun clean(raw: String): String {
        var work: String = raw.trim()
        for (prefix in COMPANY_PREFIXES) {
            if (work.startsWith(prefix)) {
                work = work.substring(prefix.length)
                break
            }
        }

        val trimmed: String = work.trim { it in TRIM_CHARS }
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
