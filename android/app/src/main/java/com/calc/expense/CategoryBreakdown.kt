package com.calc.expense

/** 카테고리 한 칸의 몫. [percent] 는 0~100 정수 (막대 길이·표시용). */
data class CategorySlice(val name: String, val amount: Long, val percent: Int)

/**
 * 카테고리별 지출을 막대·퍼센트로 옮긴다. 통계 탭이 노션에서 읽어 온 «카테고리 → 합계»를 받는다.
 *
 * Android 에 의존하지 않아 단위 테스트로 고정한다.
 */
object CategoryBreakdown {

    /**
     * 큰 것부터 정렬한 카테고리 목록. 퍼센트는 반올림해 합이 100 근처가 되지만 정확히 100은
     * 아닐 수 있다(반올림 오차) — 막대 길이는 이걸로 충분하고, 숫자는 원값을 함께 보여준다.
     *
     * 이름이 빈 행(카테고리 미지정)은 «미분류»로 묶는다.
     */
    fun of(totals: Map<String, Long>): List<CategorySlice> {
        val merged = LinkedHashMap<String, Long>()
        for ((rawName, amount) in totals) {
            if (amount <= 0L) continue
            val name: String = rawName.trim().ifBlank { "미분류" }
            merged[name] = (merged[name] ?: 0L) + amount
        }

        val total: Long = merged.values.sum()
        if (total <= 0L) return emptyList()

        return merged.entries
            .sortedByDescending { it.value }
            .map { (name, amount) ->
                CategorySlice(name, amount, percent = Math.round(amount * 100.0 / total).toInt())
            }
    }

    /** 전체 합계. 통계 상단에 «이번 달 얼마»로 쓴다. */
    fun total(totals: Map<String, Long>): Long =
        totals.values.filter { it > 0L }.sum()
}
