package com.calc.expense

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.style.ReplacementSpan

/**
 * 입력 칸 안에서 금액 낱말(«5600») 자리에 «5,600원» 알약을 대신 그린다.
 *
 * 글자는 그대로 두고 그리기만 바꾼다 — 저장되는 건 사용자가 친 글자 그대로이고,
 * [ExpenseParser] 가 같은 글자를 다시 읽는다. 커서가 알약 안으로 들어가면 [AmountEditText] 가
 * 이 span 을 떼어 원래 글자를 보여 준다(그린 폭과 글자 수가 달라 커서가 엉뚱한 데 서기 때문).
 */
class AmountPillSpan(
    private val label: String,
    private val fill: Int,
    private val textColor: Int,
    private val padH: Float,
    private val padV: Float,
    private val gap: Float,
    private val typeface: Typeface?,
) : ReplacementSpan() {

    private fun pillPaint(base: Paint): Paint = Paint(base).apply {
        textSize = base.textSize * 0.92f
        if (this@AmountPillSpan.typeface != null) typeface = this@AmountPillSpan.typeface
        isFakeBoldText = this@AmountPillSpan.typeface == null
    }

    override fun getSize(
        paint: Paint,
        text: CharSequence?,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?,
    ): Int {
        // 줄 높이는 원래 글자 그대로 둔다 — 알약이 줄을 키우면 칸 높이가 출렁인다.
        if (fm != null) paint.getFontMetricsInt(fm)
        return (pillPaint(paint).measureText(label) + padH * 2 + gap * 2).toInt()
    }

    override fun draw(
        canvas: Canvas,
        text: CharSequence?,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint,
    ) {
        val p: Paint = pillPaint(paint)
        val width: Float = p.measureText(label) + padH * 2
        val rect = RectF(x + gap, y + p.ascent() - padV, x + gap + width, y + p.descent() + padV)
        val radius: Float = rect.height() / 2f

        val originalColor: Int = p.color
        p.color = fill
        canvas.drawRoundRect(rect, radius, radius, p)
        p.color = textColor
        canvas.drawText(label, x + gap + padH, y.toFloat(), p)
        p.color = originalColor
    }
}
