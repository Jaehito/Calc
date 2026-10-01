package com.calc.expense

/**
 * 잠금화면에서 입력받은 한 줄을 지출 항목으로 해석한다.
 *
 * [category] 는 파싱이 아니라 칩 선택으로 채운다(선택 사항). 비어 있으면 카테고리 없이 기록한다.
 */
data class Expense(val name: String, val amount: Long, val category: String = "")

sealed class ParseResult {
    /** [named] 가 false 면 이름을 적지 않아 [ExpenseParser.parse] 의 대체 이름을 썼다는 뜻이다. */
    data class Ok(val expense: Expense, val named: Boolean = true) : ParseResult()
    data class Err(val message: String) : ParseResult()
}

object ExpenseParser {

    /** "4천" = 4000, "1.5만" = 15000 처럼 뒤에 붙는 한글 단위. 긴 것부터 검사. */
    private val UNITS = listOf("만" to 10_000L, "천" to 1_000L)

    private const val MAX_AMOUNT = 1_000_000_000L

    /** 낱말 사이를 가르는 문자. 한국어 키보드가 넣는 줄바꿈 없는 공백(U+00A0)도 포함한다. */
    private val SEPARATORS = charArrayOf(' ', '\t', '\n', '\u00A0')

    /**
     * 숫자 덩어리 하나. 앞의 ₩, 쉼표·소수점, 뒤에 붙는 만/천 단위와 «원»까지 한 덩어리다.
     *
     * 단위·«원»은 **뒤에 한글이 이어지지 않을 때만** 붙인다 — «2000만두»의 «만», «4500원두»의 «원»은
     * 금액이 아니라 이름의 첫 글자다.
     */
    private val AMOUNT_RUN = Regex("""₩?\d[\d,]*(?:\.\d+)?(?:[만천](?=원|[^가-힣]|$))?(?:원(?![가-힣]))?""")

    /**
     * 숫자 바로 뒤에 와서 금액이 아니라 «몇 년·몇 잔»을 뜻하게 만드는 말. 이 글자 하나로 낱말이 끝날
     * 때만 본다 — «2000년»은 금액이 아니지만 «4500도시락»의 «도»는 이름의 첫 글자다.
     */
    private const val COUNTERS = "년월일시분초개명번잔인층호박살장병권회차주"

    /**
     * 마지막에 나오는 금액을 금액으로, 나머지를 이름으로 본다.
     * "커피 4500", "점심 김밥 6000", "4500 커피", "택시 12,000원" 모두 처리한다.
     *
     * **띄어 쓰지 않아도 된다.** 숫자와 글자가 맞닿은 자리에서 가른다 — "커피4500", "4500커피",
     * "택시12,000원" 도 같다. 이름은 금액 자리만 빼고 적은 그대로 둔다("아메리카노2잔9000" → 이름
     * "아메리카노2잔").
     *
     * 금액만 적었으면("4500") [fallbackName] 을 이름으로 쓴다 — 기록 창은 고른 카테고리 이름을,
     * 알림 답장은 «미분류»를 넘긴다. [fallbackName] 이 없으면 예전처럼 이름을 적으라고 한다.
     */
    fun parse(raw: String, fallbackName: String? = null): ParseResult {
        if (tokenRanges(raw).isEmpty()) return ParseResult.Err(tr("아무것도 적지 않았어요", "Nothing entered", "No hay nada escrito"))

        val range: IntRange = amountRanges(raw).lastOrNull()
            ?: return ParseResult.Err(tr("금액을 못 찾았어요", "No amount found", "No se encontró el importe"))
        val amount: Long = parseAmount(raw.substring(range)) ?: 0L

        if (amount <= 0) return ParseResult.Err(tr("금액은 0보다 커야 해요", "The amount must be greater than 0", "El importe debe ser mayor que 0"))
        if (amount > MAX_AMOUNT) return ParseResult.Err(tr("금액이 너무 커요", "The amount is too large", "El importe es demasiado grande"))

        val rest: String = raw.substring(0, range.first) + " " + raw.substring(range.last + 1)
        val name: String = tokenRanges(rest).joinToString(" ") { rest.substring(it) }
        if (name.isNotBlank()) return ParseResult.Ok(Expense(name, amount))

        val fallback: String = fallbackName?.trim().orEmpty()
        if (fallback.isEmpty()) return ParseResult.Err(tr("무엇에 썼는지 적어 주세요", "The expense has no name", "Falta el nombre del gasto"))
        return ParseResult.Ok(Expense(fallback, amount), named = false)
    }

