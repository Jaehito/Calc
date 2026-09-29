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
     * 마지막에 나오는 숫자 토큰을 금액으로, 나머지를 이름으로 본다.
     * "커피 4500", "점심 김밥 6000", "4500 커피", "택시 12,000원" 모두 처리한다.
     *
     * 금액만 적었으면("4500") [fallbackName] 을 이름으로 쓴다 — 기록 창은 고른 카테고리 이름을,
     * 알림 답장은 «미분류»를 넘긴다. [fallbackName] 이 없으면 예전처럼 이름을 적으라고 한다.
     */
    fun parse(raw: String, fallbackName: String? = null): ParseResult {
        val tokens: List<IntRange> = tokenRanges(raw)
        if (tokens.isEmpty()) return ParseResult.Err(tr("아무것도 적지 않았어요", "Nothing entered", "No hay nada escrito"))

        val amountIndex: Int = lastAmountIndex(raw, tokens)
        if (amountIndex < 0) return ParseResult.Err(tr("금액을 못 찾았어요", "No amount found", "No se encontró el importe"))
        val amount: Long = parseAmount(raw.substring(tokens[amountIndex])) ?: 0L

        if (amount <= 0) return ParseResult.Err(tr("금액은 0보다 커야 해요", "The amount must be greater than 0", "El importe debe ser mayor que 0"))
        if (amount > MAX_AMOUNT) return ParseResult.Err(tr("금액이 너무 커요", "The amount is too large", "El importe es demasiado grande"))

        val name: String = tokens.filterIndexed { i, _ -> i != amountIndex }
            .joinToString(" ") { raw.substring(it) }
        if (name.isNotBlank()) return ParseResult.Ok(Expense(name, amount))

        val fallback: String = fallbackName?.trim().orEmpty()
        if (fallback.isEmpty()) return ParseResult.Err(tr("무엇에 썼는지 적어 주세요", "The expense has no name", "Falta el nombre del gasto"))
        return ParseResult.Ok(Expense(fallback, amount), named = false)
    }

    /**
     * [parse] 가 금액으로 볼 낱말이 [raw] 의 어디에 있는지. 기록 창이 그 부분만 초록 알약으로 그린다.
     * 금액으로 볼 낱말이 없으면 null. 0원·너무 큰 금액도 [parse] 가 거절하므로 null 이다.
     */
    fun amountRange(raw: String): IntRange? {
        val tokens: List<IntRange> = tokenRanges(raw)
        val index: Int = lastAmountIndex(raw, tokens)
        if (index < 0) return null
        val amount: Long = parseAmount(raw.substring(tokens[index])) ?: return null
        return if (amount in 1..MAX_AMOUNT) tokens[index] else null
    }

    /** 뒤에서부터 보아 처음 금액으로 읽히는 낱말의 순번. 없으면 -1. */
    private fun lastAmountIndex(raw: String, tokens: List<IntRange>): Int {
        for (i in tokens.indices.reversed()) {
            if (parseAmount(raw.substring(tokens[i])) != null) return i
        }
        return -1
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
