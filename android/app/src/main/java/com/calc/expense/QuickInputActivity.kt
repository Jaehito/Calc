package com.calc.expense

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.calc.expense.databinding.ActivityQuickInputBinding
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
         * 맨 앞 칸. 통계 화면과 같은 이름·아이콘인 «미분류»로 보이지만,
         * 실제 카테고리 값(저장소에 쓰는 값)은 예전처럼 빈 문자열이다.
         */
        private const val CATEGORY_NONE = CategoryBreakdown.UNCATEGORIZED

        /** 입력 칸 위 안내 말풍선을 몇 번까지 보여 줄지. «알겠어요» 를 누르면 바로 끝난다. */
        private const val TIP_LIMIT = 3
        private const val PREFS = "quick_input"
        private const val KEY_TIP_SHOWN = "tip_shown"
    }

    private lateinit var ui: ActivityQuickInputBinding
    private val io = Executors.newSingleThreadExecutor()

    private var purses: List<Purse> = emptyList()
    private var selected: Purse = Purse.PERSONAL
    private var submitting: Boolean = false

    /** 지금 고른 카테고리. 안 고르면 빈 문자열. 다음 입력까지 유지된다. */
    private var selectedCategory: String = ""

    /** 격자 칸 목록(«미분류» 포함, 맨 앞). [CategoryClassifier] 는 이 안에서만 고른다. */
    private var categoryLabels: List<String> = emptyList()

    /** 사용자가 칸을 직접 눌렀는지. 누르고 나면 그 뒤로는 자동 분류가 끼어들지 않는다. */
    private var manualCategoryOverride: Boolean = false

    /** 위에 띄운 숫자의 바탕. 금액을 적는 동안 «기록하면 오늘 얼마 남는지» 를 여기서 뺀다. */
    private var shownSnapshot: LedgerSnapshot? = null

    /** 입력 칸에서 지금 금액으로 읽히는 값. 없으면 null — 기록 버튼이 회색이다. */
    private var typedAmount: Long? = null

    /** 이 화면을 연 뒤로 기록한 건수. 결과 줄에 «2건째» 를 붙일지 정한다. */
    private var recorded: Int = 0

    /**
     * 이 화면에서 방금 적은 항목. 지우기 버튼으로 지울 수 있게 저장소의 줄 id 를 들고 있는다.
     * 창을 닫으면 사라진다 — 지난 기록은 로컬에 항목 단위로 저장하지 않기 때문이다.
     */
    private data class Entry(
        val name: String,
        val amount: Long,
        val rowId: String,
        val purse: Purse,
        val day: LocalDate,
        val row: View,
    )

    private val entries = mutableListOf<Entry>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 잠금 여부를 여기서 다시 보지 않는다. 어느 문으로 들어오면 어디에 도착하는지는
        // [EntryRoutes] 하나가 정하고, 이 화면에 왔다는 것은 이미 «기록» 으로 정해졌다는 뜻이다.
        // 예전에는 잠금이 풀려 있으면 홈으로 되돌렸는데, 그러면 알림 셰이드에서 카드를 눌렀을 때
        // 기록 화면 대신 앱 메인이 떠서 «적으려고 눌렀는데» 가 됐다.
        showOverLockScreen()

        ui = ActivityQuickInputBinding.inflate(layoutInflater)
        setContentView(ui.root)
        // 레이아웃의 한국어는 미리보기용이다. 고른 언어로 여기서 덮는다.
        ui.textCaption.text = tr("오늘 쓸 수 있는 돈", "Left to spend today", "Disponible hoy")
        ui.inputExpense.hint = tr("예: 커피 4500", "e.g. coffee 4500", "p. ej. café 4500")
        ui.buttonSend.contentDescription = tr("기록", "Log", "Anotar")
        ui.textRecentTitle.text = tr("방금 적은 것", "Just logged", "Recién anotado")

        // 창은 화면 전체를 덮고, 카드는 레이아웃에서 아래에 붙는다.
        // setLayout 은 floating 창에서만 먹히므로 쓰지 않는다.
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        keepSheetAboveSystemBars()

        // 카드 바깥(어두운 곳)을 누르면 닫는다. 따로 «완료» 버튼을 두지 않는다.
        ui.sheetRoot.setOnClickListener { finish() }

        setUpPurses()
        setUpCategories()
        setUpTip()
        refreshNumbers()

        ui.inputExpense.onAmountChanged = { amount ->
            typedAmount = amount
            ui.buttonSend.isEnabled = amount != null
            refreshAfterLine()
        }
        ui.buttonSend.setOnClickListener { submit() }

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
        purses = PurseAccess.linked(this)
        selected = purses.firstOrNull() ?: Purse.PERSONAL

        if (purses.size < 2) {
            ui.groupPurse.visibility = View.GONE
            return
        }

        ui.groupPurse.visibility = View.VISIBLE
        ui.tabPersonal.text = settings.labelOf(Purse.PERSONAL)
        ui.tabShared.text = settings.labelOf(Purse.SHARED)
        ui.tabPersonal.setOnClickListener { selectPurse(Purse.PERSONAL) }
        ui.tabShared.setOnClickListener { selectPurse(Purse.SHARED) }
        refreshTabs()
    }

    private fun selectPurse(purse: Purse) {
        if (selected == purse) return
        selected = purse
        refreshTabs()
        refreshNumbers()
    }

    /** 고른 탭만 흰 알약에 초록 글자, 나머지는 회색 글자. */
    private fun refreshTabs() {
        for ((tab, purse) in listOf(ui.tabPersonal to Purse.PERSONAL, ui.tabShared to Purse.SHARED)) {
            val on: Boolean = selected == purse
            tab.setBackgroundResource(if (on) R.drawable.bg_tab_on else 0)
            tab.setTextColor(ContextCompat.getColor(this, if (on) R.color.app_accent else R.color.app_muted))
            tab.isSelected = on
        }
    }

    /**
     * 카테고리 격자를 만든다. «미분류» 가 맨 앞이고 기본으로 켜져 있다. 목록은 앱이 갖는다
     * (설정에서 편집) — 잠금화면에서 네트워크 없이 바로 그린다.
     *
     * 이름을 입력하면 [CategoryMemoryStore] 의 기억과 [CategoryClassifier] 의 낱말 규칙이
     * 차례로 짐작해 칸을 자동으로 켠다 (네트워크·LLM 없음). 사용자가 칸을 직접 누르면
     * [manualCategoryOverride] 가 서서 그 뒤로는 자동이 끼어들지 않는다 — 스스로 고른 걸
     * 되돌리면 안 되기 때문이다.
     */
    private fun setUpCategories() {
        categoryLabels = listOf(CATEGORY_NONE) + CategoryStore.load(this).filter { it != CATEGORY_NONE }
        ui.groupCategory.removeAllViews()

        for (label in categoryLabels) {
            val cell: View = layoutInflater.inflate(R.layout.item_category_cell, ui.groupCategory, false)
            // 칸에 보이는 말은 번역하고, 저장할 값은 tag 에 원래 이름 그대로 둔다.
            cell.tag = label
            cell.findViewById<TextView>(R.id.cellLabel).text = L10n.name(label)
            cell.contentDescription = L10n.name(label)
            val icon: Int? = CategoryIcons.of(label)
            val iconView: ImageView = cell.findViewById(R.id.cellIcon)
            val letter: TextView = cell.findViewById(R.id.cellLetter)
            if (icon != null) {
                iconView.setImageResource(icon)
            } else {
                iconView.visibility = View.GONE
                letter.visibility = View.VISIBLE
                letter.text = L10n.name(label).take(1)
            }
            // 다섯 칸이 폭을 똑같이 나눠 갖는다.
            cell.layoutParams = GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED),
                GridLayout.spec(GridLayout.UNDEFINED, 1f),
            ).apply { width = 0 }
            cell.setOnClickListener {
                manualCategoryOverride = true
                checkCell(label)
            }
            ui.groupCategory.addView(cell)
        }
        checkCell(CATEGORY_NONE)

        ui.inputExpense.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (manualCategoryOverride) return
                applyAutoCategory(s?.toString().orEmpty())
            }
        })
    }

    /** [label] 칸을 켜고 나머지를 끈다. 켜진 칸은 민트 동그라미·흰 아이콘·초록 굵은 이름. */
    private fun checkCell(label: String) {
        selectedCategory = if (label == CATEGORY_NONE) "" else label
        val accent: Int = ContextCompat.getColor(this, R.color.app_accent)
        val ink2: Int = ContextCompat.getColor(this, R.color.app_ink_2)
        val white: Int = ContextCompat.getColor(this, R.color.app_card)
        for (i in 0 until ui.groupCategory.childCount) {
            val cell: View = ui.groupCategory.getChildAt(i)
            val on: Boolean = cell.tag == label
            cell.isSelected = on
            cell.findViewById<View>(R.id.cellCircle)
                .setBackgroundResource(if (on) R.drawable.bg_cell_on else R.drawable.bg_cell)
            cell.findViewById<ImageView>(R.id.cellIcon).setColorFilter(if (on) white else ink2)
            cell.findViewById<TextView>(R.id.cellLetter).setTextColor(if (on) white else ink2)
            val name: TextView = cell.findViewById(R.id.cellLabel)
            name.setTextColor(if (on) accent else ink2)
            name.setTypeface(null, if (on) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        }
    }

    /**
     * [text] 에서 카테고리를 짐작해 칸을 켠다. 사용자가 아직 손대지 않았을 때만 불린다.
     *
     * **기억이 낱말 규칙을 이긴다.** [CategoryClassifier] 는 아는 말만 알아서 단골 가게 이름은
     * 영영 «미분류»로 떨어진다. 한 번 정해 준 이름은 [CategoryMemoryStore] 가 기억하므로,
     * 쓸수록 «미분류»가 줄어든다. 순서를 뒤집으면 스타벅스를 식비로 적어 온 사람에게 앱이
     * 매번 카페로 되돌리게 된다 — 그건 짐작이 아니라 고집이다.
     */
    private fun applyAutoCategory(text: String) {
        val guess: String? = CategoryMemoryStore.recall(this, text, categoryLabels)
            ?: CategoryClassifier.classify(text, categoryLabels)
        checkCell(guess ?: CATEGORY_NONE)
    }

    /**
     * 고른 곳간의 숫자를 입력창 위에 띄우고 그 스냅샷을 돌려준다.
     * 적으면서 판단할 수 있게 하는 것이 이 화면의 존재 이유다.
     *
     * 큰 숫자에도 색을 입힌다 — 넘겼으면 빨강, 남았으면 초록. 글자를 읽기 전에 눈에 들어온다.
     */
    private fun refreshNumbers(): LedgerSnapshot? {
        val snapshot: LedgerSnapshot? = Ledger.snapshot(this, selected)
        shownSnapshot = snapshot
        refreshAfterLine()

        if (snapshot == null) {
            ui.textCaption.text = tr("예산을 정하지 않은 지갑", "No budget set for this wallet", "Esta cartera no tiene presupuesto")
            ui.textAvailable.text = "—"
            ui.textAvailable.setTextColor(colorOf(Tone.NEUTRAL))
            ui.textUnit.visibility = View.GONE
            ui.groupPeriod.visibility = View.GONE
            ui.barCycle.visibility = View.GONE
            return null
        }

        val available: Long = snapshot.available
        ui.textCaption.text = if (snapshot.isOver) {
            tr("오늘 초과", "Over today", "Exceso de hoy")
        } else {
            tr("오늘 쓸 수 있는 돈", "Left to spend today", "Disponible hoy")
        }
        val amount: Long = if (snapshot.isOver) -available else available
        ui.textAvailable.text = L10n.wonPrefix + StatusText.figure(amount)
        ui.textAvailable.setTextColor(colorOf(if (snapshot.isOver) Tone.OVER else Tone.REMAINING))
        ui.textUnit.text = L10n.wonSuffix
        ui.textUnit.visibility = if (L10n.wonSuffix.isEmpty()) View.GONE else View.VISIBLE

        // 홈 카드와 같은 기간 줄과 막대.
        ui.groupPeriod.visibility = View.VISIBLE
        ui.textPeriod.text = StatusText.periodLeft(snapshot)
        ui.textPeriod.setTextColor(
            ContextCompat.getColor(this, if (snapshot.untilTarget >= 0L) R.color.app_ink_2 else R.color.app_over)
        )
        ui.textDaysLeft.text = StatusText.daysLeft(snapshot)
        ui.barCycle.visibility = if (snapshot.cycleDays > 0) View.VISIBLE else View.GONE
        ui.barCycle.progress = (snapshot.cycleElapsed * ui.barCycle.max).toInt()
        return snapshot
    }

    /** 금액이 읽히면 «기록하면 오늘 얼마 남아요». 넘게 되면 빨강. 금액이나 예산이 없으면 숨긴다. */
    private fun refreshAfterLine() {
        val amount: Long? = typedAmount
        val snapshot: LedgerSnapshot? = shownSnapshot
        if (amount == null || snapshot == null) {
            ui.textAfter.visibility = View.GONE
            return
        }
        ui.textAfter.visibility = View.VISIBLE
        ui.textAfter.text = StatusText.afterRecord(snapshot.available, amount)
        ui.textAfter.setTextColor(
            ContextCompat.getColor(this, if (snapshot.available - amount >= 0L) R.color.app_ink_2 else R.color.app_over)
        )
    }

    /**
     * 처음 몇 번 입력 칸 위에 «이름 뒤에 금액» 안내를 띄운다. 창을 열 때마다 한 번씩 세고,
     * [TIP_LIMIT] 번이 지나거나 «알겠어요» 를 누르면 다시 띄우지 않는다.
     */
    private fun setUpTip() {
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val shown: Int = prefs.getInt(KEY_TIP_SHOWN, 0)
        if (shown >= TIP_LIMIT) return

        prefs.edit().putInt(KEY_TIP_SHOWN, shown + 1).apply()
        ui.groupTip.visibility = View.VISIBLE
        ui.textTip.text = tr(
            "이름 뒤에 금액을 띄어 쓰면 한 번에 적혀요.\n금액만 적어도 돼요.",
            "Type the name, a space, then the amount.\nThe amount alone works too.",
            "Escribe el nombre, un espacio y el importe.\nSolo el importe también vale.",
        )
        ui.buttonTipOk.text = tr("알겠어요", "Got it", "Entendido")
        ui.buttonTipOk.setOnClickListener {
            prefs.edit().putInt(KEY_TIP_SHOWN, TIP_LIMIT).apply()
            ui.groupTip.visibility = View.GONE
        }
    }

    private fun submit() {
        if (submitting) return
        val text: String = ui.inputExpense.text?.toString().orEmpty().trim()
        if (text.isEmpty()) return

        submitting = true
        ui.inputExpense.isEnabled = false
        ui.buttonSend.isEnabled = false
        showResult(tr("기록 중…", "Logging…", "Anotando…"), Tone.NEUTRAL)

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
                    lines = StatusText.failed(StatusText.error(e), now),
                )
            }

            // 알림도 같은 결과로 갱신한다. 화면을 닫아도 숫자가 남아 있게.
            NotificationHelper.show(app, result.lines)

            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread

                submitting = false
                ui.inputExpense.isEnabled = true
                ui.buttonSend.isEnabled = typedAmount != null
                ui.inputExpense.requestFocus()

                if (result.ok) {
                    // 입력창만 비우고 화면은 그대로 둔다. 다음 건을 바로 이어 적을 수 있게.
                    recorded++
                    ui.inputExpense.setText("")
                    val after: LedgerSnapshot? = refreshNumbers()

                    val e: Expense? = result.expense
                    if (e != null) addEntryRow(e, result.rowId, purse, day)
                    showResult(
                        if (e == null) tr("기록됨", "Logged", "Anotado") else StatusText.entered(e.name, e.amount, recorded),
                        Tone.of(ok = true, snapshot = after),
                    )
                } else {
                    // 실패하면 입력한 내용을 그대로 둔다. 고쳐서 다시 엔터를 누르면 된다.
                    showResult(result.lines.summary, Tone.of(ok = false, snapshot = null))
                }
            }
        }
    }

    /** 방금 적은 항목을 목록 맨 위에 한 줄 추가한다. 지우기를 누르면 [removeEntry] 로 지운다. */
    private fun addEntryRow(expense: Expense, rowId: String, purse: Purse, day: LocalDate) {
        val row: View = layoutInflater.inflate(R.layout.item_entry_row, ui.listEntries, false)
        row.findViewById<TextView>(R.id.textEntry).text = expense.name
        row.findViewById<TextView>(R.id.textEntryAmount).text = StatusText.won(expense.amount)

        // 카테고리 동그라미. 안 골랐으면 «미분류» 아이콘, 사용자가 만든 카테고리는 첫 글자.
        val category: String = expense.category.ifBlank { CATEGORY_NONE }
        val icon: Int? = CategoryIcons.of(category)
        val iconView: ImageView = row.findViewById(R.id.entryIcon)
        if (icon != null) {
            iconView.setImageResource(icon)
        } else {
            iconView.visibility = View.GONE
            val letter: TextView = row.findViewById(R.id.entryLetter)
            letter.visibility = View.VISIBLE
            letter.text = L10n.name(category).take(1)
        }

        val remove: View = row.findViewById(R.id.buttonRemove)
        remove.contentDescription = tr("지우기", "Delete", "Borrar") + " · " + expense.name

        val entry = Entry(expense.name, expense.amount, rowId, purse, day, row)
        entries.add(entry)
        ui.listEntries.addView(row, 0)
        ui.groupRecent.visibility = View.VISIBLE

        remove.setOnClickListener { removeEntry(entry) }
    }

    /** 지우기를 누르면 저장소에서 그 줄을 지우고 로컬 숫자도 되돌린다. */
    private fun removeEntry(entry: Entry) {
        val remove: View = entry.row.findViewById(R.id.buttonRemove)
        remove.isEnabled = false

        val app = applicationContext
        io.execute {
            val result: DeleteResult = try {
                RecordExpense.delete(app, entry.rowId, entry.purse.key, entry.day, entry.amount)
            } catch (e: Exception) {
                DeleteResult(ok = false, message = StatusText.error(e))
            }

            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread

                if (result.ok) {
                    entries.remove(entry)
                    ui.listEntries.removeView(entry.row)
                    if (entries.isEmpty()) ui.groupRecent.visibility = View.GONE
                    NotificationHelper.show(app)
                    refreshNumbers()
                    showResult(
                        "${entry.name} ${StatusText.won(entry.amount)} " + tr("지웠어요", "deleted", "borrado"),
                        Tone.NEUTRAL,
                    )
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
