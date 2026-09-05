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
 */
data class PendingPayment(
    val id: String,
    val amount: Long,
    val merchant: String,
    val category: String,
    val packageName: String,
    val sender: String,
    val postedAt: Long,
)

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

    /**
     * 후보 하나를 넣는다. 같은 [PendingPayment.id] 가 이미 있으면 새 값으로 덮어쓴다.
     * 결과는 최신순(내림차순)으로 정렬되고, 오래된 것과 상한을 넘은 것은 빠진다.
     */
    fun add(items: List<PendingPayment>, item: PendingPayment, now: Long): List<PendingPayment> =
        prune(items.filterNot { it.id == item.id } + item, now)

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
                    .put("postedAt", item.postedAt),
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
                ),
            )
        }
        return items
    }
}
