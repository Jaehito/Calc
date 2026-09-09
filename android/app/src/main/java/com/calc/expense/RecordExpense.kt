package com.calc.expense

import android.content.Context
import java.time.LocalDate

/**
 * 기록 한 번의 결과.
 *
 * @param lines 알림이 그대로 쓰는 문구
 * @param expense 성공했을 때 무엇이 들어갔는지. 입력 화면이 결과 줄을 만들 때 쓴다
 */
data class RecordResult(
    val ok: Boolean,
    val lines: StatusLines,
    val expense: Expense? = null,
    /** 성공했을 때 만들어진 지출 줄의 id. 이 줄만 지울 때 쓴다. 실패면 빈 문자열. */
    val rowId: String = "",
)

/** 항목 하나를 지운 결과. 성공하면 [ok] 가 true 이고, 실패하면 [message] 에 이유가 있다. */
data class DeleteResult(val ok: Boolean, val message: String = "")

/** 항목 하나를 고친 결과. 성공하면 [ok] 가 true 이고, 실패하면 [message] 에 이유가 있다. */
data class EditResult(val ok: Boolean, val message: String = "")

/**
 * 지출 한 건을 기록하는 유일한 경로.
 *
 * 잠금화면 인라인 답장과 빠른 입력 화면이 같은 함수를 쓴다. 두 곳에 같은 순서를 적어 두면
 * 한쪽만 고쳐져 캐시와 저장소가 어긋난다.
 *
 * [FirestoreExpenseStore] 가 네트워크를 기다리지 않으므로 이 경로는 이제 네트워크를 타지
 * 않는다. 그래도 저장소 호출이 걸릴 수 있어 백그라운드 스레드에서 부른다.
 */
object RecordExpense {

    fun submit(
        context: Context,
        text: String,
        purseKey: String?,
        now: String,
        today: LocalDate = LocalDate.now(),
        category: String = "",
    ): RecordResult {
        val parsed = when (val r = ExpenseParser.parse(text)) {
            is ParseResult.Err ->
                return fail("${r.message} · 입력: \"${text.trim()}\"", now)
            is ParseResult.Ok -> r.expense.copy(category = category.trim())
        }
        return record(context, parsed, purseKey, now, today)
    }

    /**
     * 이미 이름·금액·카테고리가 정해진 지출 한 건을 기록한다.
     *
     * [submit] 은 «커피 4500» 같은 한 줄을 파싱해 여기로 넘긴다. 수집함([PendingPayment])처럼
     * 값이 이미 나뉘어 있는 경로는 문자열로 되돌렸다가 다시 파싱하지 않고 곧장 이리로 온다 —
     * 저장·캐시·리마인더·수집함 정리의 순서는 여기 한 곳에만 있다.
     */
    fun record(
        context: Context,
        parsed: Expense,
        purseKey: String?,
        now: String,
        today: LocalDate = LocalDate.now(),
    ): RecordResult {
        val linked: List<Purse> = PurseAccess.linked(context)
        if (linked.isEmpty()) {
            return fail("로그인이 풀렸습니다. 앱을 열어 다시 로그인해 주세요", now)
        }

        // 어느 곳간인지는 부른 쪽이 실어 보낸다. 값이 없으면 첫 곳간으로 본다.
        val purse: Purse = linked.firstOrNull { it.key == purseKey } ?: linked.first()

        return when (val r = FirestoreExpenseStore.add(context, purse, parsed, today)) {
            is FirestoreExpenseStore.Outcome.Err -> fail(r.message, now)
            is FirestoreExpenseStore.Outcome.Ok -> {
                // 저장이 받아들여진 뒤에만 로컬 사본에 더한다. 실패한 기록을 세면 숫자가 거짓말을 한다.
                Ledger.record(context, purse, today, parsed.amount)
                // 기록이 있었으니 결제 리마인더는 이 뒤로 보내지 않는다.
                val at: Long = System.currentTimeMillis()
                ReminderState.markRecorded(context, at)
                // 방금 적은 것과 같은 결제로 보이는 수집함 후보를 치운다 — 같은 걸 두 번 묻지 않는다.
                PendingPaymentStore.removeRecorded(context, parsed.amount, at)
                // 저장소를 다시 읽지 않는다 — 로컬 사본만으로 계산하고, 대조는 앱을 열 때 한다.
                RecordResult(
                    ok = true,
                    expense = parsed,
                    rowId = r.id,
                    lines = StatusText.recorded(
                        name = parsed.name,
                        amount = parsed.amount,
                        snapshot = Ledger.snapshot(context, purse, today),
                        time = now,
                        showPurse = linked.size > 1,
                    ),
                )
            }
        }
    }

    /**
     * 방금 적은 항목 하나를 지운다. 저장소에서 지우고 로컬 사본에서도 뺀다.
     *
     * 순서가 중요하다 — 저장소 삭제가 받아들여진 뒤에만 로컬에서 뺀다. 반대로 하면 삭제가
     * 거절됐는데 숫자만 되돌아가 캐시와 저장소가 어긋난다. 백그라운드 스레드에서 부른다.
     */
    fun delete(
        context: Context,
        rowId: String,
        purseKey: String,
        day: LocalDate,
        amount: Long,
    ): DeleteResult {
        val purse: Purse = PurseAccess.linked(context).firstOrNull { it.key == purseKey }
            ?: return DeleteResult(ok = false, message = "곳간을 찾을 수 없습니다")

        return when (val r = FirestoreExpenseStore.archive(context, purse, rowId)) {
            is FirestoreExpenseStore.Outcome.Err -> DeleteResult(ok = false, message = r.message)
            is FirestoreExpenseStore.Outcome.Ok -> {
                Ledger.unrecord(context, purse, day, amount)
                DeleteResult(ok = true)
            }
        }
    }

    /**
     * 옛 기록 한 줄을 고친다. 내역 화면(과거 기록)에서 이름·금액·카테고리를 바꿀 때 쓴다.
     *
     * **새 줄을 먼저 만들고, 성공하면 옛 줄을 지운다.** 순서가 중요하다 — 새 줄 만들기가
     * 실패하면 옛 줄이 그대로 남아 데이터가 사라지지 않는다. (노션 시절에는 PATCH 를 보낼 수
     * 없어 어쩔 수 없이 이 순서였는데, Firestore 로 옮긴 뒤에도 이 순서가 더 안전해서 남긴다.)
     *
     * 백그라운드 스레드에서 부른다.
     */
    fun edit(
        context: Context,
        purse: Purse,
        day: LocalDate,
        oldRowId: String,
        oldAmount: Long,
        newExpense: Expense,
    ): EditResult {
        return when (val created = FirestoreExpenseStore.add(context, purse, newExpense, day)) {
            is FirestoreExpenseStore.Outcome.Err -> EditResult(ok = false, message = created.message)
            is FirestoreExpenseStore.Outcome.Ok -> {
                // 새 줄이 실제로 생겼으니 캐시에도 바로 반영한다.
                Ledger.record(context, purse, day, newExpense.amount)

                when (val archived = FirestoreExpenseStore.archive(context, purse, oldRowId)) {
                    is FirestoreExpenseStore.Outcome.Err -> EditResult(
                        ok = false,
                        message = "새 값은 저장됐지만 옛 줄을 지우지 못했습니다: ${archived.message}",
                    )
                    is FirestoreExpenseStore.Outcome.Ok -> {
                        Ledger.unrecord(context, purse, day, oldAmount)
                        EditResult(ok = true)
                    }
                }
            }
        }
    }

    private fun fail(message: String, now: String): RecordResult =
        RecordResult(ok = false, lines = StatusText.failed(message, now))
}
