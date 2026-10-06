package com.calc.expense

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.LocalDate
import java.util.concurrent.Executors

/**
 * 곳간 하나의 지출 내역 화면. 홈의 곳간 카드를 눌러 들어온다.
 * 통계의 막대·달력 날짜를 누르면 [EXTRA_DAY] 를 실어 **그 하루만** 보여 준다.
 *
 * 읽기는 [ExpenseHistory] seam 한 곳으로만 나간다 — 나중에 저장소를 바꿔도 이 액티비티는
 * 그대로다. 열 때와 «새로고침» 때 저장소를 다시 읽는다.
 *
 * 항목을 누르면 수정·삭제할 수 있다. 수정·삭제 둘 다 [RecordExpense]로 나가고, 성공하면
 * 목록을 다시 읽어 화면을 맞춘다 — 로컬에서 직접 리스트를 고치지 않는 이유는 저장소가
 * 진실이고 이 화면은 그걸 그대로 비추기 때문이다.
 */
class PurseHistoryActivity : ComponentActivity() {

    companion object {
        const val EXTRA_PURSE = "purse"
        /** «2026-10-03». 있으면 그 하루만 본다. */
        const val EXTRA_DAY = "day"
    }

    private val io = Executors.newSingleThreadExecutor()

    private lateinit var purse: Purse
    /** 하루만 볼 때 그 날. null 이면 주기 단위. */
    private var day: LocalDate? = null
    /** 0 = 이번 주기, 1 = 지난 주기. 달력 달이 아니라 월급날 기준이다. */
    private var cycleBack: Int by mutableStateOf(0)
    private var ui: HistoryUi by mutableStateOf(HistoryUi(loading = true))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val key: String = intent.getStringExtra(EXTRA_PURSE).orEmpty()
        purse = Purse.entries.firstOrNull { it.key == key } ?: Purse.PERSONAL
        day = intent.getStringExtra(EXTRA_DAY)?.let { raw ->
            try {
                LocalDate.parse(raw)
            } catch (_: Exception) {
                null
            }
        }

        setContent {
            HistoryScreen(
                ui = ui,
                categories = CategoryStore.load(this@PurseHistoryActivity),
                onBack = { finish() },
                onToggleMonth = {
                    cycleBack = if (cycleBack == 0) 1 else 0
                    load()
                },
                onRefresh = { load() },
                onEditRow = { row, name, amount, category -> editRow(row, name, amount, category) },
                onDeleteRow = { row -> deleteRow(row) },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    override fun onDestroy() {
        io.shutdown()
        super.onDestroy()
    }

    private fun load() {
        val settings: Settings = SettingsStore.load(this)
        val oneDay: LocalDate? = day
        val title: String =
            if (oneDay != null) oneDay.format(L10n.fullDay())
            else tr("${settings.labelOf(purse)} 내역", "${settings.labelOf(purse)} history", "Historial de ${settings.labelOf(purse)}")
        val cycle: BudgetCycle =
            if (oneDay != null) BudgetCycle(oneDay, oneDay.plusDays(1))
            else Payday.cycleBefore(LocalDate.now(), settings.payDayOf(purse), cycleBack)
        val singleDay: Boolean = oneDay != null
        val periodName: String = if (cycleBack == 0) tr("이번 주기", "This cycle", "Este ciclo") else tr("지난 주기", "Last cycle", "Ciclo anterior")
        val periodRange: String = StatusText.cycleRange(cycle)
        val shared: Boolean = purse == Purse.SHARED

        ui = HistoryUi(
            title = title,
            periodName = periodName,
            periodRange = periodRange,
            isThisPeriod = cycleBack == 0,
            shared = shared,
            singleDay = singleDay,
            loading = true,
        )

        val app = applicationContext
        io.execute {
            val result: ExpenseHistory.Result = try {
                ExpenseHistory.load(app, purse, cycle)
            } catch (e: Exception) {
                ExpenseHistory.Result.Err(StatusText.error(e))
            }

            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                ui = when (result) {
                    is ExpenseHistory.Result.Ok -> HistoryUi(
                        title = title,
                        periodName = periodName,
                        periodRange = periodRange,
                        isThisPeriod = cycleBack == 0,
                        shared = shared,
                        singleDay = singleDay,
                        loading = false,
                        total = result.total,
                        groups = result.groups,
                        error = null,
                    )
                    is ExpenseHistory.Result.Err -> HistoryUi(
                        title = title,
                        periodName = periodName,
                        periodRange = periodRange,
                        isThisPeriod = cycleBack == 0,
                        shared = shared,
                        singleDay = singleDay,
                        loading = false,
                        error = result.message,
                    )
                }
            }
        }
    }

    /** 옛 기록 한 줄을 고친다. 성공하면 목록을 다시 읽어 맞춘다. */
    private fun editRow(row: ExpenseRow, name: String, amount: Long, category: String) {
        ui = ui.copy(loading = true)
        val app = applicationContext
        val newExpense = Expense(name, amount, category)

        io.execute {
            val result: EditResult = try {
                RecordExpense.edit(app, purse, row.date, row.id, row.amount, newExpense)
            } catch (e: Exception) {
                EditResult(ok = false, message = StatusText.error(e))
            }

            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (result.ok) load() else ui = ui.copy(loading = false, error = result.message)
            }
        }
    }

    /** 옛 기록 한 줄을 지운다. 성공하면 목록을 다시 읽어 맞춘다. */
    private fun deleteRow(row: ExpenseRow) {
        ui = ui.copy(loading = true)
        val app = applicationContext

        io.execute {
            val result: DeleteResult = try {
                RecordExpense.delete(app, row.id, purse.key, row.date, row.amount)
            } catch (e: Exception) {
                DeleteResult(ok = false, message = StatusText.error(e))
            }

            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (result.ok) load() else ui = ui.copy(loading = false, error = result.message)
            }
        }
    }
}
