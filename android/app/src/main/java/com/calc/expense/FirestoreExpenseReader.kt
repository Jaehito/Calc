package com.calc.expense

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.QuerySnapshot
import java.time.LocalDate
import java.time.YearMonth
import java.util.concurrent.TimeUnit

/**
 * 그 달의 지출 행을 읽는다. [FirestoreExpenseStore.add] 가 쓴 것과 같은 문서 모양
 * (name·amount·category·date)을 그대로 읽는다.
 *
 * 읽다가 실패하면(권한·네트워크·로그인 풀림) null 을 돌려준다. 빈 목록과 실패는 다르다 —
 * 실패를 빈 목록으로 돌려주면 부른 쪽이 «그 달에 안 썼다»로 읽어 캐시를 0 으로 덮어쓴다.
 * 부른 쪽([Ledger]·[StatsRepository]·[ExpenseHistory])은 null 을 오류로 보고한다.
 *
 * 반드시 백그라운드 스레드에서 부른다 — 호출부 세 곳이 전부 동기 함수라 [Tasks.await] 로
 * 블로킹 모양을 맞춘다.
 */
object FirestoreExpenseReader {

    private const val TIMEOUT_SECONDS = 8L

    fun monthRows(context: Context, purse: Purse, month: YearMonth): List<ExpenseRow>? {
        val collection: CollectionReference = FirestoreExpenseStore.collectionFor(context, purse) ?: return null
        val first: LocalDate = month.atDay(1)
        val last: LocalDate = month.atEndOfMonth()

        return try {
            val snapshot: QuerySnapshot = Tasks.await(
                collection
                    .whereGreaterThanOrEqualTo("date", first.toString())
                    .whereLessThanOrEqualTo("date", last.toString())
                    .get(),
                TIMEOUT_SECONDS,
                TimeUnit.SECONDS,
            )
            snapshot.documents.mapNotNull { doc ->
                val name: String = doc.getString("name") ?: return@mapNotNull null
                val amount: Long = doc.getLong("amount") ?: return@mapNotNull null
                val dateText: String = doc.getString("date") ?: return@mapNotNull null
                val day: LocalDate = try {
                    LocalDate.parse(dateText)
                } catch (_: Exception) {
                    return@mapNotNull null
                }
                ExpenseRow(
                    id = doc.id,
                    name = name,
                    amount = amount,
                    date = day,
                    category = doc.getString("category").orEmpty(),
                )
            }
        } catch (_: Exception) {
            null
        }
    }
}
