package com.calc.expense

import android.content.Context
import java.time.YearMonth

/**
 * 내역 화면이 지출을 읽어 오는 **유일한 진입점(seam)**.
 *
 * 저장소를 바꾸면 **이 파일 하나만** 고치면 된다 — 화면([HistoryScreen])과 묶기 로직
 * ([ExpenseHistoryGrouping])은 [DayGroup] 만 받으므로 그대로 산다. 노션에서 Firestore 로
 * 옮길 때 실제로 그렇게 됐다.
 *
 * 저장소를 읽으므로 반드시 백그라운드 스레드에서 부른다.
 */
object ExpenseHistory {

    sealed class Result {
        data class Ok(val groups: List<DayGroup>, val total: Long) : Result()
        data class Err(val message: String) : Result()
    }

    fun load(context: Context, purse: Purse, month: YearMonth): Result {
        val rows: List<ExpenseRow> = FirestoreExpenseReader.monthRows(context, purse, month)
            ?: return Result.Err("${SettingsStore.load(context).labelOf(purse)} 곳간을 불러오지 못했습니다")

        return Result.Ok(ExpenseHistoryGrouping.groupByDay(rows), ExpenseHistoryGrouping.total(rows))
    }
}
