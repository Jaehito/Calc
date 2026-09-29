package com.calc.expense

import android.content.Context
import android.text.Editable
import android.text.Selection
import android.text.Spanned
import android.text.TextWatcher
import android.util.AttributeSet
import android.util.TypedValue
import android.view.inputmethod.BaseInputConnection
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat

/**
 * 기록 창의 입력 칸. «스타벅스 5600» 처럼 한 칸에 이름과 금액을 같이 적는다.
 *
 * 금액으로 읽히는 낱말은 칸 안에서 초록 알약(«5,600원»)으로 바꿔 그린다. 처음 쓰는 사람이
 * «이름 뒤에 금액» 을 몰라도, 숫자를 치는 순간 그 부분만 모양이 바뀌어 한 칸에 둘 다
 * 들어간다는 걸 보게 된다. 어느 낱말이 금액인지는 [ExpenseParser.amountRange] 가 정한다 —
 * 기록할 때 읽는 규칙과 같아서, 알약이 된 숫자가 곧 기록될 금액이다.
 *
 * 알약을 붙이지 않는 때:
 * - 커서가 알약 **안**에 있을 때 — 고치는 중인 숫자는 원래 글자로 보여야 커서가 제자리에 선다.
 * - 키보드가 아직 조합 중인 글자와 겹칠 때 — 조합 중인 글자를 가리면 키보드가 헷갈린다.
 */
class AmountEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.editTextStyle,
) : AppCompatEditText(context, attrs, defStyleAttr) {

    /** 금액이 바뀔 때마다 부른다. 금액으로 읽히는 낱말이 없으면 null. */
    var onAmountChanged: ((Long?) -> Unit)? = null

    private var lastAmount: Long? = null

    init {
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (s != null) refreshPill(s)
            }
        })
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        // 생성자 안(부모 초기화 중)에서도 불린다. 그때는 text 가 아직 준비되지 않았을 수 있다.
        val s: Editable = text ?: return
        refreshPill(s)
    }

    private fun refreshPill(s: Editable) {
        for (old in s.getSpans(0, s.length, AmountPillSpan::class.java)) s.removeSpan(old)

        val raw: String = s.toString()
        val range: IntRange? = ExpenseParser.amountRange(raw)
        val amount: Long? = range?.let { ExpenseParser.parseAmount(raw.substring(it)) }
        if (amount != lastAmount) {
            lastAmount = amount
            onAmountChanged?.invoke(amount)
        }
        if (range == null || amount == null) return

        val start: Int = range.first
        val end: Int = range.last + 1
        val cursor: Int = Selection.getSelectionEnd(s)
        if (cursor in (start + 1) until end) return

        val composingStart: Int = BaseInputConnection.getComposingSpanStart(s)
        val composingEnd: Int = BaseInputConnection.getComposingSpanEnd(s)
        if (composingStart >= 0 && composingStart < end && composingEnd > start) return

        s.setSpan(pillFor(amount), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    private fun pillFor(amount: Long): AmountPillSpan = AmountPillSpan(
        label = StatusText.won(amount),
        fill = ContextCompat.getColor(context, R.color.app_soft),
        textColor = ContextCompat.getColor(context, R.color.app_accent),
        padH = dp(9f),
        padV = dp(4f),
        gap = dp(2f),
        typeface = ResourcesCompat.getFont(context, R.font.figures_bold),
    )

    private fun dp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)
}
