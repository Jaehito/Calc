package com.calc.expense

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.Executors

/**
 * 곳간 하나의 지출 내역 화면. 홈의 곳간 카드를 눌러 들어온다.
 *
 * 읽기는 [ExpenseHistory] seam 한 곳으로만 나간다 — 나중에 저장소를 바꿔도 이 액티비티는
 * 그대로다. 열 때와 «새로고침» 때 노션을 다시 읽는다.
 *
 * 항목을 누르면 수정·삭제할 수 있다. 수정·삭제 둘 다 [RecordExpense]로 나가고, 성공하면
 * 목록을 다시 읽어 화면을 맞춘다 — 로컬에서 직접 리스트를 고치지 않는 이유는 노션이
 * 진실이고 이 화면은 그걸 그대로 비추기 때문이다.
 */
class PurseHistoryActivity : ComponentActivity() {

    companion object {
        const val EXTRA_PURSE = "purse"
    }

    private val io = Executors.newSingleThreadExecutor()
    private val monthFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("M월", Locale.KOREA)

    private lateinit var purse: Purse
    /** 0 = 이번 달, 1 = 지난 달. */
    private var monthBack: Int by mutableStateOf(0)
    private var ui: HistoryUi by mutableStateOf(HistoryUi(loading = true))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val key: String = intent.getStringExtra(EXTRA_PURSE).orEmpty()
        purse = Purse.entries.firstOrNull { it.key == key } ?: Purse.PERSONAL

        setContent {
            HistoryScreen(
                ui = ui,
                categories = CategoryStore.load(this@PurseHistoryActivity),
                onBack = { finish() },
                onToggleMonth = {
                    monthBack = if (monthBack == 0) 1 else 0
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
        val title: String = "${settings.labelOf(purse)} 내역"
        val month: YearMonth = YearMonth.now().minusMonths(monthBack.toLong())
        val monthName: String = month.atDay(1).format(monthFormat)
        val shared: Boolean = purse == Purse.SHARED

        ui = HistoryUi(
            title = title,
            monthName = monthName,
            isThisMonth = monthBack == 0,
            shared = shared,
            loading = true,
        )

        val app = applicationContext
        io.execute {
            val result: ExpenseHistory.Result = try {
                ExpenseHistory.load(app, purse, month)
            } catch (e: Exception) {
                ExpenseHistory.Result.Err("오류: ${e.message ?: e.javaClass.simpleName}")
            }

            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                ui = when (result) {
                    is ExpenseHistory.Result.Ok -> HistoryUi(
                        title = title,
                        monthName = monthName,
                        isThisMonth = monthBack == 0,
                        shared = shared,
                        loading = false,
                        total = result.total,
                        groups = result.groups,
                        error = null,
                    )
                    is ExpenseHistory.Result.Err -> HistoryUi(
                        title = title,
                        monthName = monthName,
                        isThisMonth = monthBack == 0,
                        shared = shared,
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
                EditResult(ok = false, message = "오류: ${e.message ?: e.javaClass.simpleName}")
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
                DeleteResult(ok = false, message = "오류: ${e.message ?: e.javaClass.simpleName}")
            }

            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (result.ok) load() else ui = ui.copy(loading = false, error = result.message)
            }
        }
    }
}