    /**
     * [parse] 가 금액으로 볼 부분이 [raw] 의 어디에 있는지. 기록 창이 그 부분만 초록 알약으로 그린다.
     * 금액으로 볼 부분이 없으면 null. 0원·너무 큰 금액도 [parse] 가 거절하므로 null 이다.
     */
    fun amountRange(raw: String): IntRange? {
        val range: IntRange = amountRanges(raw).lastOrNull() ?: return null
        val amount: Long = parseAmount(raw.substring(range)) ?: return null
        return if (amount in 1..MAX_AMOUNT) range else null
    }

    /**
     * 금액으로 읽힐 수 있는 자리들, 앞에서부터.
     *
     * 낱말 전체가 금액이면("4500", "12,000원", "만원") 낱말 전체가 한 자리다. 아니면 낱말 안에서
     * 숫자 덩어리([AMOUNT_RUN])를 찾는다 — 띄어 쓰지 않은 "커피4500" 의 "4500".
     */
    private fun amountRanges(raw: String): List<IntRange> {
        val found = mutableListOf<IntRange>()
        for (token in tokenRanges(raw)) {
            val text: String = raw.substring(token)
            if (parseAmount(text) != null) {
                found.add(token)
                continue
            }
            for (match in AMOUNT_RUN.findAll(text)) {
                if (isCounted(text, match.range.last + 1)) continue
                if (parseAmount(match.value) == null) continue
                found.add((token.first + match.range.first)..(token.first + match.range.last))
            }
        }
        return found
    }

    /** [text] 의 [at] 자리에 [COUNTERS] 글자 하나가 오고 거기서 낱말(한글)이 끝나는가. */
    private fun isCounted(text: String, at: Int): Boolean {
        if (at >= text.length || text[at] !in COUNTERS) return false
        val next: Int = at + 1
        return next >= text.length || text[next] !in '가'..'힣'
    }

    /** [raw] 를 [SEPARATORS] 로 갈라 각 낱말의 위치를 돌려준다. */
    private fun tokenRanges(raw: String): List<IntRange> {
        val ranges = mutableListOf<IntRange>()
        var start = -1
        for (i in raw.indices) {
            if (raw[i] in SEPARATORS) {
                if (start >= 0) ranges.add(start until i)
                start = -1
            } else if (start < 0) {
                start = i
            }
        }
        if (start >= 0) ranges.add(start until raw.length)
        return ranges
    }

    /** 토큰 하나를 금액으로 해석한다. 금액이 아니면 null. */
    fun parseAmount(token: String): Long? {
        var t = token.trim()
            .removePrefix("₩")
            .removeSuffix("원")
            .replace(",", "")
        if (t.isEmpty()) return null

        var unit = 1L
        for ((suffix, value) in UNITS) {
            if (t.endsWith(suffix)) {
                t = t.dropLast(suffix.length)
                unit = value
                break
            }
        }
        // "만" 처럼 숫자 없이 단위만 온 경우 = 10000
        if (t.isEmpty()) return if (unit > 1L) unit else null

        val number = t.toDoubleOrNull() ?: return null
        if (!number.isFinite() || number < 0) return null

        val result = number * unit
        if (result > MAX_AMOUNT) return null
        return Math.round(result)
    }
}
