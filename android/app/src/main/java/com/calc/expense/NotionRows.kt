package com.calc.expense

import org.json.JSONObject
import java.time.LocalDate

/**
 * Notion DB 조회 응답을 날짜별 지출 합계로 접는다.
 *
 * 네트워크와 분리해 둔 이유는 이 부분이 조용히 틀리기 때문이다. 속성 이름이 다르거나
 * 값이 비어 있으면 예외 대신 그 행을 건너뛴다 — 한 행 때문에 한 달치가 통째로
 * 날아가면 안 된다.
 */
object NotionRows {

    /** 응답 한 페이지의 행들을 [into] 에 누적한다. 해석할 수 없는 행은 조용히 건너뛴다. */
    fun accumulate(
        page: JSONObject,
        dateProp: String,
        priceProp: String,
        into: MutableMap<LocalDate, Long>,
    ) {
        val results = page.optJSONArray("results") ?: return

        for (i in 0 until results.length()) {
            val props = results.optJSONObject(i)?.optJSONObject("properties") ?: continue

            val day = readDate(props, dateProp) ?: continue
            val amount = readNumber(props, priceProp) ?: continue

            into[day] = (into[day] ?: 0L) + amount
        }
    }

    /** 다음 페이지가 있으면 커서를, 없으면 null 을 준다. */
    fun nextCursor(page: JSONObject): String? {
        if (!page.optBoolean("has_more", false)) return null
        val cursor = page.optString("next_cursor", "")
        return if (cursor.isBlank() || cursor == "null") null else cursor
    }

    /** date 속성의 start 를 날짜로 읽는다. "2026-08-27T15:21:00+09:00" 처럼 시각이 붙어도 된다. */
    private fun readDate(props: JSONObject, dateProp: String): LocalDate? {
        val start = props.optJSONObject(dateProp)
            ?.optJSONObject("date")
            ?.optString("start")
            ?.takeIf { it.length >= 10 }
            ?: return null

        return try {
            LocalDate.parse(start.substring(0, 10))
        } catch (_: Exception) {
            null
        }
    }

    /** number 속성을 읽는다. 비어 있으면 null — 0 으로 세면 없는 지출이 생긴다. */
    private fun readNumber(props: JSONObject, priceProp: String): Long? {
        val spec = props.optJSONObject(priceProp) ?: return null
        if (spec.isNull("number")) return null
        val value = spec.optDouble("number", Double.NaN)
        if (value.isNaN() || !value.isFinite()) return null
        return Math.round(value)
    }
}
