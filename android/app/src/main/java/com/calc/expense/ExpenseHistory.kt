package com.calc.expense

import android.content.Context
import java.time.YearMonth

/**
 * 내역 화면이 지출을 읽어 오는 **유일한 진입점(seam)**.
 *
 * 지금은 노션에서 읽지만, 나중에 지출 저장소를 다른 DB 로 옮기면 **이 파일 하나만** 바꾸면 된다.
 * 화면([HistoryScreen])과 묶기 로직([ExpenseHistoryGrouping])은 [DayGroup] 만 받으므로 그대로 산다.
 *
 * 네트워크를 타므로 반드시 백그라운드 스레드에서 부른다.
 */
object ExpenseHistory {

    sealed class Result {
        data class Ok(val groups: List<DayGroup>, val total: Long) : Result()
        data class Err(val message: String) : Result()
    }

    fun load(context: Context, purse: Purse, month: YearMonth): Result {
        if (FirestoreReadMode.isEnabled(context)) {
            val rows: List<ExpenseRow>? = FirestoreExpenseReader.monthRows(context, purse, month)
            if (rows != null) {
                return Result.Ok(ExpenseHistoryGrouping.groupByDay(rows), ExpenseHistoryGrouping.total(rows))
            }
            // Firestore 읽기 실패 — 아래에서 노션으로 폴백한다.
        }

        val settings: Settings = SettingsStore.load(context)
        val target: NotionTarget = settings.target(purse)
            ?: return Result.Err("${settings.labelOf(purse)} 곳간에 DB가 연결되지 않았습니다")

        return when (val r = NotionClient(target).queryMonthRows(month)) {
            is NotionClient.RowsOutcome.Ok ->
                Result.Ok(ExpenseHistoryGrouping.groupByDay(r.rows), ExpenseHistoryGrouping.total(r.rows))
            is NotionClient.RowsOutcome.Err -> Result.Err(r.message)
        }
    }
}
