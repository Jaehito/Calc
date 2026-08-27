package com.calc.expense

import android.content.Context
import android.content.SharedPreferences
import java.time.LocalDate

/**
 * 곳간 상태 저장소.
 *
 * 지출 기록과 달리 곳간은 Notion 에 없다. 여기서만 산다. 그래서 값이 사라지면
 * 곳간은 0 부터 다시 쌓인다 — 과거를 소급해 복원하지 않는다.
 *
 * 키에 [Purse.key] 를 붙여 개인과 공용을 따로 담는다.
 */
object BudgetStore {

    private const val FILE = "expense_budget"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun load(context: Context, purse: Purse): BudgetState? {
        val p = prefs(context)
        val settled = p.getString("${purse.key}.settledThrough", null) ?: return null

        return try {
            BudgetState(
                monthlyBudget = p.getLong("${purse.key}.monthlyBudget", 0L),
                dailyRate = p.getLong("${purse.key}.dailyRate", 0L),
                vault = p.getLong("${purse.key}.vault", 0L),
                settledThrough = LocalDate.parse(settled),
            )
        } catch (_: Exception) {
            // 저장값이 깨졌으면 없는 것으로 본다. 곳간이 0 부터 다시 쌓이는 편이
            // 말도 안 되는 숫자를 보여주는 것보다 낫다.
            null
        }
    }

    fun save(context: Context, state: BudgetState, purse: Purse) {
        prefs(context).edit()
            .putLong("${purse.key}.monthlyBudget", state.monthlyBudget)
            .putLong("${purse.key}.dailyRate", state.dailyRate)
            .putLong("${purse.key}.vault", state.vault)
            .putString("${purse.key}.settledThrough", state.settledThrough.toString())
            .apply()
    }

    fun clear(context: Context, purse: Purse) {
        prefs(context).edit()
            .remove("${purse.key}.monthlyBudget")
            .remove("${purse.key}.dailyRate")
            .remove("${purse.key}.vault")
            .remove("${purse.key}.settledThrough")
            .apply()
    }
}
