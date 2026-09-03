package com.calc.expense

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.QuerySnapshot
import java.time.LocalDate
import java.time.YearMonth
import java.util.concurrent.TimeUnit

/**
 * 3단계 읽기 전환 — Firestore에서 그 달의 지출 행을 읽는다. [FirestoreExpenseStore.add] 가
 * 쓴 것과 같은 문서 모양(name·amount·category·date)을 그대로 읽는다.
 *
 * [FirestoreReadMode] 가 꺼져 있거나, 읽다가 실패하거나(권한·네트워크·백필 전이라 비어
 * 있는 등), 결과가 미심쩍으면 null을 돌려준다 — 부른 쪽([Ledger]·[StatsRepository]·
 * [ExpenseHistory])이 노션으로 폴백한다. Firestore 사정으로 화면 숫자가 깨지면 안 된다.
 *
 * 반드시 백그라운드 스레드에서 부른다. [NotionClient] 와 같은 동기(블로킹) 모양을
 * 맞추려고 [Tasks.await] 를 쓴다 — 호출부 세 곳이 전부 이미 동기 함수라서.
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
