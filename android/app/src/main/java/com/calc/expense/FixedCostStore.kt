package com.calc.expense

import android.content.Context

/**
 * 고정비 계획 저장소. 계산은 [FixedCostPlan], 직렬화는 [FixedCostCodec] 이 하고
 * 여기는 SharedPreferences 입출력만 한다.
 *
 * 월급이 들어 있지만 비밀 저장소를 쓰지 않는다 — 노션 토큰처럼 남의 계정을 여는 열쇠가
 * 아니라 내 숫자이고, 앱 전용 저장소라 다른 앱이 읽지 못한다([CategoryStore] 와 같은 판단).
 */
object FixedCostStore {

    private const val FILE = "fixed_cost"
    private const val KEY_PLAN = "plan"

    /** 첫 시작 파이프라인을 «봤나». 건너뛴 사람에게 다시 들이밀지 않으려고 따로 둔다. */
    private const val KEY_ASKED = "asked"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun load(context: Context): FixedCostPlan =
        FixedCostCodec.decode(prefs(context).getString(KEY_PLAN, null))

    fun save(context: Context, plan: FixedCostPlan) {
        val cleaned = FixedCostPlan(
            monthlyIncome = maxOf(0L, plan.monthlyIncome),
            items = FixedCosts.clean(plan.items),
        )
        prefs(context).edit()
            .putString(KEY_PLAN, FixedCostCodec.encode(cleaned))
            .putBoolean(KEY_ASKED, true)
            .apply()
    }

    /**
     * 첫 시작 파이프라인을 이미 보여줬는지.
     *
     * 건너뛴 것도 «봤다»로 친다 — 앱을 열 때마다 다시 물으면 그건 건너뛰기가 아니다.
     * 다시 하고 싶으면 홈·설정의 «고정비로 계산하기»로 사용자가 직접 들어온다.
     */
    fun wasAsked(context: Context): Boolean = prefs(context).getBoolean(KEY_ASKED, false)

    /** 건너뛰었을 때. 계획은 비워 두고 «물어봤다»만 남긴다. */
    fun markAsked(context: Context) {
        prefs(context).edit().putBoolean(KEY_ASKED, true).apply()
    }
}
