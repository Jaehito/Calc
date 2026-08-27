package com.calc.expense

import org.json.JSONObject
import java.time.LocalDate

/**
 * 한 달치 날짜별 지출 합계를 저장 문자열로 옮긴다.
 *
 * 캐시는 언제든 Notion 에서 다시 만들 수 있는 파생 데이터이므로, 읽다가 깨지면
 * 예외를 던지지 않고 빈 값으로 되돌린다. 앱이 못 켜지는 것보다 다시 받아오는 편이 낫다.
 */
object MonthTotals {

    fun encode(totals: Map<LocalDate, Long>): String {
        val json = JSONObject()
        for ((day, amount) in totals) {
            if (amount != 0L) json.put(day.toString(), amount)
        }
        return json.toString()
    }

    fun decode(raw: String?): Map<LocalDate, Long> {
        if (raw.isNullOrBlank()) return emptyMap()

        return try {
            val json = JSONObject(raw)
            val totals = LinkedHashMap<LocalDate, Long>()
            for (key in json.keys()) {
                val day = try {
                    LocalDate.parse(key)
                } catch (_: Exception) {
                    continue
                }
                totals[day] = json.optLong(key, 0L)
            }
            totals
        } catch (_: Exception) {
            emptyMap()
        }
    }
}
