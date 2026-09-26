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
     * 한 곳에서 **매번 다른 것**을 사는 쇼핑몰·간편결제. 알림은 늘 같은 이름(「우리카드 쿠팡」)으로
     * 오지만 산 물건은 매번 다르다. 여기서 이름을 기억하면 한 번 「선호 칫솔」로 고친 뒤로
     * 쿠팡 결제가 전부 「선호 칫솔」이 된다(실제로 그랬다).
     *
     * [normalize] 한 열쇠에 이 낱말이 들어 있으면 기억하지 않는다.
     */
    private val MARKETPLACES = listOf(
        "쿠팡", "쿠페이", "coupang", "포워드벤처스",
        "네이버", "naver", "스마트스토어",
        "11번가", "g마켓", "지마켓", "gmarket", "옥션", "auction",
        "ssg", "쓱", "컬리", "kurly", "위메프", "티몬", "tmon",
        "알리익스프레스", "aliexpress", "테무", "temu",
        "무신사", "에이블리", "지그재그", "오늘의집",
    )

    /**
     * 이 알림 이름을 기억해도 되는가.
     *
     * - 빈 이름은 안 된다.
     * - **은행·카드사 이름만 남은 것**은 안 된다. 가맹점을 못 읽은 결제는 전부 같은 이름
     *   (「우리카드」)이 되므로, 하나를 고치면 그 카드의 못 읽은 결제가 전부 그 이름이 된다.
     * - **쇼핑몰**([MARKETPLACES])은 안 된다. 같은 이름 아래 매번 다른 물건이다.
     */
    fun isRememberable(parsed: String): Boolean {
        val key: String = normalize(parsed)
        if (key.isEmpty()) return false
        if (PaymentParse.isIssuerName(parsed)) return false
        return MARKETPLACES.none { key.contains(it) }
    }

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
        if (!isRememberable(from)) return memory
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
        if (!isRememberable(parsed)) return null
        return memory[normalize(parsed)]
    }

    /**
     * 잘못 쌓인 기억을 걷어 낸다. [isRememberable] 을 통과하지 못하는 열쇠(쇼핑몰·카드사
     * 이름만)는 늘 버린다.
     *
     * [dropChains] 면 **사람이 붙인 이름이 열쇠가 된 줄**도 버린다. 예전에는 기억으로 바뀐
     * 이름(「선호 칫솔」)을 다시 고치면 원래 알림 이름이 아니라 그 바뀐 이름을 열쇠로 적었다.
     * 그 줄은 어떤 알림과도 맞지 않는다. 다만 드물게 진짜 알림 이름과 겹칠 수 있어 저장소가
     * **한 번만** 이걸 켠다.
     */
    fun sanitize(memory: Map<String, String>, dropChains: Boolean = false): Map<String, String> {
        val userNames: Set<String> = if (dropChains) memory.values.map { normalize(it) }.toSet() else emptySet()
        return memory.filter { (key, _) -> isRememberable(key) && key !in userNames }
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
