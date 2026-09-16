package com.calc.expense

import android.content.Context

/**
 * 결제 기록 저장소. 목록 규칙은 [PaymentLogs], 직렬화는 [PaymentLogCodec] 이 하고
 * 여기는 SharedPreferences 입출력만 한다.
 *
 * **폰 밖으로 나가지 않는다.** 저장소에 올리지도, 내보내기에 담지도 않는다 — 이 목록의
 * 쓸모는 «달마다 반복되나»를 세는 것뿐이라 어디서도 다시 읽을 필요가 없고,
 * 확인하지 않은 결제까지 들어 있어 남아 있을 이유가 가장 적은 데이터다.
 * 계정이 바뀌면 [AccountScope] 가 통째로 지운다.
 */
object PaymentLogStore {

    private const val FILE = "payment_log"
    private const val KEY = "items"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** 최신순. 보관 기간이 지난 것은 읽는 시점에 걸러진다. */
    fun load(context: Context, now: Long = System.currentTimeMillis()): List<PaymentLogEntry> =
        PaymentLogs.prune(PaymentLogCodec.decode(prefs(context).getString(KEY, null)), now)

    fun add(context: Context, item: PaymentLogEntry, now: Long = System.currentTimeMillis()) {
        save(context, PaymentLogs.add(load(context, now), item, now))
    }

    /** 계정이 바뀌면 앞사람이 무엇을 결제했는지도 남의 이야기다. */
    fun clear(context: Context) {
        save(context, emptyList())
    }

    private fun save(context: Context, items: List<PaymentLogEntry>) {
        prefs(context).edit().putString(KEY, PaymentLogCodec.encode(items)).apply()
    }
}
