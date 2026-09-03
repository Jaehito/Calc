package com.calc.expense

import android.app.KeyguardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.calc.expense.databinding.ActivityQuickInputBinding
import com.google.android.material.chip.Chip
import java.time.LocalDate
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

    companion object {
        /**
         * 이 값을 true 로 실어 보내면 잠금 풀린 상태여도 홈으로 돌리지 않고 이 화면을 그대로 연다.
         * 결제 리마인더(«방금 쓴 거 있어요?»)처럼 "이 결제를 지금 바로 적자"가 목적인 진입점이
         * 쓴다 — 그 알림은 잠금 여부와 무관하게 늘 입력 화면으로 가야 제 역할을 한다.
         */
        const val EXTRA_FORCE_INPUT = "force_input"

        /** «없음» 칩의 표시 이름. 실제 카테고리 값(노션에 쓰는 값)은 빈 문자열이다. */
        private const val CATEGORY_NONE = "없음"
    }

    private lateinit var ui: ActivityQuickInputBinding
    private val io = Executors.newSingleThreadExecutor()

    private var purses: List<Purse> = emptyList()
    private var selected: Purse = Purse.PERSONAL
    private var submitting: Boolean = false

    /** 지금 고른 카테고리. 안 고르면 빈 문자열. 다음 입력까지 유지된다. */
    private var selectedCategory: String = ""

    /** 칩 표시 목록(«없음» 포함, 맨 앞). [CategoryClassifier] 는 이 안에서만 고른다. */
    private var categoryLabels: List<String> = emptyList()

    /** 사용자가 칩을 직접 눌렀는지. 누르고 나면 그 뒤로는 자동 분류가 끼어들지 않는다. */
    private var manualCategoryOverride: Boolean = false

    /** 코드로 칩 체크를 바꾸는 동안, 그 변화가 «사용자가 눌렀다» 로 오인되지 않게 막는다. */
    private var suppressCategoryListener: Boolean = false

    /** 이 화면을 연 뒤로 기록한 건수. 결과 줄에 «2건째» 를 붙일지 정한다. */
    private var recorded: Int = 0

    /**
     * 이 화면에서 방금 적은 항목. ✕ 로 지울 수 있게 Notion 페이지 id 를 들고 있는다.
     * 창을 닫으면 사라진다 — 지난 기록은 로컬에 항목 단위로 저장하지 않기 때문이다.
     */
    private data class Entry(
        val name: String,
        val amount: Long,
        val pageId: String,
        val purse: Purse,
        val day: LocalDate,
        val row: View,
    )

    private val entries = mutableListOf<Entry>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 잠금이 풀린 상태에서 상시 카드를 눌렀다면 빠른 입력 대신 앱 메인화면을 연다.
        // 잠긴 상태에서만 이 시트가 뜬다 — 그게 이 화면이 존재하는 이유(잠금 해제 없이 적기)다.
        // 결제 리마인더처럼 항상 입력으로 가야 하는 진입점은 EXTRA_FORCE_INPUT 으로 이걸 건너뛴다.
        if (!intent.getBooleanExtra(EXTRA_FORCE_INPUT, false) && !isDeviceLocked()) {
            startActivity(Intent(this, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            finish()
            return
        }

        showOverLockScreen()

        ui = ActivityQuickInputBinding.inflate(layoutInflater)
        setContentView(ui.root)

        // 창은 화면 전체를 덮고, 카드는 레이아웃에서 아래에 붙는다.
        // setLayout 은 floating 창에서만 먹히므로 쓰지 않는다.
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        keepSheetAboveSystemBars()

        // 카드 바깥(어두운 곳)을 누르면 닫는다. 따로 «완료» 버튼을 두지 않는다.
        ui.sheetRoot.setOnClickListener { finish() }

        setUpPurses()
        setUpCategories()
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

    /**
     * 키보드와 내비게이션 바가 카드를 가리지 않게 아래 여백을 직접 준다.
     *
     * targetSdk 35 라 안드로이드 15 에서는 창이 무조건 시스템 바 아래까지 그려지고,
     * 3버튼 내비게이션이면 시스템이 그 위에 **반투명 회색 띠**를 덮는다 — 이게 결과 줄을
     * 가리던 바다. 게다가 반투명 창에서는 adjustResize 가 먹지 않아 키보드가 입력창을 덮는다.
     *
     * 그래서 회색 띠를 끄고(카드가 흰색이라 대비 보정이 필요 없다) 인셋만큼 카드 아래
     * 여백을 직접 준다. 키보드가 올라오면 그 높이가 내비 바보다 크므로 둘 중 큰 값만 쓴다.
     */
    private fun keepSheetAboveSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        // 카드가 항상 흰색이므로(values-night 없음) 내비 버튼·제스처 바는 어둡게 그린다.
        WindowCompat.getInsetsController(window, ui.root).isAppearanceLightNavigationBars = true

        val basePadding: Int = ui.sheet.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(ui.sheet) { view, insets ->
            val ime: Int = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val navigation: Int =
                insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            view.updatePadding(bottom = basePadding + maxOf(ime, navigation))
            insets
        }
        ViewCompat.requestApplyInsets(ui.sheet)
    }

    /** 지금 폰이 잠겨 있는지. 카드를 누른 순간의 상태를 봐야 하므로 인텐트를 만들 때가 아니라 여기서 확인한다. */
    private fun isDeviceLocked(): Boolean =
        getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true

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

    /**
     * 카테고리 칩을 만든다. «없음» 이 맨 앞이고 기본 선택이다. 목록은 앱이 갖는다
     * (설정에서 편집) — 잠금화면에서 네트워크 없이 바로 그린다.
     *
     * 이름을 입력하면 [CategoryClassifier] 가 낱말을 보고 짐작해 칩을 자동으로 켠다
     * (네트워크·LLM 없음). 사용자가 칩을 직접 누르면 [manualCategoryOverride] 가 서서
     * 그 뒤로는 자동이 끼어들지 않는다 — 스스로 고른 걸 되돌리면 안 되기 때문이다.
     *
     * 각 칩에 [View.generateViewId] 로 id 를 준다. id 가 없으면(NO_ID) `findViewById` 가
     * 항상 null 을 돌려줘 어느 칩이 눌렸는지 알 수 없다.
     */
    private fun setUpCategories() {
        categoryLabels = listOf(CATEGORY_NONE) + CategoryStore.load(this)
        ui.groupCategory.removeAllViews()

        for (label in categoryLabels) {
            val chip: Chip =
                layoutInflater.inflate(R.layout.item_category_chip, ui.groupCategory, false) as Chip
            chip.id = View.generateViewId()
            chip.text = label
            chip.isChecked = label == CATEGORY_NONE
            ui.groupCategory.addView(chip)
        }

        ui.groupCategory.setOnCheckedStateChangeListener { _, checkedIds ->
            if (suppressCategoryListener) return@setOnCheckedStateChangeListener
            manualCategoryOverride = true
            selectedCategory = categoryValueOf(checkedIds.firstOrNull())
        }

        ui.inputExpense.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (manualCategoryOverride) return
                applyAutoCategory(s?.toString().orEmpty())
            }
        })
    }

    /** 체크된 칩 id 를 실제 카테고리 값으로. «없음» 이면 빈 문자열(카테고리 없음)이다. */
    private fun categoryValueOf(checkedId: Int?): String {
        if (checkedId == null) return ""
        val label: String = ui.groupCategory.findViewById<Chip>(checkedId)?.text?.toString().orEmpty()
        return if (label == CATEGORY_NONE) "" else label
    }

    /** [text] 에서 카테고리를 짐작해 칩을 켠다. 사용자가 아직 손대지 않았을 때만 불린다. */
    private fun applyAutoCategory(text: String) {
        val guess: String? = CategoryClassifier.classify(text, categoryLabels)
        selectChip(guess ?: CATEGORY_NONE)
        selectedCategory = guess.orEmpty()
    }

    /** [label] 이름의 칩을 켠다. 이 호출로는 [manualCategoryOverride] 가 서지 않는다. */
    private fun selectChip(label: String) {
        for (i in 0 until ui.groupCategory.childCount) {
            val chip: Chip = ui.groupCategory.getChildAt(i) as? Chip ?: continue
            if (chip.text.toString() != label) continue
            suppressCategoryListener = true
            ui.groupCategory.check(chip.id)
            suppressCategoryListener = false
            return
        }
    }

    /**
     * 고른 곳간의 숫자를 입력창 위에 띄우고 그 스냅샷을 돌려준다.
     * 적으면서 판단할 수 있게 하는 것이 이 화면의 존재 이유다.
     *
     * 큰 숫자에도 색을 입힌다 — 넘겼으면 빨강, 남았으면 초록. 글자를 읽기 전에 눈에 들어온다.
     */
    private fun refreshNumbers(): LedgerSnapshot? {
        val snapshot: LedgerSnapshot? = Ledger.snapshot(this, selected)

        if (snapshot == null) {
            ui.textCaption.text = "예산을 정하지 않은 곳간"
            ui.textAvailable.text = "—"
            ui.textAvailable.setTextColor(colorOf(Tone.NEUTRAL))
            ui.textBreakdown.visibility = View.GONE
            return null
        }

        val available: Long = snapshot.available
        if (snapshot.isOver) {
            ui.textCaption.text = "오늘 초과"
            ui.textAvailable.text = StatusText.won(-available)
        } else {
            ui.textCaption.text = "오늘 쓸 수 있는 돈"
            ui.textAvailable.text = StatusText.won(available)
        }
        ui.textAvailable.setTextColor(colorOf(if (snapshot.isOver) Tone.OVER else Tone.REMAINING))

        ui.textBreakdown.visibility = View.VISIBLE
        ui.textBreakdown.text = StatusText.untilTarget(snapshot)
        return snapshot
    }

    private fun submit() {
        if (submitting) return
        val text: String = ui.inputExpense.text?.toString().orEmpty().trim()
        if (text.isEmpty()) return

        submitting = true
        ui.inputExpense.isEnabled = false
        showResult("기록 중…", Tone.NEUTRAL)

        val app = applicationContext
        val purse: Purse = selected
        val day: LocalDate = LocalDate.now()
        val now: String = LocalTime.now().format(ReplyReceiver.TIME_FORMAT)

        io.execute {
            val result: RecordResult = try {
                RecordExpense.submit(app, text, purse.key, now, day, selectedCategory)
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
                    val after: LedgerSnapshot? = refreshNumbers()

                    val e: Expense? = result.expense
                    if (e != null) addEntryRow(e, result.pageId, purse, day)
                    showResult(
                        if (e == null) "기록됨" else StatusText.entered(e.name, e.amount, recorded),
                        Tone.of(ok = true, snapshot = after),
                    )
                } else {
                    // 실패하면 입력한 내용을 그대로 둔다. 고쳐서 다시 엔터를 누르면 된다.
                    showResult(result.lines.summary, Tone.of(ok = false, snapshot = null))
                }
            }
        }
    }

    /** 방금 적은 항목을 목록 맨 위에 한 줄 추가한다. ✕ 를 누르면 [removeEntry] 로 지운다. */
    private fun addEntryRow(expense: Expense, pageId: String, purse: Purse, day: LocalDate) {
        val row: View = layoutInflater.inflate(R.layout.item_entry_row, ui.listEntries, false)
        val label: android.widget.TextView = row.findViewById(R.id.textEntry)
        label.text = if (expense.category.isBlank()) {
            "${expense.name}  ${StatusText.won(expense.amount)}"
        } else {
            "${expense.name} · ${expense.category}  ${StatusText.won(expense.amount)}"
        }

        val entry = Entry(expense.name, expense.amount, pageId, purse, day, row)
        entries.add(entry)
        ui.listEntries.addView(row, 0)

        row.findViewById<View>(R.id.buttonRemove).setOnClickListener { removeEntry(entry) }
    }

    /** ✕ 를 누르면 Notion 에서 그 줄을 지우고 로컬 숫자도 되돌린다. */
    private fun removeEntry(entry: Entry) {
        val remove: View = entry.row.findViewById(R.id.buttonRemove)
        remove.isEnabled = false

        val app = applicationContext
        io.execute {
            val result: DeleteResult = try {
                RecordExpense.delete(app, entry.pageId, entry.purse.key, entry.day, entry.amount)
            } catch (e: Exception) {
                DeleteResult(ok = false, message = "오류: ${e.message ?: e.javaClass.simpleName}")
            }

            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread

                if (result.ok) {
                    entries.remove(entry)
                    ui.listEntries.removeView(entry.row)
                    NotificationHelper.show(app)
                    refreshNumbers()
                    showResult("✕ ${entry.name} ${StatusText.won(entry.amount)} 지웠어요", Tone.NEUTRAL)
                } else {
                    remove.isEnabled = true
                    showResult(result.message, Tone.of(ok = false, snapshot = null))
                }
            }
        }
    }

    private fun showResult(message: String, tone: Tone) {
        ui.textResult.visibility = View.VISIBLE
        ui.textResult.text = message
        ui.textResult.setTextColor(colorOf(tone))
    }

    /** [Tone] 을 이 화면의 색으로 옮긴다. 판정은 [Tone.of] 가 하고 여기서는 고르기만 한다. */
    private fun colorOf(tone: Tone): Int {
        val res: Int = when (tone) {
            Tone.REMAINING -> R.color.app_accent
            Tone.OVER -> R.color.app_over
            Tone.FAILED -> R.color.app_over
            Tone.NEUTRAL -> R.color.app_muted
        }
        return ContextCompat.getColor(this, res)
    }
}
