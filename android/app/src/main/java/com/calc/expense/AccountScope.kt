package com.calc.expense

import android.content.Context

/**
 * 로그인한 계정이 바뀌면 **앞사람의 돈 이야기를 지운다.**
 *
 * 지출은 Firestore 의 계정 아래에 있어 로그인만 바꾸면 저절로 갈리지만, 예산·고정비·곳간
 * 잔액·수집함처럼 폰에만 사는 것들은 그대로 남는다. 그래서 다른 계정으로 들어와도 앞사람의
 * 챌린지 금액과 고정비를 그대로 물려받고, 온보딩도 «이미 물어봤다»로 건너뛰어졌다.
 *
 * **처음 로그인은 지우지 않는다.** 저장된 uid 가 없다는 건 이 기능이 생기기 전부터 쓰던
 * 사람이라는 뜻이고, 그 사람의 예산을 업데이트했다고 날려 버리면 안 된다.
 *
 * 남기는 것은 **기기 취향**뿐이다 — 알림을 켜 뒀는지([NotificationState])와 카테고리 칩
 * 목록([CategoryStore]). 계정이 바뀌었다고 알림을 다시 켜게 하거나 칩을 다시 만들게 할
 * 이유가 없다. 차단한 발신자는 반대로 지운다 — 내가 막아 둔 카드사가 다음 사람에게도
 * 막혀 있으면 그 사람은 이유도 모른 채 결제 알림을 못 받는다.
 */
object AccountScope {

    private const val FILE = "account_scope"
    private const val KEY_UID = "uid"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /**
     * 지금 로그인한 [uid] 를 직전 것과 맞춰 본다. 달라졌으면 비우고 true 를 돌려준다.
     *
     * 로그인 경로가 여럿이라(자동 재로그인·구글 로그인) [LoginActivity] 한 곳에서만 부른다 —
     * 화면마다 부르면 «언제 지워지나»가 흩어진다.
     */
    fun syncTo(context: Context, uid: String?): Boolean {
        if (uid.isNullOrBlank()) return false

        val stored = prefs(context)
        val last: String? = stored.getString(KEY_UID, null)
        stored.edit().putString(KEY_UID, uid).apply()

        if (last == null || last == uid) return false
        wipe(context)
        return true
    }

    /** 그 계정의 것이었던 로컬 데이터. 여기 목록이 «계정에 속한 것»의 정의다. */
    private fun wipe(context: Context) {
        SettingsStore.clear(context)
        FixedCostStore.clear(context)
        SpendingCache.clear(context)
        for (purse in Purse.entries) BudgetStore.clear(context, purse)
        PendingPaymentStore.clear(context)
        PaymentBlocklist.clear(context)
        ReminderState.clearAccountState(context)
        CycleGradeStore.clear(context)
        DailyGradeStore.clear(context)
        ChallengeStore.clear(context)
        HouseholdStore.setHouseholdId(context, null)
    }
}
