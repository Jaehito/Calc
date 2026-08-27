package com.calc.expense

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.YearMonth

/** Notion REST API 최소 클라이언트. 반드시 백그라운드 스레드에서 호출할 것. */
class NotionClient(private val settings: Settings) {

    sealed class Outcome {
        /** addExpense 는 만들어진 페이지 URL, verify 는 DB 제목을 담는다. */
        data class Ok(val detail: String) : Outcome()
        data class Err(val message: String) : Outcome()
    }

    /** 한 달치 조회 결과. 날짜별 지출 합계를 담는다. */
    sealed class MonthOutcome {
        data class Ok(val totals: Map<LocalDate, Long>) : MonthOutcome()
        data class Err(val message: String) : MonthOutcome()
    }

    companion object {
        private const val BASE = "https://api.notion.com"
        private const val NOTION_VERSION = "2022-06-28"
        // goAsync() 로 연장한 브로드캐스트 수명은 약 10초다. 연결+읽기 합이 그 안에
        // 끝나야 결과를 알림에 반영할 수 있으므로 넉넉히 잡지 않는다.
        private const val CONNECT_TIMEOUT_MS = 4_000
        private const val READ_TIMEOUT_MS = 5_000
        private const val PAGE_SIZE = 100
        // 한 달 지출이 이만큼을 넘을 일은 없다. 응답이 이상할 때 무한 루프를 막는 안전장치다.
        private const val MAX_PAGES = 20
    }

    /** 지출 한 건을 DB에 추가한다. isoDate 는 "2026-08-26" 형식. */
    fun addExpense(expense: Expense, isoDate: String): Outcome {
        val properties = JSONObject().apply {
            put(
                settings.nameProp,
                JSONObject().put(
                    "title",
                    JSONArray().put(
                        JSONObject().put("text", JSONObject().put("content", expense.name))
                    )
                )
            )
            put(settings.priceProp, JSONObject().put("number", expense.amount))
            put(settings.dateProp, JSONObject().put("date", JSONObject().put("start", isoDate)))
        }

        val body = JSONObject()
            .put("parent", JSONObject().put("database_id", settings.databaseId))
            .put("properties", properties)

        return when (val r = send("POST", "/v1/pages", body)) {
            is Raw.Ok -> Outcome.Ok(r.json.optString("url", ""))
            is Raw.Err -> Outcome.Err(r.message)
        }
    }

    /**
     * 한 달치 지출을 날짜별 합계로 가져온다. 로컬 캐시를 Notion 기준으로 다시 맞출 때 쓴다.
     *
     * 잠금화면 기록 경로에서는 쓰지 않는다 — 브로드캐스트 수명 안에 왕복을 두 번 할 수 없다.
     * 앱을 열었을 때 백그라운드에서만 부른다.
     */
    fun queryMonth(month: YearMonth): MonthOutcome {
        val first: LocalDate = month.atDay(1)
        val last: LocalDate = month.atEndOfMonth()

        val filter = JSONObject().put(
            "and",
            JSONArray()
                .put(dateBound(first.toString(), "on_or_after"))
                .put(dateBound(last.toString(), "on_or_before")),
        )

        val totals = LinkedHashMap<LocalDate, Long>()
        var cursor: String? = null
        var page = 0

        while (page < MAX_PAGES) {
            val body = JSONObject()
                .put("filter", filter)
                .put("page_size", PAGE_SIZE)
            if (cursor != null) body.put("start_cursor", cursor)

            val r = send("POST", "/v1/databases/${settings.databaseId}/query", body)
            if (r is Raw.Err) return MonthOutcome.Err(r.message)

            val json = (r as Raw.Ok).json
            NotionRows.accumulate(json, settings.dateProp, settings.priceProp, totals)

            cursor = NotionRows.nextCursor(json) ?: return MonthOutcome.Ok(totals)
            page++
        }

        // 여기까지 왔으면 응답이 계속 다음 페이지를 가리킨 것이다. 받은 만큼만 쓴다.
        return MonthOutcome.Ok(totals)
    }

    private fun dateBound(iso: String, condition: String): JSONObject =
        JSONObject()
            .put("property", settings.dateProp)
            .put("date", JSONObject().put(condition, iso))

