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
)

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
    ): RecordResult {
        val parsed = when (val r = ExpenseParser.parse(text)) {
            is ParseResult.Err ->
                return fail("${r.message} · 입력: \"${text.trim()}\"", now)
            is ParseResult.Ok -> r.expense
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
                // 여기서 Notion 을 한 번 더 왕복하지 않는다 — 브로드캐스트 수명 안에 못 끝낸다.
                // 로컬 사본만으로 계산하고, Notion 과의 대조는 앱을 열 때 한다.
                RecordResult(
                    ok = true,
                    expense = parsed,
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

    private fun fail(message: String, now: String): RecordResult =
        RecordResult(ok = false, lines = StatusText.failed(message, now))
}
