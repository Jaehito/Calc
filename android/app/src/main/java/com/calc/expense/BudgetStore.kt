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
 * 곳간이 공동·개인 둘로 갈릴 것을 감안해 키에 [purse] 를 붙여 둔다. 나중에 값 하나만
 * 더 넘기면 되고 저장된 데이터를 옮길 필요가 없다.
 */
object BudgetStore {

    private const val FILE = "expense_budget"

    /** 기본 곳간. 부부 모드가 붙으면 "shared" / "personal" 이 추가된다. */
    const val MAIN = "main"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun load(context: Context, purse: String = MAIN): BudgetState? {
        val p = prefs(context)
        val settled = p.getString("$purse.settledThrough", null) ?: return null

        return try {
            BudgetState(
                monthlyBudget = p.getLong("$purse.monthlyBudget", 0L),
                dailyRate = p.getLong("$purse.dailyRate", 0L),
                vault = p.getLong("$purse.vault", 0L),
                settledThrough = LocalDate.parse(settled),
            )
        } catch (_: Exception) {
            // 저장값이 깨졌으면 없는 것으로 본다. 곳간이 0 부터 다시 쌓이는 편이
            // 말도 안 되는 숫자를 보여주는 것보다 낫다.
            null
        }
    }

    fun save(context: Context, state: BudgetState, purse: String = MAIN) {
        prefs(context).edit()
            .putLong("$purse.monthlyBudget", state.monthlyBudget)
            .putLong("$purse.dailyRate", state.dailyRate)
            .putLong("$purse.vault", state.vault)
            .putString("$purse.settledThrough", state.settledThrough.toString())
            .apply()
    }

    fun clear(context: Context, purse: String = MAIN) {
        prefs(context).edit()
            .remove("$purse.monthlyBudget")
            .remove("$purse.dailyRate")
            .remove("$purse.vault")
            .remove("$purse.settledThrough")
            .apply()
    }
}
