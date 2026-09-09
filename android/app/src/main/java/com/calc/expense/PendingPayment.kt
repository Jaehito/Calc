package com.calc.expense

import org.json.JSONArray
import org.json.JSONObject

/**
 * 수집함에 담긴 «아직 기록하지 않은 결제» 한 건.
 *
 * 알림에서 짐작한 값이라 전부 고칠 수 있다. 사용자가 수집함에서 확인해야 비로소 기록된다 —
 * 자동으로 기록하지 않는다. 잘못 읽은 금액이 조용히 곳간을 갉아먹으면 숫자를 믿을 수 없게 된다.
 *
 * @param id 같은 알림을 두 번 담지 않기 위한 키. 안드로이드 알림 키(StatusBarNotification.key)를 쓴다
 * @param sender 알림 제목(문자 발신번호·카드사 이름). «이 발신자 안 보기» 의 단위다
 * @param packageName 알림을 낸 앱. «이 앱 안 보기» 의 단위다
 * @param issuer 내용에서 읽은 은행·카드사 이름. [sender] 가 발신번호일 때 화면에 대신 보인다
 */
data class PendingPayment(
    val id: String,
    val amount: Long,
    val merchant: String,
    val category: String,
    val packageName: String,
    val sender: String,
    val postedAt: Long,
    val issuer: String = "",
) {
    /**
     * 어디서 온 결제인지 사람이 읽을 수 있는 한 마디.
     *
     * 발신자가 «1577-8000» 같은 번호면 그 번호로는 아무것도 알 수 없다. 그럴 때는 내용에서
     * 읽은 은행·카드사 이름([issuer])을 대신 보인다 — 화면에 필요한 것은 «누가 보냈나»가
     * 아니라 «어디서 나간 돈인가»다.
     */
    val sourceName: String
        get() {
            if (sender.isNotBlank() && !PaymentParse.looksLikeNumber(sender)) return sender
            if (issuer.isNotBlank()) return issuer
            return sender.ifBlank { packageName }
        }
}

/**
 * 수집함 목록을 다루는 규칙. 저장소와 떼어 두어 단위 테스트로 고정한다.
 *
 * 안드로이드는 같은 알림을 갱신할 때마다 다시 올리므로, 같은 키가 오면 덮어쓴다(중복 방지).
 * 오래된 것과 너무 많이 쌓인 것은 버린다 — 수집함은 «지금 확인할 것» 이지 보관함이 아니다.
 */
object PendingPayments {

    /** 이보다 오래된 후보는 버린다. 며칠 지난 결제를 이제 와서 물어봐야 기억나지 않는다. */
    const val KEEP_DAYS = 7L

    /** 수집함에 담아 두는 최대 건수. 넘치면 오래된 것부터 버린다. */
    const val MAX_ITEMS = 50

    private const val DAY_MS = 24L * 60L * 60L * 1000L
    private const val MINUTE_MS = 60L * 1000L

    /** 같은 결제로 볼 시간 폭. 문자앱과 은행앱 알림은 몇 초~몇 분 차이로 잇따라 온다. */
    const val DUPLICATE_WINDOW_MINUTES = 5L

    /**
     * 후보 하나를 넣는다. 같은 [PendingPayment.id] 가 이미 있으면 새 값으로 덮어쓴다.
     * 결과는 최신순(내림차순)으로 정렬되고, 오래된 것과 상한을 넘은 것은 빠진다.
     *
     * **한 결제가 두 알림으로 오면 하나로 합친다.** 카드 문자(발신번호 «1577-8000»)와 은행 앱
     * 푸시가 같은 결제를 각각 올리면 수집함이 같은 것을 두 번 물었다. 알림 키가 달라 id 로는
     * 못 걸러지므로 **금액이 같고 [DUPLICATE_WINDOW_MINUTES] 분 안**이면 같은 결제로 본다.
     */
    fun add(items: List<PendingPayment>, item: PendingPayment, now: Long): List<PendingPayment> {
        val others: List<PendingPayment> = items.filterNot { it.id == item.id }
        val twins: List<PendingPayment> = others.filter { isSamePayment(it, item) }
        if (twins.isEmpty()) return prune(others + item, now)

        val merged: PendingPayment = twins.fold(item) { kept, twin -> merge(kept, twin) }
        return prune(others.filterNot { other -> twins.any { it.id == other.id } } + merged, now)
    }

    /** 두 후보가 같은 결제인가. 금액이 같고 시각이 [DUPLICATE_WINDOW_MINUTES] 분 안이면 같다고 본다. */
    fun isSamePayment(a: PendingPayment, b: PendingPayment): Boolean =
        a.amount == b.amount &&
            kotlin.math.abs(a.postedAt - b.postedAt) <= DUPLICATE_WINDOW_MINUTES * MINUTE_MS

