package com.calc.expense

import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.calc.expense.databinding.ActivityQuickInputBinding
import java.time.LocalTime
import java.util.concurrent.Executors

/**
 * 알림 카드를 누르면 뜨는 빠른 입력 화면.
 *
 * 알림 안에는 입력창을 미리 펼쳐 둘 수 없다 — RemoteInput 은 액션 탭으로만 열리고,
 * 알림 커스텀 레이아웃은 EditText 를 지원하지 않는다. 그래서 알림 카드 전체를
 * 이 화면을 여는 버튼으로 쓴다. 탭 수는 같지만 조준할 필요가 없고,
 * 무엇보다 **적는 동안 오늘 쓸 수 있는 돈이 보인다.**
 *
 * 기록해도 화면을 닫지 않는다. 장보기처럼 여러 건을 적을 때 엔터마다 한 건씩 들어가고
 * 위의 숫자가 그때그때 줄어든다. 여러 줄을 모아 한 번에 보내면 그 감각이 한 번뿐이고,
 * 엔터가 줄바꿈이 되어 한 건만 적을 때도 전송 버튼을 눌러야 한다.
 *
 * 자동으로 닫지 않는다 — "몇 초 뒤 닫힘" 은 두 번째 항목을 적으려는 순간 닫히면 최악이다.
 */
class QuickInputActivity : AppCompatActivity() {

    private lateinit var ui: ActivityQuickInputBinding
    private val io = Executors.newSingleThreadExecutor()

    private var purses: List<Purse> = emptyList()
    private var selected: Purse = Purse.PERSONAL
    private var submitting: Boolean = false

    /** 이 화면을 연 뒤로 기록한 건수. 결과 줄에 «2건째» 를 붙일지 정한다. */
    private var recorded: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()

        ui = ActivityQuickInputBinding.inflate(layoutInflater)
        setContentView(ui.root)

        window.setGravity(Gravity.BOTTOM)
        window.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
        )
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)

        // 카드 바깥을 누르면 닫는다.
        ui.sheetRoot.setOnClickListener { finish() }
        ui.buttonDone.setOnClickListener { finish() }

        setUpPurses()
        refreshNumbers()

        ui.inputExpense.requestFocus()
        // actionSend 로 둔다. actionDone 은 일부 키보드가 처리 후 키보드를 내려버려
        // 다음 건을 이어 적을 수 없다.
        ui.inputExpense.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND || actionId == EditorInfo.IME_ACTION_DONE) {
                submit()
                true
            } else {
                false
            }
        }
    }

    override fun onDestroy() {
        io.shutdown()
        super.onDestroy()
    }

    /** 잠금 해제 없이 뜨도록 요청한다. 제조사 정책이 막으면 인증을 먼저 요구할 수 있다. */
    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
    }

    /** 연결된 곳간이 하나뿐이면 고르게 하지 않는다. 멈칫하는 3초가 이탈 지점이다. */
    private fun setUpPurses() {
        val settings = SettingsStore.load(this)
        purses = settings.linkedPurses
        selected = purses.firstOrNull() ?: Purse.PERSONAL

        if (purses.size < 2) {
            ui.groupPurse.visibility = View.GONE
            return
        }

        ui.groupPurse.visibility = View.VISIBLE
        ui.buttonPursePersonal.text = settings.labelOf(Purse.PERSONAL)
        ui.buttonPurseShared.text = settings.labelOf(Purse.SHARED)
        ui.groupPurse.check(
            if (selected == Purse.SHARED) R.id.buttonPurseShared else R.id.buttonPursePersonal
        )

        ui.groupPurse.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            selected =
                if (checkedId == R.id.buttonPurseShared) Purse.SHARED else Purse.PERSONAL
            refreshNumbers()
        }
    }

    /** 고른 곳간의 숫자를 입력창 위에 띄운다. 적으면서 판단할 수 있게 하는 것이 이 화면의 존재 이유다. */
    private fun refreshNumbers() {
        val snapshot: LedgerSnapshot? = Ledger.snapshot(this, selected)

        if (snapshot == null) {
            ui.textCaption.text = "예산을 정하지 않은 곳간"
            ui.textAvailable.text = "—"
            ui.textBreakdown.visibility = View.GONE
            return
        }

        val available: Long = snapshot.available
        if (available >= 0L) {
            ui.textCaption.text = "오늘 쓸 수 있는 돈"
            ui.textAvailable.text = StatusText.won(available)
        } else {
            ui.textCaption.text = "오늘 초과"
            ui.textAvailable.text = StatusText.won(-available)
        }

        ui.textBreakdown.visibility = View.VISIBLE
        ui.textBreakdown.text = StatusText.untilTarget(snapshot)
    }

    private fun submit() {
        if (submitting) return
        val text: String = ui.inputExpense.text?.toString().orEmpty().trim()
        if (text.isEmpty()) return

        submitting = true
        ui.inputExpense.isEnabled = false
        showResult("기록 중…", isError = false)

        val app = applicationContext
        val purseKey: String = selected.key
        val now: String = LocalTime.now().format(ReplyReceiver.TIME_FORMAT)

        io.execute {
            val result: RecordResult = try {
                RecordExpense.submit(app, text, purseKey, now)
            } catch (e: Exception) {
                RecordResult(
                    ok = false,
                    lines = StatusText.failed("오류: ${e.message ?: e.javaClass.simpleName}", now),
                )
            }

            // 알림도 같은 결과로 갱신한다. 화면을 닫아도 숫자가 남아 있게.
            NotificationHelper.show(app, result.lines)

            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread

                submitting = false
                ui.inputExpense.isEnabled = true
                ui.inputExpense.requestFocus()

                if (result.ok) {
                    // 입력창만 비우고 화면은 그대로 둔다. 다음 건을 바로 이어 적을 수 있게.
                    recorded++
                    ui.inputExpense.setText("")
                    ui.buttonDone.visibility = View.VISIBLE
                    refreshNumbers()

                    val e: Expense? = result.expense
                    showResult(
                        if (e == null) "기록됨" else StatusText.entered(e.name, e.amount, recorded),
                        isError = false,
                        highlight = true,
                    )
                } else {
                    // 실패하면 입력한 내용을 그대로 둔다. 고쳐서 다시 엔터를 누르면 된다.
                    showResult(result.lines.summary, isError = true)
                }
            }
        }
    }

    private fun showResult(message: String, isError: Boolean, highlight: Boolean = false) {
        ui.rowResult.visibility = View.VISIBLE
        ui.textResult.text = message

        val color: Int = when {
            isError -> R.color.app_over
            highlight -> R.color.app_accent
            else -> R.color.app_muted
        }
        ui.textResult.setTextColor(ContextCompat.getColor(this, color))
    }
}
