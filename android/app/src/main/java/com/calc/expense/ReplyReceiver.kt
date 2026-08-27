package com.calc.expense

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 잠금화면 알림에서 전송된 텍스트를 받아 Notion에 기록하고, 오늘 쓸 수 있는 돈을 되돌려준다. */
class ReplyReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_REPLY = "com.calc.expense.ACTION_REPLY"
        const val EXTRA_PURSE = "purse"
        private val TIME_FORMAT = DateTimeFormatter.ofPattern("a h:mm", Locale.KOREA)
    }

    override fun onReceive(context: Context, intent: Intent) {
        val text = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(NotificationHelper.KEY_REPLY)
            ?.toString()
            .orEmpty()
        val purseKey = intent.getStringExtra(EXTRA_PURSE)

        val app = context.applicationContext
        // 네트워크 호출이 필요하므로 브로드캐스트 수명을 연장한다 (약 10초 허용).
        val pending = goAsync()
        Thread {
            val now = LocalTime.now().format(TIME_FORMAT)
            val lines = try {
                handle(app, text, purseKey, now)
            } catch (e: Exception) {
                StatusText.failed("오류: ${e.message ?: e.javaClass.simpleName}", now)
            }
            NotificationHelper.show(app, lines)
            pending.finish()
        }.start()
    }

    private fun handle(context: Context, text: String, purseKey: String?, now: String): StatusLines {
        val parsed = when (val r = ExpenseParser.parse(text)) {
            is ParseResult.Err -> return StatusText.failed("${r.message} · 입력: \"${text.trim()}\"", now)
            is ParseResult.Ok -> r.expense
        }

        val settings = SettingsStore.load(context)
        val linked: List<Purse> = settings.linkedPurses
        if (settings.token.isBlank() || linked.isEmpty()) {
            return StatusText.failed("설정이 비어 있습니다. 앱을 열어 토큰과 DB를 입력하세요", now)
        }

        // 알림 액션이 곳간을 실어 보낸다. 옛 알림이 남아 있어 값이 없으면 첫 곳간으로 본다.
        val purse: Purse = linked.firstOrNull { it.key == purseKey } ?: linked.first()
        val target: NotionTarget = settings.target(purse)
            ?: return StatusText.failed("${purse.label} 곳간에 DB가 연결되지 않았습니다", now)

        val today = LocalDate.now()
        return when (val r = NotionClient(target).addExpense(parsed, today.toString())) {
            is NotionClient.Outcome.Err -> StatusText.failed(r.message, now)
            is NotionClient.Outcome.Ok -> {
                // Notion 쓰기가 성공한 뒤에만 로컬 사본에 더한다. 실패한 기록을 세면 숫자가 거짓말을 한다.
                Ledger.record(context, purse, today, parsed.amount)
                // 여기서 Notion을 한 번 더 왕복하지 않는다 — 남은 수명으로는 못 끝낸다.
                // 로컬 사본만으로 계산하고, Notion 과의 대조는 앱을 열 때 한다.
                StatusText.recorded(
                    name = parsed.name,
                    amount = parsed.amount,
                    snapshot = Ledger.snapshot(context, purse, today),
                    time = now,
                    showPurse = linked.size > 1,
                )
            }
        }
    }
}