    /**
     * 같은 결제 둘을 하나로 합친다. **이름을 더 잘 읽은 쪽을 남긴다** — 한쪽은 «최재호» 만,
     * 다른 쪽은 «신한은행 최재호» 까지 읽었을 수 있고, 그럴 때 더 많이 아는 쪽이 이겨야 한다.
     *
     * 시각은 둘 중 **이른 쪽**을 쓴다. 문자는 결제보다 늦게 오므로 이른 쪽이 실제 결제 시각에 가깝다.
     * 카테고리는 남은 쪽이 비어 있을 때만 진 쪽에서 가져온다.
     */
    fun merge(a: PendingPayment, b: PendingPayment): PendingPayment {
        val winner: PendingPayment = if (nameScore(b) > nameScore(a)) b else a
        val loser: PendingPayment = if (winner.id == b.id) a else b
        return winner.copy(
            category = winner.category.ifBlank { loser.category },
            issuer = winner.issuer.ifBlank { loser.issuer },
            postedAt = minOf(a.postedAt, b.postedAt),
        )
    }

    /** 이름을 얼마나 읽어냈나. 빈 이름은 0, 길수록 많이 안 것으로 본다. */
    private fun nameScore(item: PendingPayment): Int =
        if (item.merchant.isBlank()) 0 else item.merchant.length

    /** id 로 하나를 뺀다. 기록했거나 사용자가 무시했을 때 부른다. */
    fun remove(items: List<PendingPayment>, id: String): List<PendingPayment> =
        items.filterNot { it.id == id }

    /** 그 앱·발신자에서 온 것을 전부 뺀다. 차단했을 때 이미 쌓인 것도 함께 치운다. */
    fun removeFrom(
        items: List<PendingPayment>,
        packageName: String? = null,
        sender: String? = null,
    ): List<PendingPayment> = items.filterNot { item ->
        (packageName != null && item.packageName == packageName) ||
            (sender != null && item.sender == sender)
    }

    /** 손으로 적은 기록과 같은 결제로 볼 시간 폭. 문자는 결제 몇 분 뒤에 오기도 한다. */
    const val MATCH_WINDOW_MINUTES = 30L

    /**
     * 방금 손으로 적은 기록([amount], [at])과 같은 결제로 보이는 후보의 id. 없으면 null.
     *
     * 잠금화면에서 이미 적은 뒤 카드 문자가 도착하면 수집함이 같은 걸 또 묻게 된다. 그래서
     * 금액이 같고 [MATCH_WINDOW_MINUTES] 분 안에 있는 후보를 하나 치운다.
     *
     * **하나만** 고른다(시간이 가장 가까운 것) — 같은 금액을 하루에 두 번 썼다면 둘 다 사라지면
     * 안 된다. 한 건을 적으면 한 건만 치우는 것이 안전한 쪽이다.
     */
    fun matchRecorded(items: List<PendingPayment>, amount: Long, at: Long): String? {
        val window: Long = MATCH_WINDOW_MINUTES * MINUTE_MS
        return items
            .filter { it.amount == amount && kotlin.math.abs(it.postedAt - at) <= window }
            .minByOrNull { kotlin.math.abs(it.postedAt - at) }
            ?.id
    }

    /** 오래된 것을 버리고 최신순으로 세운 뒤 상한까지만 남긴다. */
    fun prune(items: List<PendingPayment>, now: Long): List<PendingPayment> {
        val oldest: Long = now - KEEP_DAYS * DAY_MS
        return items
            .filter { it.postedAt >= oldest }
            .sortedByDescending { it.postedAt }
            .take(MAX_ITEMS)
    }
}

/**
 * 수집함 목록을 JSON 한 덩어리로 옮긴다.
 *
 * 저장(SharedPreferences)과 떼어 둔 이유는 이 변환이 조용히 틀리기 때문이다 — 읽지 못한 항목은
 * 통째로 사라지는 편이 낫지, 예외로 앱을 죽이면 안 된다. 그래서 항목 단위로 건너뛴다.
 */
object PendingPaymentCodec {

    fun encode(items: List<PendingPayment>): String {
        val array = JSONArray()
        for (item in items) {
            array.put(
                JSONObject()
                    .put("id", item.id)
                    .put("amount", item.amount)
                    .put("merchant", item.merchant)
                    .put("category", item.category)
                    .put("package", item.packageName)
                    .put("sender", item.sender)
                    .put("postedAt", item.postedAt)
                    .put("issuer", item.issuer),
            )
        }
        return array.toString()
    }

    /** 해석할 수 없는 항목은 조용히 건너뛴다. 한 줄 때문에 수집함 전체를 잃지 않는다. */
    fun decode(raw: String?): List<PendingPayment> {
        if (raw.isNullOrBlank()) return emptyList()
        val array: JSONArray = try {
            JSONArray(raw)
        } catch (_: Exception) {
            return emptyList()
        }

        val items = ArrayList<PendingPayment>()
        for (i in 0 until array.length()) {
            val obj: JSONObject = array.optJSONObject(i) ?: continue
            val id: String = obj.optString("id", "")
            if (id.isEmpty()) continue
            val amount: Long = obj.optLong("amount", 0L)
            if (amount <= 0L) continue

            items.add(
                PendingPayment(
                    id = id,
                    amount = amount,
                    merchant = obj.optString("merchant", ""),
                    category = obj.optString("category", ""),
                    packageName = obj.optString("package", ""),
                    sender = obj.optString("sender", ""),
                    postedAt = obj.optLong("postedAt", 0L),
                    issuer = obj.optString("issuer", ""),
                ),
            )
        }
        return items
    }
}
