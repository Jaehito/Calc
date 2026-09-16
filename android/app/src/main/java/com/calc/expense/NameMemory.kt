package com.calc.expense

import org.json.JSONObject

/**
 * 알림에서 읽은 이름을 사용자가 **어떤 이름으로 고쳤는지** 기억한다.
 *
 * 수집함 카드에서 「우리카드 우아한형제들」을 「배달」로 고쳐 기록해도, 다음 결제 알림이 오면
 * 또 「우리카드 우아한형제들」로 떴다. 앱이 그 고침을 기억하지 않아서 같은 가게를 볼 때마다
 * 매번 다시 고쳐야 했다. 이 기억이 그 반복을 한 번으로 끝낸다.
 *
 * 이름이 사용자의 것이 되면 [CategoryMemories] 도 저절로 걸린다 — 카테고리 기억은 사용자가
 * 적은 이름에 붙어 있어서, 알림의 원래 이름으로는 찾지 못했다.
 *
 * **[PaymentLogStore] 에도 고친 이름으로 쌓인다.** 고정비 찾기는 이름으로 묶으므로,
 * 같은 곳이 두 이름으로 갈라져 둘 다 탈락하던 문제가 여기서 사라진다.
 */
object NameMemories {

    /** 기억하는 이름 수의 상한. 넘으면 오래 안 쓴 것부터 버린다. */
    const val MAX_ENTRIES = 300

    /** 견주기용 열쇠. 띄어쓰기를 없애고 소문자로 ([CategoryMemories] 와 같은 방식). */
    fun normalize(raw: String): String = raw.filterNot { it.isWhitespace() }.lowercase()

    /**
     * [from](알림이 읽은 이름)을 [to](사용자가 고른 이름)로 기억한다.
     * 이미 있으면 새 값으로 덮고 **맨 뒤로 보낸다** — 최근에 쓴 것이 오래 남아야 한다.
     *
     * 고치지 않았으면 기억하지 않는다. 같은 이름을 같은 이름으로 바꾸는 줄이 상한을 채우면
     * 정작 필요한 기억이 밀려난다.
     */
    fun put(memory: Map<String, String>, from: String, to: String): Map<String, String> {
        val key: String = normalize(from)
        val value: String = to.trim()
        if (key.isEmpty() || value.isEmpty()) return memory
        if (key == normalize(value)) return memory

        val next = LinkedHashMap<String, String>(memory)
        next.remove(key)
        next[key] = value

        if (next.size <= MAX_ENTRIES) return next
        return next.entries.drop(next.size - MAX_ENTRIES).associate { it.key to it.value }
    }

    /**
     * [parsed] 를 대신할 이름. 기억에 없으면 null.
     *
     * **정확히 같은 이름만 본다 — 부분 일치를 쓰지 않는다.** [CategoryMemories] 와 갈리는
     * 지점이고, 이유는 틀렸을 때의 값이 다르기 때문이다. 카테고리를 잘못 짚으면 칩 한 번
     * 누르면 그만이지만, 이름을 잘못 바꾸면 **원래 정보가 사라진다.** 「신한은행」을 「월세」로
     * 기억해 둔 상태에서 부분 일치를 허용하면 신한은행에서 나간 모든 결제가 월세가 되고,
     * 그 이름 그대로 [PaymentLogStore] 에 쌓여 고정비 찾기까지 망가진다.
     *
     * 카드사 문자는 기계가 찍어 내는 글이라 같은 가맹점이면 파싱 결과도 늘 같다.
     * 정확한 일치만으로 실제 상황의 대부분이 걸린다.
     */
    fun lookup(memory: Map<String, String>, parsed: String): String? {
        val key: String = normalize(parsed)
        if (key.isEmpty()) return null
        return memory[key]
    }
}

/**
 * 이름 기억을 JSON 한 덩어리로 옮긴다. [CategoryMemoryCodec] 과 같은 모양 —
 * JSONObject 가 넣은 순서를 지키지 않을 수 있어 순서를 따로 적어 둔다.
 */
object NameMemoryCodec {

    private const val KEY_ORDER = "order"
    private const val KEY_MAP = "map"

    fun encode(memory: Map<String, String>): String {
        val map = JSONObject()
        val order = org.json.JSONArray()
        for ((from, to) in memory) {
            map.put(from, to)
            order.put(from)
        }
        return JSONObject().put(KEY_MAP, map).put(KEY_ORDER, order).toString()
    }

    fun decode(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        val root: JSONObject = try {
            JSONObject(raw)
        } catch (_: Exception) {
            return emptyMap()
        }

        val map: JSONObject = root.optJSONObject(KEY_MAP) ?: return emptyMap()
        val order: org.json.JSONArray = root.optJSONArray(KEY_ORDER) ?: org.json.JSONArray()

        val result = LinkedHashMap<String, String>()
        for (i in 0 until order.length()) {
            val from: String = order.optString(i, "")
            if (from.isEmpty()) continue
            val to: String = map.optString(from, "")
            if (to.isEmpty()) continue
            result[from] = to
        }
        return result
    }
}
