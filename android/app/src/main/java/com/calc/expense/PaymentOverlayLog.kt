package com.calc.expense

import android.content.Context
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * **임시 진단용.** 결제 팝업([PaymentOverlay])을 띄우려 한 최근 몇 번을 남긴다.
 *
 * 은행 앱 위에서 팝업도 배너도 안 보인 일이 있었다 — 팝업 창은 붙었는데 화면에 안 보인 것인지,
 * 애초에 다른 길로 빠진 것인지 폰 밖에서는 알 수 없다. 설정의 «팝업 시험» 아래에 그대로 보여 준다.
 * 원인을 찾으면 지운다. 개발자가 읽는 줄이라 번역하지 않는다.
 */
object PaymentOverlayLog {

    private const val FILE = "payment_overlay_log"
    private const val KEY = "lines"
    private const val MAX = 6
    private const val SEP = "\u001F"

    private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

    fun add(context: Context, line: String) {
        val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val old: List<String> = prefs.getString(KEY, null)?.split(SEP).orEmpty().filter { it.isNotBlank() }
        val next: List<String> = (listOf(LocalTime.now().format(TIME) + " " + line) + old).take(MAX)
        prefs.edit().putString(KEY, next.joinToString(SEP)).apply()
    }

    fun lines(context: Context): List<String> =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString(KEY, null)?.split(SEP).orEmpty().filter { it.isNotBlank() }
}
