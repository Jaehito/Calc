package com.calc.expense

/**
 * 알림 하나에서 뽑아낸 «결제로 보이는 것» 한 건. **아직 기록이 아니다** —
 * 수집함에서 사용자가 확인·수정해야 비로소 기록된다.
 *
 * @param amount 결제 금액. 누적·잔액은 걸러낸 값이다
 * @param merchant 짐작한 가맹점 이름. 못 찾으면 빈 문자열 — 그러면 사용자가 직접 적는다
 * @param category [CategoryClassifier] 가 가맹점 이름에서 짐작한 카테고리. 없으면 빈 문자열
 * @param issuer 내용에서 읽은 은행·카드사·페이 이름. 없으면 빈 문자열 —
 *   알림 제목이 발신번호(«1577-8000»)일 때 그 자리를 대신한다
 */
data class PaymentCandidate(
    val amount: Long,
    val merchant: String,
    val category: String,
    val issuer: String = "",
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
     * 은행·카드사·간편결제 브랜드 이름. **긴 이름이 먼저 와야 한다** — «신한은행» 을 «신한» 으로
     * 잘라 읽지 않기 위해서다.
     *
     * 쓰는 곳이 둘이다. ① 알림 제목이 발신번호(«1577-8000»)라 이름 노릇을 못 할 때 그 자리를
     * 대신한다. ② 계좌 이체처럼 상대가 사람인 거래에서 «신한은행 최재호» 로 앞에 붙는다 —
     * 사람 이름만 남으면 며칠 뒤 내역에서 «이게 어느 계좌였지» 를 알 수 없다.
     */
    private val ISSUERS = listOf(
        "카카오뱅크", "케이뱅크", "토스뱅크", "카카오페이", "네이버페이", "삼성페이", "페이코",
        "새마을금고", "우체국", "농협은행", "국민은행", "신한은행", "우리은행", "하나은행",
        "기업은행", "수협은행", "부산은행", "대구은행", "광주은행", "전북은행", "경남은행",
        "제주은행", "신협", "산업은행", "씨티은행", "SC제일은행",
        "국민카드", "신한카드", "우리카드", "하나카드", "삼성카드", "현대카드", "롯데카드",
        "비씨카드", "농협카드", "KB국민", "NH농협", "IBK기업", "카카오", "토스",
        "국민", "신한", "우리", "하나", "농협", "기업", "삼성", "현대", "롯데", "비씨",
        "KB", "NH", "IBK", "BC", "SC",
    )

    /**
     * 계좌 거래를 뜻하는 낱말. 이 낱말이 있으면 상대 이름이 **가맹점이 아니라 사람**일 수 있다.
     *
     * 카드 승인(«승인»·«결제»)과 갈라 두는 이유는 은행명을 붙이는 규칙이 여기서만 옳기 때문이다 —
     * «신한 이마트» 는 이름을 더 나쁘게 만들고, «신한은행 최재호» 는 더 낫게 만든다.
     */
    private val TRANSFER_WORDS = listOf("이체", "송금", "입금", "출금", "인출", "보냈", "받았")

    /**
     * 흔한 한국 성(姓). 2~4글자에 이 성으로 시작하면 사람 이름으로 본다.
     *
     * 길이만으로 가르면 «이마트»(이)·«정관장»(정)처럼 성으로 시작하는 상호가 전부 사람이 된다.
     * 그래서 [TRANSFER_WORDS] 가 함께 있을 때만 이 판정을 쓴다.
     */
    private val SURNAMES = setOf(
        "김", "이", "박", "최", "정", "강", "조", "윤", "장", "임", "한", "오", "서", "신",
        "권", "황", "안", "송", "류", "유", "홍", "전", "고", "문", "손", "양", "배", "백",
        "허", "남", "심", "노", "하", "곽", "성", "차", "주", "우", "구", "원", "천", "방",
        "공", "현", "함", "변", "염", "여", "추", "도", "소", "석", "선", "설", "마", "길",
        "연", "위", "표", "명", "기", "반", "왕", "금", "옥", "육", "인", "맹", "제", "모",
        "탁", "국", "진", "지", "엄", "채", "봉", "피", "두",
    )

    /**
     * 이 글자로 끝나면 사람 이름이 아니다. «임대료»(임)·«관리비»(관)처럼 성으로 시작하는
     * 적요를 사람으로 오인하지 않기 위한 안전장치다.
     *
     * «원» 은 일부러 뺐다 — «지원»·«서원» 같은 이름이 흔해서 넣으면 더 많이 틀린다.
     */
    private val NOT_PERSON_TAILS = listOf("료", "비", "세", "금", "값", "점", "실", "회")

    /** 발신번호·계좌번호처럼 숫자와 기호뿐인 이름. 제목이 이 꼴이면 이름 노릇을 못 한다. */
    private val NUMERIC_NAME = Regex("^[0-9][0-9\\-+ ()]*$")

    /** 한글 음절 또는 마스킹 «*» 한 글자인가. «홍*동» 같은 가린 이름도 사람으로 본다. */
    private fun isNameChar(c: Char): Boolean = c == '*' || (c in '\uAC00'..'\uD7A3')

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
        val issuer: String = readIssuer(body)
        val merchant: String = nameFor(readMerchant(nameSource), issuer, body)
        val category: String = CategoryClassifier.classify(merchant, categories).orEmpty()
        return PaymentCandidate(
            amount = amount,
            merchant = merchant,
            category = category,
            issuer = issuer,
        )
    }

    /**
     * 수집함에 보일 이름을 정한다. 규칙 셋이고, 위에서부터 먼저 맞는 것을 쓴다.
     *
     * 1. 가맹점을 못 읽었으면 **은행·카드사 이름이라도** 쓴다. 빈칸보다는 «카카오뱅크» 가 낫다 —
     *    적어도 어디서 나간 돈인지는 알 수 있고, 이름 칸을 고칠 때 기억을 되짚을 단서가 된다.
     * 2. 계좌 이체이고 상대가 사람 이름이면 **«은행명 사람이름»** 으로 붙인다. 사람 이름만 남으면
     *    며칠 뒤에 어느 계좌에서 나갔는지 알 수 없다.
     * 3. 그 밖에는 읽은 그대로 둔다. 카드 승인에 은행명을 붙이면(«신한 이마트») 되레 나빠진다.
     */
    fun nameFor(merchant: String, issuer: String, body: String): String {
        if (merchant.isBlank()) return issuer
        if (issuer.isEmpty()) return merchant
        if (merchant.startsWith(issuer)) return merchant
        if (!isTransfer(body)) return merchant
        if (!looksLikePerson(merchant)) return merchant
        return issuer + " " + merchant
    }

    /** 내용에 나오는 은행·카드사·페이 이름. 없으면 빈 문자열. 긴 이름이 먼저 걸린다. */
    fun readIssuer(body: String): String {
        for (issuer in ISSUERS) {
            if (body.contains(issuer, ignoreCase = true)) return issuer
        }
        return ""
    }

    /** 카드 승인이 아니라 계좌 이체·입출금인가. 은행명을 이름 앞에 붙일지 가르는 조건이다. */
    fun isTransfer(body: String): Boolean = TRANSFER_WORDS.any { body.contains(it) }

    /** 사람 이름으로 보이는가. 2~4글자 한글(마스킹 «*» 포함)이고 흔한 성으로 시작해야 한다. */
    fun looksLikePerson(name: String): Boolean {
        if (name.length !in 2..4) return false
        if (!name.all { isNameChar(it) }) return false
        if (name.take(1) !in SURNAMES) return false
        return NOT_PERSON_TAILS.none { name.endsWith(it) }
    }

    /** 발신번호·계좌번호처럼 숫자뿐이라 이름 노릇을 못 하는 문자열인가. */
    fun looksLikeNumber(text: String): Boolean = NUMERIC_NAME.matches(text.trim())

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
        // 은행·카드사 이름 자체는 가맹점이 아니다. 이름을 못 읽었을 때 [nameFor] 가
        // 마지막 수단으로 쓸 뿐, 진짜 가맹점을 밀어내서는 안 된다.
        if (ISSUERS.any { token.equals(it, ignoreCase = true) }) return null

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
