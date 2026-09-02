package com.calc.expense

/**
 * 잠금화면에서 입력받은 한 줄을 지출 항목으로 해석한다.
 *
 * [category] 는 파싱이 아니라 칩 선택으로 채운다(선택 사항). 비어 있으면 카테고리 없이 기록한다.
 */
data class Expense(val name: String, val amount: Long, val category: String = "")

sealed class ParseResult {
    data class Ok(val expense: Expense) : ParseResult()
    data class Err(val message: String) : ParseResult()
}

object ExpenseParser {

    /** "4천" = 4000, "1.5만" = 15000 처럼 뒤에 붙는 한글 단위. 긴 것부터 검사. */
    private val UNITS = listOf("만" to 10_000L, "천" to 1_000L)

    private const val MAX_AMOUNT = 1_000_000_000L

    /**
     * 마지막에 나오는 숫자 토큰을 금액으로, 나머지를 이름으로 본다.
     * "커피 4500", "점심 김밥 6000", "4500 커피", "택시 12,000원" 모두 처리한다.
     */
    fun parse(raw: String): ParseResult {
        val tokens = raw.trim().split(' ', '\t', '\n', ' ').filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return ParseResult.Err("내용이 비어 있습니다")

        var amountIndex = -1
        var amount = 0L
        for (i in tokens.indices.reversed()) {
            val parsed = parseAmount(tokens[i])
            if (parsed != null) {
                amountIndex = i
                amount = parsed
                break
            }
        }

        if (amountIndex < 0) return ParseResult.Err("금액을 찾을 수 없습니다")
        if (amount <= 0) return ParseResult.Err("금액은 0보다 커야 합니다")
        if (amount > MAX_AMOUNT) return ParseResult.Err("금액이 너무 큽니다")

        val name = tokens.filterIndexed { i, _ -> i != amountIndex }.joinToString(" ")
        if (name.isBlank()) return ParseResult.Err("지출 이름이 없습니다")

        return ParseResult.Ok(Expense(name, amount))
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
