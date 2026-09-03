package com.calc.expense

import android.content.Context

/**
 * 3단계 — 노션에 이미 있는 지출을 Firestore로 1회 복사한다.
 *
 * [FirestoreExpenseStore.add] 를 그대로 재사용한다 — 문서 id 를 노션 페이지 id 로 쓰는
 * 규칙, 공용 곳간이 가정으로 안 묶였으면 건너뛰는 규칙이 이미 거기 있다. 여기서 새로
 * 정의하지 않는다.
 *
 * fire-and-forget 이라 이 함수가 끝난 시점에 Firestore 쓰기가 전부 끝났다는 보장은
 * 없다 — 개수는 "시도한 건수"다. 완료 여부는 며칠 뒤 Firestore 콘솔이나 화면 숫자
 * 대조로 확인한다(사용자 몫).
 */
object FirestoreBackfill {

    data class Result(val ok: Boolean, val attempted: Int = 0, val message: String = "")

    /** 반드시 백그라운드 스레드에서 부른다. 곳간마다 노션을 한 번씩 왕복해 수 초 걸릴 수 있다. */
    fun run(context: Context): Result {
        val settings = SettingsStore.load(context)
        val linked: List<Purse> = settings.linkedPurses
        if (settings.token.isBlank() || linked.isEmpty()) {
            return Result(ok = false, message = "설정이 비어 있습니다. 먼저 토큰과 DB를 입력하세요")
        }

        var attempted = 0
        for (purse in linked) {
            val target: NotionTarget = settings.target(purse) ?: continue

            when (val outcome = NotionClient(target).queryAllRows()) {
                is NotionClient.RowsOutcome.Err ->
                    return Result(
                        ok = false,
                        attempted = attempted,
                        message = "${settings.labelOf(purse)} 곳간: ${outcome.message}",
                    )
                is NotionClient.RowsOutcome.Ok -> {
                    for (row in outcome.rows) {
                        if (row.id.isBlank()) continue
                        val expense = Expense(name = row.name, amount = row.amount, category = row.category)
                        FirestoreExpenseStore.add(context, purse, row.id, expense, row.date)
                        attempted++
                    }
                }
            }
        }
        return Result(ok = true, attempted = attempted)
    }
}
