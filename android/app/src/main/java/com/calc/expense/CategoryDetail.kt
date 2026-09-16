package com.calc.expense

/** 어느 곳간의 줄인지 함께 든 지출 한 줄. 되돌려 쓸 때 곳간을 알아야 한다. */
data class PursedRow(val purse: Purse, val row: ExpenseRow)

/**
 * 한 카테고리 안에서 **같은 이름끼리 묶은 한 덩어리.**
 *
 * 이것이 이 앱의 «하위 카테고리»다. 카테고리를 두 층으로 만드는 대신 이미 있는 이름으로
 * 파고든다 — 「식비 > 외식」보다 「식비 > 우아한형제들 312,000원」이 더 많은 것을 말해 준다.
 *
 * @param rows 이 이름으로 묶인 줄들. 다시 분류할 때 이 줄을 전부 고친다
 */
data class NameGroup(
    val name: String,
    val total: Long,
    val rows: List<PursedRow>,
) {
    val count: Int
        get() = rows.size
}

/**
 * 한 카테고리를 이름으로 펼친 결과.
 *
 * @param category 빈 문자열이면 «미분류»다 — 카테고리 없이 적은 줄들이 여기 모인다
 */
data class CategoryDetail(
    val category: String,
    val groups: List<NameGroup>,
    val loading: Boolean = false,
    val error: String? = null,
) {
    val total: Long
        get() = groups.sumOf { it.total }

    val count: Int
        get() = groups.sumOf { it.count }

    /** 화면에 보일 카테고리 이름. [CategoryBreakdown] 이 도넛에 쓰는 말과 같아야 한다. */
    val label: String
        get() = category.ifBlank { "미분류" }

    val isEmpty: Boolean
        get() = groups.isEmpty()
}

/**
 * 이름으로 묶는 규칙. Android 에 의존하지 않아 단위 테스트로 고정한다.
 */
object CategoryDetails {

    /**
     * [rows] 중 [category] 에 속한 것만 골라 이름으로 묶는다. 큰 금액부터.
     *
     * 이름 견주기는 [RecurringCosts.normalize] 를 쓴다(띄어쓰기만 무시) — 고정비 찾기가
     * 「같은 곳인가」를 판단하는 것과 같은 잣대라야, 화면에서 한 줄로 보이는 것이 고정비
     * 후보로도 한 줄로 잡힌다. 보여 주는 이름은 **가장 최근에 적은 것**을 쓴다.
     */
    fun of(rows: List<PursedRow>, category: String): CategoryDetail {
        val mine: List<PursedRow> = rows.filter { it.row.category.trim() == category.trim() }
        val byKey = LinkedHashMap<String, MutableList<PursedRow>>()
        for (item in mine) {
            val key: String = RecurringCosts.normalize(item.row.name).ifEmpty { "?" }
            byKey.getOrPut(key) { ArrayList() }.add(item)
        }

        val groups: List<NameGroup> = byKey.values.map { list ->
            NameGroup(
                name = displayName(list),
                total = list.sumOf { it.row.amount },
                rows = list.sortedByDescending { it.row.date },
            )
        }

        return CategoryDetail(category = category, groups = groups.sortedByDescending { it.total })
    }

    /** 한 덩어리를 대표하는 이름. 가장 최근에 적은 것 — 이름을 고쳤으면 새 이름이 보여야 한다. */
    private fun displayName(list: List<PursedRow>): String {
        val latest: PursedRow? = list.maxByOrNull { it.row.date }
        return (latest ?: list.first()).row.name.trim().ifEmpty { "이름 없음" }
    }
}
