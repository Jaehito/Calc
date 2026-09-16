package com.calc.expense

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * «본 결제» 한 건. 수집함([PendingPayment])과 달리 **확인하든 무시하든 남는다.**
 *
 * 수집함은 «지금 물어볼 것»이라 7일이면 버리고, 기록하거나 무시하면 그 자리에서 사라진다.
 * 그런데 고정비를 찾으려면 정확히 그 사라진 것들이 필요하다 — 월세·보험·통신비는 자동이체라
 * 사용자가 손으로 적지 않고, 수집함에서도 «무시»로 치워 버리는 바로 그 항목들이다.
 * 그래서 목록을 따로 하나 더 둔다.
 *
 * **금액·이름·시각만** 담는다. 카테고리도 발신자도 앱 이름도 넣지 않는다 — 여기서 하는 일은
 * «같은 값이 달마다 반복되나»를 세는 것 하나뿐이고, 그 외의 것은 쌓아 둘 이유가 없다.
 * 폰 안에만 있고 저장소로 올리지 않는다.
 */
data class PaymentLogEntry(
    val name: String,
    val amount: Long,
    val at: Long,
)

/**
 * 결제 기록 목록 규칙. 저장소와 떼어 두어 단위 테스트로 고정한다.
 */
object PaymentLogs {

    /**
     * 이보다 오래된 것은 버린다. 주기 세 번을 온전히 덮으려면 달의 길이가 흔들리는 만큼
     * 여유가 있어야 한다 — 93일이면 3주기가 «간신히» 들어가서 맨 앞 주기가 잘릴 수 있다.
     */
    const val KEEP_DAYS = 120L

    /** 담아 두는 최대 건수. 하루 다섯 건씩 넉 달을 써도 닿지 않는 수다. */
    const val MAX_ITEMS = 600

    /** 같은 결제로 볼 시간 폭. 문자앱과 은행앱이 같은 결제를 각각 올린다([PendingPayments] 와 같은 판단). */
    const val DUPLICATE_WINDOW_MINUTES = 5L

    private const val DAY_MS = 24L * 60L * 60L * 1000L
    private const val MINUTE_MS = 60L * 1000L

    /**
     * 한 건을 넣는다. 같은 결제가 이미 있으면 **이름을 더 잘 읽은 쪽**을 남긴다.
     *
     * 이름이 반복 판정의 열쇠라서 여기서 지는 쪽을 남기면 같은 고정비가 두 이름으로 갈라져
     * 어느 쪽도 «달마다 나간다»에 닿지 못한다.
     */
    fun add(items: List<PaymentLogEntry>, item: PaymentLogEntry, now: Long): List<PaymentLogEntry> {
        if (item.amount <= 0L) return prune(items, now)

        val twins: List<PaymentLogEntry> = items.filter { isSamePayment(it, item) }
        if (twins.isEmpty()) return prune(items + item, now)

        val rest: List<PaymentLogEntry> = items.filterNot { isSamePayment(it, item) }
        val best: PaymentLogEntry = (twins + item).maxByOrNull { it.name.length } ?: item
        val kept = PaymentLogEntry(
            name = best.name,
            amount = item.amount,
            at = minOf(item.at, twins.minOf { it.at }),
        )
        return prune(rest + kept, now)
    }

    /** 두 건이 같은 결제인가. 금액이 같고 시각이 [DUPLICATE_WINDOW_MINUTES] 분 안이면 같다고 본다. */
    fun isSamePayment(a: PaymentLogEntry, b: PaymentLogEntry): Boolean =
        a.amount == b.amount &&
            kotlin.math.abs(a.at - b.at) <= DUPLICATE_WINDOW_MINUTES * MINUTE_MS

    /** 오래된 것을 버리고 최신순으로 세운 뒤 상한까지만 남긴다. */
    fun prune(items: List<PaymentLogEntry>, now: Long): List<PaymentLogEntry> {
        val oldest: Long = now - KEEP_DAYS * DAY_MS
        return items
            .filter { it.at >= oldest && it.amount > 0L }
            .sortedByDescending { it.at }
            .take(MAX_ITEMS)
    }

    /** 반복 판정이 읽을 수 있는 모양으로 옮긴다. 시각을 그 기기의 날짜로 바꾸는 유일한 자리다. */
    fun events(items: List<PaymentLogEntry>, zone: ZoneId = ZoneId.systemDefault()): List<MoneyEvent> =
        items.map { entry ->
            MoneyEvent(
                name = entry.name,
                amount = entry.amount,
                date = Instant.ofEpochMilli(entry.at).atZone(zone).toLocalDate(),
                fromRecord = false,
            )
        }
}

/**
 * 결제 기록을 JSON 한 덩어리로 옮긴다. [PendingPaymentCodec] 과 같은 판단 —
 * 읽지 못한 항목은 통째로 사라지는 편이 낫지, 예외로 앱을 죽이면 안 된다.
 */
object PaymentLogCodec {

    fun encode(items: List<PaymentLogEntry>): String {
        val array = JSONArray()
        for (item in items) {
            array.put(
                JSONObject()
                    .put("name", item.name)
                    .put("amount", item.amount)
                    .put("at", item.at),
            )
        }
        return array.toString()
    }

    fun decode(raw: String?): List<PaymentLogEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        val array: JSONArray = try {
            JSONArray(raw)
        } catch (_: Exception) {
            return emptyList()
        }

        val items = ArrayList<PaymentLogEntry>()
        for (i in 0 until array.length()) {
            val obj: JSONObject = array.optJSONObject(i) ?: continue
            val amount: Long = obj.optLong("amount", 0L)
            if (amount <= 0L) continue
            items.add(
                PaymentLogEntry(
                    name = obj.optString("name", ""),
                    amount = amount,
                    at = obj.optLong("at", 0L),
                ),
            )
        }
        return items
    }
}

/** 지출 기록을 반복 판정이 읽을 수 있는 모양으로 옮긴다. */
fun ExpenseRow.toMoneyEvent(): MoneyEvent =
    MoneyEvent(name = name, amount = amount, date = date, fromRecord = true)

/** 날짜만 남긴 «돈이 나간 한 건». 기록에서 왔든 알림에서 왔든 여기서는 같은 모양이다. */
data class MoneyEvent(
    val name: String,
    val amount: Long,
    val date: LocalDate,
    /** 사용자가 손으로 적은 기록인가. 아니면 결제 알림에서만 본 것이다. */
    val fromRecord: Boolean,
)
