package com.calc.expense

import android.content.Context

/**
 * 수집함 저장소. 목록 규칙은 [PendingPayments], 직렬화는 [PendingPaymentCodec] 이 하고
 * 여기는 SharedPreferences 입출력만 한다.
 *
 * 지출 데이터이긴 하지만 아직 «기록»이 아니라 확인 대기열이고, 비밀도 아니라
 * 평문 저장소를 쓴다([CategoryStore] 와 같은 판단).
 */
object PendingPaymentStore {

    private const val FILE = "pending_payments"
    private const val KEY = "items"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** 최신순. 오래된 것은 읽는 시점에 걸러진다. */
    fun load(context: Context, now: Long = System.currentTimeMillis()): List<PendingPayment> =
        PendingPayments.prune(PendingPaymentCodec.decode(prefs(context).getString(KEY, null)), now)

    fun add(context: Context, item: PendingPayment, now: Long = System.currentTimeMillis()) {
        save(context, PendingPayments.add(load(context, now), item, now))
    }

    fun remove(context: Context, id: String) {
        save(context, PendingPayments.remove(load(context), id))
    }

    /**
     * 방금 손으로 적은 기록과 같은 결제로 보이는 후보를 조용히 치운다([PendingPayments.matchRecorded]).
     * 잠금화면에서 적은 뒤 카드 문자가 와도 수집함이 같은 걸 다시 묻지 않게 한다.
     */
    fun removeRecorded(context: Context, amount: Long, at: Long = System.currentTimeMillis()) {
        val items: List<PendingPayment> = load(context, at)
        val id: String = PendingPayments.matchRecorded(items, amount, at) ?: return
        save(context, PendingPayments.remove(items, id))
    }

    /** 차단할 때 이미 쌓여 있던 그 출처의 후보도 함께 치운다. */
    fun removeFrom(context: Context, packageName: String? = null, sender: String? = null) {
        save(context, PendingPayments.removeFrom(load(context), packageName, sender))
    }

    fun clear(context: Context) {
        save(context, emptyList())
    }

    private fun save(context: Context, items: List<PendingPayment>) {
        prefs(context).edit().putString(KEY, PendingPaymentCodec.encode(items)).apply()
    }
}
