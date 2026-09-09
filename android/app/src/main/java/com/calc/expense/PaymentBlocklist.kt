package com.calc.expense

import android.content.Context

/**
 * 수집함에 담지 않을 출처 목록.
 *
 * 두 단위로 막는다 — **발신자**(문자 발신번호·카드사 이름 = 알림 제목)와 **앱**(패키지).
 * 특정 카드 문자만 막고 싶을 때와, 광고성 앱을 통째로 막고 싶을 때가 다르기 때문이다.
 * 막는 것은 «수집»뿐이다. 결제 리마인더나 다른 앱 동작에는 영향을 주지 않는다.
 *
 * SharedPreferences 의 StringSet 은 돌려받은 집합을 그대로 고치면 안 된다(문서화된 함정) —
 * 언제나 새 집합을 만들어 넣는다.
 */
object PaymentBlocklist {

    private const val FILE = "payment_blocklist"
    private const val KEY_PACKAGES = "packages"
    private const val KEY_SENDERS = "senders"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun blockedPackages(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_PACKAGES, null).orEmpty().toSet()

    fun blockedSenders(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_SENDERS, null).orEmpty().toSet()

    /** 이 알림을 수집함에 담지 않아야 하는가. */
    fun isBlocked(context: Context, packageName: String, sender: String): Boolean {
        if (packageName.isNotEmpty() && packageName in blockedPackages(context)) return true
        return sender.isNotEmpty() && sender in blockedSenders(context)
    }

    fun blockPackage(context: Context, packageName: String) {
        if (packageName.isEmpty()) return
        put(context, KEY_PACKAGES, blockedPackages(context) + packageName)
    }

    fun blockSender(context: Context, sender: String) {
        if (sender.isEmpty()) return
        put(context, KEY_SENDERS, blockedSenders(context) + sender)
    }

    fun unblockPackage(context: Context, packageName: String) {
        put(context, KEY_PACKAGES, blockedPackages(context) - packageName)
    }

    fun unblockSender(context: Context, sender: String) {
        put(context, KEY_SENDERS, blockedSenders(context) - sender)
    }

    private fun put(context: Context, key: String, value: Set<String>) {
        prefs(context).edit().putStringSet(key, value).apply()
    }

    /** 계정이 바뀌면 차단 목록도 비운다 — 내가 막은 카드사가 다음 사람에게도 막혀 있으면 안 된다. */
    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }
}
