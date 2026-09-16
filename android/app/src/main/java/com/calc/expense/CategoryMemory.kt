package com.calc.expense

import org.json.JSONObject

/**
 * 사용자가 그 이름을 어느 카테고리에 넣었는지 기억한다.
 *
 * [CategoryClassifier] 는 낱말 규칙이라 아는 말만 안다 — 「우아한형제들」·「HUAMAN CAR」·
 * 단골 가게 이름처럼 규칙에 없는 말은 영영 «없음»으로 떨어진다. 규칙을 계속 늘리는 것은
 * 끝이 없고, 끝이 없는 목록은 결국 낡는다.
 *
 * 대신 **사용자가 한 번 정한 것을 기억한다.** 「우아한형제들」을 식비로 적었으면 다음부터
 * 그 이름은 식비다. 쓰면 쓸수록 «없음»이 줄어드는 쪽이 낱말을 더 채워 넣는 쪽보다 낫다.
 *
 * **기억이 규칙을 이긴다.** 스타벅스를 카페가 아니라 식비로 적어 온 사람에게 앱이 매번
 * 카페로 되돌리면, 그건 짐작이 아니라 고집이다.
 */
object CategoryMemories {

    /** 기억하는 이름 수의 상한. 넘으면 오래 안 쓴 것부터 버린다. */
    const val MAX_ENTRIES = 300

    /** 이 길이보다 짧은 이름은 부분 일치에 쓰지 않는다 — 한 글자가 아무 데나 걸린다. */
    const val MIN_PARTIAL_LENGTH = 2

    /**
     * 이름을 견주기 좋게 다듬는다. 띄어쓰기를 없애고 소문자로 — 「GS25」와 「gs 25」는 같은 곳이다.
     */
    fun normalize(raw: String): String = raw.filterNot { it.isWhitespace() }.lowercase()

    /**
     * [name] 을 [category] 로 기억한다. 이미 있으면 새 값으로 덮고 **맨 뒤로 보낸다** —
     * 최근에 쓴 것이 오래 남아야 한다.
     *
     * 빈 카테고리는 기억하지 않는다. «없음»은 사용자가 고른 것이 아니라 아직 안 고른 것이다.
     */
    fun put(memory: Map<String, String>, name: String, category: String): Map<String, String> {
        val key: String = normalize(name)
        val value: String = category.trim()
        if (key.isEmpty() || value.isEmpty()) return memory

        val next = LinkedHashMap<String, String>(memory)
        next.remove(key)
        next[key] = value

        if (next.size <= MAX_ENTRIES) return next
        val drop: Int = next.size - MAX_ENTRIES
        return next.entries.drop(drop).associate { it.key to it.value }
    }

    /**
     * [name] 에 대해 기억해 둔 카테고리. 없거나 그 카테고리가 지금 칩 목록([categories])에
     * 없으면 null — 사용자가 칩 이름을 바꿨으면 없는 칩을 켤 수 없다.
     *
     * 정확히 같은 이름을 먼저 보고, 없으면 **최근 것부터** 부분 일치를 본다. 「스타벅스 강남점」을
     * 적었을 때 예전에 「스타벅스」로 정해 둔 것이 걸리게 하기 위해서다.
     */
    fun lookup(memory: Map<String, String>, name: String, categories: List<String>): String? {
        val key: String = normalize(name)
        if (key.isEmpty()) return null

        val exact: String? = memory[key]
        if (exact != null && exact in categories) return exact

        for ((remembered, category) in memory.entries.reversed()) {
            if (category !in categories) continue
            if (remembered.length < MIN_PARTIAL_LENGTH) continue
            if (key.contains(remembered) || remembered.contains(key)) return category
        }
        return null
    }
}

/**
 * 기억을 JSON 한 덩어리로 옮긴다. [PendingPaymentCodec] 과 같은 판단 —
 * 읽지 못한 항목은 통째로 사라지는 편이 낫지, 예외로 앱을 죽이면 안 된다.
 *
 * JSONObject 는 넣은 순서를 지키지 않을 수 있어 순서를 따로 적어 둔다. 순서가 곧
 * «최근에 쓴 것»이라 잃으면 부분 일치가 엉뚱한 옛날 이름을 집는다.
 */
object CategoryMemoryCodec {

    private const val KEY_ORDER = "order"
    private const val KEY_MAP = "map"

    fun encode(memory: Map<String, String>): String {
        val map = JSONObject()
        val order = org.json.JSONArray()
        for ((name, category) in memory) {
            map.put(name, category)
            order.put(name)
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
            val name: String = order.optString(i, "")
            if (name.isEmpty()) continue
            val category: String = map.optString(name, "")
            if (category.isEmpty()) continue
            result[name] = category
        }
        return result
    }
}
