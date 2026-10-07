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

    /** 기억하는 이름 수의 상한. 넘으면 오래 안 쓴 것부터 버린다. 지난 기록에서 배운 것까지 담도록 넉넉히. */
    const val MAX_ENTRIES = 500

    /** 이 길이보다 짧은 이름은 부분 일치에 쓰지 않는다 — 한 글자가 아무 데나 걸린다. */
    const val MIN_PARTIAL_LENGTH = 2

    /**
     * 이름을 견주기 좋게 다듬는다. 띄어쓰기를 없애고 소문자로 — 「GS25」와 「gs 25」는 같은 곳이다.
     * 결제 알림에 붙어 오는 회사 꼴(«(주)»·«㈜»·«주식회사»)도 뗀다.
     */
    fun normalize(raw: String): String {
        var text: String = raw
        for (mark in COMPANY_MARKS) text = text.replace(mark, "")
        return text.filterNot { it.isWhitespace() }.lowercase()
    }

    /**
     * 맨 끝 지점 이름을 뗀 꼴. «스타벅스 강남점» → «스타벅스». 띄어 쓴 마지막 낱말이 «점»으로 끝날 때만 —
     * «편의점» 하나만 적은 이름은 그대로다. 지점이 없으면 null.
     *
     * 찾을 때 마지막에 한 번 더 본다([lookup]) — 지점마다 따로 기억하면 «스타벅스 역삼점»에서 처음부터다.
     */
    fun withoutBranch(raw: String): String? {
        var text: String = raw
        for (mark in COMPANY_MARKS) text = text.replace(mark, " ")
        val words: List<String> = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.size < 2 || words.last().length < 2 || !words.last().endsWith("점")) return null
        return normalize(words.dropLast(1).joinToString(" "))
    }

    private val COMPANY_MARKS: List<String> = listOf("(주)", "㈜", "주식회사", "(유)", "유한회사")

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
        val found: String? = find(memory, normalize(name), categories)
        if (found != null) return found
        val branchless: String = withoutBranch(name) ?: return null
        return find(memory, branchless, categories)
    }

    private fun find(memory: Map<String, String>, key: String, categories: List<String>): String? {
        if (key.isEmpty()) return null

        val exact: String? = memory[key]
        if (exact != null && exact in categories) return exact

        for ((remembered, category) in memory.entries.reversed()) {
            if (category !in categories) continue
            if (remembered.length < MIN_PARTIAL_LENGTH) continue
            if (key.contains(remembered)) return category
            // 적는 중인 앞부분(«스타»)이 기억한 이름(«스타벅스»)에 들어 있는 경우. 한 글자는 아무 데나 걸린다.
            if (key.length >= MIN_PARTIAL_LENGTH && remembered.contains(key)) return category
        }
        return null
    }

    /**
     * 지난 기록([rows], 오래된 것 → 최근 것, (이름, 카테고리))에서 이름마다 카테고리 하나를 뽑는다.
     *
     * 가장 많이 쓴 카테고리가 이기고, 같으면 최근 것이 이긴다. 카테고리가 빈 줄은 건너뛴다 — «미분류»는
     * 고른 게 아니라 아직 안 고른 것이다. 돌려주는 순서는 마지막으로 쓴 때가 이른 것부터다([seed] 가 그대로 쌓는다).
     */
    fun learn(rows: List<Pair<String, String>>): List<Pair<String, String>> {
        val counts = LinkedHashMap<String, LinkedHashMap<String, Int>>()
        val lastSeen = LinkedHashMap<String, Int>()
        val lastCategory = HashMap<String, String>()
        for ((index, row) in rows.withIndex()) {
            val key: String = normalize(row.first)
            val category: String = row.second.trim()
            if (key.isEmpty() || category.isEmpty()) continue
            val perName: LinkedHashMap<String, Int> = counts.getOrPut(key) { LinkedHashMap() }
            perName[category] = (perName[category] ?: 0) + 1
            lastSeen.remove(key)
            lastSeen[key] = index
            lastCategory[key] = category
        }
        return lastSeen.keys.map { key ->
            val perName: Map<String, Int> = counts.getValue(key)
            val top: Int = perName.values.maxOrNull() ?: 0
            val latest: String = lastCategory.getValue(key)
            val winner: String = if (perName[latest] == top) latest else perName.entries.first { it.value == top }.key
            key to winner
        }
    }

    /**
     * 지난 기록에서 배운 것([learned])을 기억에 보탠다. **이 폰에서 정한 것이 이긴다** — 이미 있는 이름은
     * 건드리지 않는다. 배운 것은 기억의 앞(오래된 쪽)에 둔다: 부분 일치는 최근 것부터 보므로 이 폰에서 막
     * 정한 것이 먼저 걸리고, 넘치면 배운 것부터 버린다.
     */
    fun seed(memory: Map<String, String>, learned: List<Pair<String, String>>): Map<String, String> {
        val next = LinkedHashMap<String, String>()
        for ((name, category) in learned) {
            val key: String = normalize(name)
            if (key.isEmpty() || category.isBlank() || key in memory) continue
            next[key] = category.trim()
        }
        if (next.isEmpty()) return memory
        for ((key, category) in memory) {
            next.remove(key)
            next[key] = category
        }
        if (next.size <= MAX_ENTRIES) return next
        return next.entries.drop(next.size - MAX_ENTRIES).associate { it.key to it.value }
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
