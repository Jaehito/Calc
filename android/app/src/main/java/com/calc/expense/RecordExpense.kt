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
    /** 성공했을 때 만들어진 Notion 페이지 id. 이 줄만 지울 때 쓴다. 실패면 빈 문자열. */
    val pageId: String = "",
)

/** 항목 하나를 지운 결과. 성공하면 [ok] 가 true 이고, 실패하면 [message] 에 이유가 있다. */
data class DeleteResult(val ok: Boolean, val message: String = "")

/** 항목 하나를 고친 결과. 성공하면 [ok] 가 true 이고, 실패하면 [message] 에 이유가 있다. */
data class EditResult(val ok: Boolean, val message: String = "")

/**
 * 지출 한 건을 기록하는 유일한 경로.
 *
 * 잠금화면 인라인 답장과 빠른 입력 화면이 같은 함수를 쓴다. 두 곳에 같은 순서를 적어 두면
 * 한쪽만 고쳐져 캐시와 Notion 이 어긋난다.
 *
 * 네트워크를 타므로 반드시 백그라운드 스레드에서 부른다.
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

        val settings = SettingsStore.load(context)
        val linked: List<Purse> = settings.linkedPurses
        if (settings.token.isBlank() || linked.isEmpty()) {
            return fail("설정이 비어 있습니다. 앱을 열어 토큰과 DB를 입력하세요", now)
        }

        // 어느 곳간인지는 부른 쪽이 실어 보낸다. 값이 없으면 첫 곳간으로 본다.
        val purse: Purse = linked.firstOrNull { it.key == purseKey } ?: linked.first()
        val target: NotionTarget = settings.target(purse)
            ?: return fail("${settings.labelOf(purse)} 곳간에 DB가 연결되지 않았습니다", now)

        return when (val r = NotionClient(target).addExpense(parsed, today.toString())) {
            is NotionClient.Outcome.Err -> fail(r.message, now)
            is NotionClient.Outcome.Ok -> {
                // Notion 쓰기가 성공한 뒤에만 로컬 사본에 더한다. 실패한 기록을 세면 숫자가 거짓말을 한다.
                Ledger.record(context, purse, today, parsed.amount)
                // Firestore 이중 쓰기 — 실패해도 위 노션 기록엔 영향 없다(안전망일 뿐).
                FirestoreExpenseStore.add(context, purse, r.detail, parsed, today)
                // 기록이 있었으니 결제 리마인더는 이 뒤로 보내지 않는다.
                ReminderState.markRecorded(context, System.currentTimeMillis())
                // 여기서 Notion 을 한 번 더 왕복하지 않는다 — 브로드캐스트 수명 안에 못 끝낸다.
                // 로컬 사본만으로 계산하고, Notion 과의 대조는 앱을 열 때 한다.
                RecordResult(
                    ok = true,
                    expense = parsed,
                    pageId = r.detail,
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
     * 방금 적은 항목 하나를 지운다. Notion 에서 아카이브하고 로컬 사본에서도 뺀다.
     *
     * 순서가 중요하다 — Notion 삭제가 성공한 뒤에만 로컬에서 뺀다. 반대로 하면 삭제가
     * 실패했는데 숫자만 되돌아가 캐시와 Notion 이 어긋난다. 백그라운드 스레드에서 부른다.
     */
    fun delete(
        context: Context,
        pageId: String,
        purseKey: String,
        day: LocalDate,
        amount: Long,
    ): DeleteResult {
        val settings = SettingsStore.load(context)
        val purse: Purse = settings.linkedPurses.firstOrNull { it.key == purseKey }
            ?: return DeleteResult(ok = false, message = "곳간을 찾을 수 없습니다")
        val target: NotionTarget = settings.target(purse)
            ?: return DeleteResult(ok = false, message = "DB가 연결되지 않았습니다")

        return when (val r = NotionClient(target).archivePage(pageId)) {
            is NotionClient.Outcome.Err -> DeleteResult(ok = false, message = r.message)
            is NotionClient.Outcome.Ok -> {
                Ledger.unrecord(context, purse, day, amount)
                FirestoreExpenseStore.archive(context, purse, pageId)
                DeleteResult(ok = true)
            }
        }
    }

    /**
     * 옛 기록 한 줄을 고친다. 내역 화면(과거 기록)에서 이름·금액·카테고리를 바꿀 때 쓴다.
     *
     * Notion 은 HttpURLConnection 으로 PATCH 를 보낼 수 없어 속성을 부분 수정할 방법이
     * 없다 — 그래서 **새 값을 먼저 만들고, 성공하면 옛 줄을 아카이브**한다. 순서가 중요하다:
     *
     * - 새 줄 만들기가 실패하면 옛 줄이 그대로 남아 데이터가 사라지지 않는다.
     * - 새 줄은 만들어졌는데 옛 줄 아카이브가 실패하면, 두 줄이 다 노션에 실재하는 것이므로
     *   로컬 캐시도 그 사실을 그대로 따른다(옛 줄을 캐시에서 빼지 않는다) — 캐시가 항상
     *   «지금 노션에 실제로 있는 것»과 같은 숫자를 말하게 하기 위해서다.
     *
     * 백그라운드 스레드에서 부른다.
     */
    fun edit(
        context: Context,
        purse: Purse,
        day: LocalDate,
        oldPageId: String,
        oldAmount: Long,
        newExpense: Expense,
    ): EditResult {
        val settings = SettingsStore.load(context)
        val target: NotionTarget = settings.target(purse)
            ?: return EditResult(ok = false, message = "DB가 연결되지 않았습니다")
        val client = NotionClient(target)

        return when (val created = client.addExpense(newExpense, day.toString())) {
            is NotionClient.Outcome.Err -> EditResult(ok = false, message = created.message)
            is NotionClient.Outcome.Ok -> {
                // 새 줄이 노션에 실제로 생겼으니 캐시에도 바로 반영한다.
                Ledger.record(context, purse, day, newExpense.amount)
                FirestoreExpenseStore.add(context, purse, created.detail, newExpense, day)

                when (val archived = client.archivePage(oldPageId)) {
                    is NotionClient.Outcome.Err -> EditResult(
                        ok = false,
                        message = "새 값은 저장됐지만 옛 줄을 지우지 못했습니다: ${archived.message}\n" +
                            "노션에서 옛 줄을 직접 지워 주세요.",
                    )
                    is NotionClient.Outcome.Ok -> {
                        Ledger.unrecord(context, purse, day, oldAmount)
                        FirestoreExpenseStore.archive(context, purse, oldPageId)
                        EditResult(ok = true)
                    }
                }
            }
        }
    }

    private fun fail(message: String, now: String): RecordResult =
        RecordResult(ok = false, lines = StatusText.failed(message, now))
}
