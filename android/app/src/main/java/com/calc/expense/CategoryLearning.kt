package com.calc.expense

import android.content.Context
import java.time.LocalDate
import java.time.YearMonth

/**
 * 지난 기록에서 «이름 → 카테고리»를 배워 [CategoryMemoryStore] 에 보탠다.
 *
 * 기억은 폰 안에만 있어서 다시 깔거나 폰을 바꾸면 처음부터였다. 기록 자체는 계정에 있으니 거기서 다시
 * 배운다. 공용 지갑 기록도 읽는다 — 배우자가 정한 카테고리까지 배워 둘이 같은 습관을 쓴다.
 *
 * 하루 한 번, 앱을 열어 숫자를 맞출 때([HomeActivity] 의 resync) 백그라운드에서 돈다. 이 폰에서 정한
 * 기억은 덮지 않는다([CategoryMemories.seed]).
 */
object CategoryLearning {

    private const val FILE = "category_learning"
    private const val KEY_LEARNED_ON = "learnedOn"

    /** 거슬러 읽는 달 수(이번 달 포함). */
    private const val MONTHS_BACK = 6L

    /** 오늘 아직 안 배웠으면 배운다. 반드시 백그라운드 스레드에서 부른다(저장소 읽기가 블로킹). */
    fun learnIfDue(context: Context, purses: List<Purse>, today: LocalDate = LocalDate.now()) {
        val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_LEARNED_ON, null) == today.toString()) return

        val rows = ArrayList<ExpenseRow>()
        val thisMonth: YearMonth = YearMonth.from(today)
        for (purse in purses) {
            var back: Long = MONTHS_BACK - 1
            while (back >= 0) {
                // 한 달이라도 못 읽으면 오늘은 접는다 — 반쪽만 배우고 «배웠다»고 적어 두지 않게.
                val month: List<ExpenseRow> =
                    FirestoreExpenseReader.monthRows(context, purse, thisMonth.minusMonths(back)) ?: return
                rows.addAll(month)
                back--
            }
        }
        rows.sortBy { it.date }
        CategoryMemoryStore.seed(context, CategoryMemories.learn(rows.map { it.name to it.category }))
        prefs.edit().putString(KEY_LEARNED_ON, today.toString()).apply()
    }

    fun clear(context: Context) {
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
