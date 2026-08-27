package com.calc.expense

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import java.text.NumberFormat
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 잠금화면 알림에서 전송된 텍스트를 받아 Notion에 기록한다. */
class ReplyReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_REPLY = "com.calc.expense.ACTION_REPLY"
        private val TIME_FORMAT = DateTimeFormatter.ofPattern("a h:mm", Locale.KOREA)
    }

    override fun onReceive(context: Context, intent: Intent) {
        val text = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(NotificationHelper.KEY_REPLY)
            ?.toString()
            .orEmpty()

        val app = context.applicationContext
        // 네트워크 호출이 필요하므로 브로드캐스트 수명을 연장한다 (약 10초 허용).
        val pending = goAsync()
        Thread {
            val status = try {
                handle(app, text)
            } catch (e: Exception) {
                "✗ 오류: ${e.message ?: e.javaClass.simpleName}"
            }
            NotificationHelper.show(app, "$status · ${LocalTime.now().format(TIME_FORMAT)}")
            pending.finish()
        }.start()
    }

    private fun handle(context: Context, text: String): String {
        val parsed = when (val r = ExpenseParser.parse(text)) {
            is ParseResult.Err -> return "✗ ${r.message} · 입력: \"${text.trim()}\""
            is ParseResult.Ok -> r.expense
        }

        val settings = SettingsStore.load(context)
        if (!settings.isComplete) return "✗ 설정이 비어 있습니다. 앱을 열어 토큰과 DB를 입력하세요"

        val today = LocalDate.now().toString()
        return when (val r = NotionClient(settings).addExpense(parsed, today)) {
            is NotionClient.Outcome.Ok -> "✓ ${parsed.name} ${formatWon(parsed.amount)} 기록됨"
            is NotionClient.Outcome.Err -> "✗ ${r.message}"
        }
    }

    private fun formatWon(amount: Long): String =
        NumberFormat.getNumberInstance(Locale.KOREA).format(amount) + "원"
}