    /** 토큰과 DB 접근 권한, 속성 이름이 맞는지 확인한다. */
    fun verify(): Outcome {
        val r = send("GET", "/v1/databases/${settings.databaseId}", null)
        if (r is Raw.Err) return Outcome.Err(r.message)

        val db = (r as Raw.Ok).json
        val props = db.optJSONObject("properties") ?: JSONObject()

        val missing = mutableListOf<String>()
        checkProp(props, settings.nameProp, "title")?.let { missing += it }
        checkProp(props, settings.priceProp, "number")?.let { missing += it }
        checkProp(props, settings.dateProp, "date")?.let { missing += it }

        if (missing.isNotEmpty()) {
            val available = props.keys().asSequence().joinToString(", ")
            return Outcome.Err(missing.joinToString("\n") + "\n\nDB의 실제 속성: $available")
        }

        val title = db.optJSONArray("title")?.let { arr ->
            (0 until arr.length()).joinToString("") { arr.getJSONObject(it).optString("plain_text") }
        }.orEmpty()

        return Outcome.Ok(title.ifBlank { "(제목 없음)" })
    }

    /** 속성이 없거나 타입이 다르면 사람이 읽을 수 있는 오류 문구를 돌려준다. */
    private fun checkProp(props: JSONObject, name: String, expectedType: String): String? {
        val spec = props.optJSONObject(name)
            ?: return "· '$name' 속성이 DB에 없습니다"
        val actual = spec.optString("type")
        return if (actual != expectedType) {
            "· '$name' 은 $expectedType 이어야 하는데 $actual 입니다"
        } else null
    }

    private sealed class Raw {
        data class Ok(val json: JSONObject) : Raw()
        data class Err(val message: String) : Raw()
    }

    private fun send(method: String, path: String, body: JSONObject?): Raw {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(BASE + path).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("Authorization", "Bearer ${settings.token}")
                setRequestProperty("Notion-Version", NOTION_VERSION)
                setRequestProperty("Accept", "application/json")
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
            }

            if (body != null) {
                conn.outputStream.use { it.write(body.toString().toByteArray(StandardCharsets.UTF_8)) }
            }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()

            if (code in 200..299) {
                Raw.Ok(JSONObject(text))
            } else {
                Raw.Err(describeError(code, text))
            }
        } catch (_: SocketTimeoutException) {
            // 요청이 서버에 닿았는지 알 수 없다. 중복 기록을 피하려면 사용자가 확인해야 한다.
            Raw.Err("시간 초과 — 기록됐는지 Notion에서 확인 후 다시 시도하세요")
        } catch (e: IOException) {
            Raw.Err("네트워크 오류: ${e.message ?: e.javaClass.simpleName}")
        } catch (e: Exception) {
            Raw.Err("오류: ${e.message ?: e.javaClass.simpleName}")
        } finally {
            conn?.disconnect()
        }
    }

    /** Notion 오류 응답을 한국어 안내로 바꾼다. */
    private fun describeError(code: Int, rawBody: String): String {
        val notionMessage = try {
            JSONObject(rawBody).optString("message").ifBlank { null }
        } catch (_: Exception) {
            null
        }

        val hint = when (code) {
            401 -> "토큰이 잘못되었습니다. 인테그레이션 시크릿을 다시 확인하세요."
            403 -> "권한이 없습니다. DB 페이지의 ••• → 연결 에서 인테그레이션을 추가하세요."
            404 -> "DB를 찾을 수 없습니다. DB ID가 맞는지, 그리고 ••• → 연결 로 권한을 줬는지 확인하세요."
            400 -> "요청이 거부되었습니다. 속성 이름이나 타입을 확인하세요."
            429 -> "요청이 너무 잦습니다. 잠시 후 다시 시도하세요."
            in 500..599 -> "Notion 서버 오류입니다. 잠시 후 다시 시도하세요."
            else -> null
        }

        return listOfNotNull(hint, notionMessage?.let { "($it)" })
            .joinToString(" ")
            .ifBlank { "HTTP $code" }
    }
}
