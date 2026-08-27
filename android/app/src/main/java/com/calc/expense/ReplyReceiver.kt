package com.calc.expense

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 잠금화면 알림에서 전송된 텍스트를 받아 기록한다. 실제 순서는 [RecordExpense] 가 안다.
 *
 * 지금은 알림에 인라인 답장 액션이 없어 이 경로로 들어오지 않는다. 기기가 잠금화면 위로
 * 액티비티를 띄우는 걸 막는 경우를 대비해 남겨 둔다. [TIME_FORMAT] 은 계속 쓰인다.
 */
class ReplyReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_REPLY = "com.calc.expense.ACTION_REPLY"
        const val EXTRA_PURSE = "purse"
        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("a h:mm", Locale.KOREA)
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
                RecordExpense.submit(app, text, purseKey, now).lines
            } catch (e: Exception) {
                StatusText.failed("오류: ${e.message ?: e.javaClass.simpleName}", now)
            }
            NotificationHelper.show(app, lines)
            pending.finish()
        }.start()
    }
}
