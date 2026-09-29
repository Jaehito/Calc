package com.calc.expense

/** 카테고리 한 칸의 몫. [percent] 는 0~100 정수 (막대 길이·표시용). */
data class CategorySlice(val name: String, val amount: Long, val percent: Int)

/**
 * 통계 목록의 한 줄. 큰 카테고리는 그대로 한 줄이고, 작은 것들은 «나머지» 한 줄로 묶인다.
 *
 * @param name 카테고리 이름. «나머지» 줄이면 null
 * @param restNames «나머지» 줄에 묶인 카테고리 이름들(큰 것부터). 보통 줄이면 비어 있다
 */
data class CategoryGroup(
    val name: String?,
    val amount: Long,
    val percent: Int,
    val restNames: List<String> = emptyList(),
) {
    val isRest: Boolean get() = name == null
}

/**
 * 카테고리별 지출을 막대·퍼센트로 옮긴다. 통계 탭이 저장소에서 읽어 온 «카테고리 → 합계»를 받는다.
 *
 * Android 에 의존하지 않아 단위 테스트로 고정한다.
 */
object CategoryBreakdown {

    /**
     * 카테고리 없이 적은 돈을 묶는 이름. 저장된 값은 빈 문자열이고 이건 **보여줄 때의 이름**이다.
     * 도넛·리포트·펼치기 화면이 같은 말을 써야 해서 여기서만 정한다.
     */
    const val UNCATEGORIZED = "미분류"

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
            val name: String = rawName.trim().ifBlank { UNCATEGORIZED }
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

    /**
     * 큰 것 [keep] 개만 따로 두고 나머지는 한 줄로 묶는다.
     *
     * 색이 8가지뿐이라 카테고리가 많으면 같은 색이 두 번 나오고, 1~2% 짜리 줄이 목록을 길게
     * 늘인다. 묶을 게 하나뿐이면 묶지 않는다 — «나머지 1개»는 그 카테고리를 그냥 보여주는 것만 못하다.
     */
    fun topWithRest(slices: List<CategorySlice>, keep: Int = 5): List<CategoryGroup> {
        if (slices.size <= keep + 1) return slices.map { CategoryGroup(it.name, it.amount, it.percent) }
        val total: Long = slices.sumOf { it.amount }
        val top: List<CategoryGroup> = slices.take(keep).map { CategoryGroup(it.name, it.amount, it.percent) }
        val rest: List<CategorySlice> = slices.drop(keep)
        val restAmount: Long = rest.sumOf { it.amount }
        val restPercent: Int = if (total > 0L) Math.round(restAmount * 100.0 / total).toInt() else 0
        return top + CategoryGroup(null, restAmount, restPercent, rest.map { it.name })
    }

    /** 전체 합계. 통계 상단에 «이번 달 얼마»로 쓴다. */
    fun total(totals: Map<String, Long>): Long =
        totals.values.filter { it > 0L }.sum()
}
