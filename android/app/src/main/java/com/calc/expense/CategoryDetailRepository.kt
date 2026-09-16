package com.calc.expense

import android.content.Context
import java.time.LocalDate

/**
 * 한 카테고리를 이름으로 펼치고, 그 이름 전체를 다른 카테고리로 옮긴다.
 *
 * 저장소를 읽고 쓰므로 반드시 백그라운드 스레드에서 부른다.
 */
object CategoryDetailRepository {

    /**
     * [from]~[to] 사이에서 [category] 에 속한 줄들을 이름으로 묶는다.
     *
     * 곳간 하나라도 읽지 못하면 실패로 본다 — 반쪽만 보여주면 사용자가 «이게 전부»로 읽고
     * 나머지를 영영 분류하지 않는다([StatsRepository.fetchCategories] 와 같은 판단).
     */
    fun load(
        context: Context,
        from: LocalDate,
        to: LocalDate,
        category: String,
    ): CategoryDetail {
        val rows = ArrayList<PursedRow>()
        for (purse in PurseAccess.linked(context)) {
            val read: List<ExpenseRow> = FirestoreExpenseReader.rowsBetween(context, purse, from, to)
                ?: return CategoryDetail(
                    category = category,
                    groups = emptyList(),
                    error = "불러오지 못했습니다. 잠시 뒤 다시 열어 주세요",
                )
            for (row in read) rows.add(PursedRow(purse, row))
        }
        return CategoryDetails.of(rows, category)
    }

    /**
     * 한 이름으로 묶인 줄 전체를 [category] 로 옮긴다. 옮긴 줄 수를 돌려준다.
     *
     * **이름도 함께 기억한다.** 그래야 다음 달에 같은 이름이 오면 다시 묻지 않는다 —
     * 과거를 고치는 일이 미래를 고치는 일이기도 해야 이 화면을 한 번만 쓰게 된다.
     *
     * 실패한 줄은 세지 않는다. 한 줄이 실패해도 나머지는 계속 옮긴다 — 곳간 하나가 풀려
     * 있다고 나머지 분류까지 막을 이유가 없다.
     */
    fun assign(context: Context, group: NameGroup, category: String): Int {
        var moved = 0
        for (item in group.rows) {
            val outcome: FirestoreExpenseStore.Outcome =
                FirestoreExpenseStore.updateCategory(context, item.purse, item.row.id, category)
            if (outcome is FirestoreExpenseStore.Outcome.Ok) moved++
        }

        if (moved > 0 && category.isNotBlank()) {
            CategoryMemoryStore.remember(context, group.name, category)
        }
        return moved
    }
}
