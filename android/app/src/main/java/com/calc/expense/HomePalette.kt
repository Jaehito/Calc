package com.calc.expense

import androidx.compose.ui.graphics.Color

/**
 * 곳간 화면 팔레트. 런처 아이콘의 딥그린에서 파생했고 `res/values/colors.xml` 과 같은 값이다.
 *
 * 파란색을 쓰지 않은 이유는 곳간이 **쌓이는 것**이라서다 — 초록 계열이 축적의 은유에 맞고
 * 금융앱 기본값과도 갈린다. 중립색도 순회색이 아니라 초록 쪽으로 미세하게 기울여 한 벌로 묶었다.
 */
object HomePalette {
    val Ground = Color(0xFFEFF3F1)
    val Card = Color(0xFFFFFFFF)
    val Ink = Color(0xFF101614)
    val Ink2 = Color(0xFF55655F)
    val Muted = Color(0xFF8C9B95)
    val Line = Color(0xFFE4EAE7)
    val Accent = Color(0xFF0E7C61)
    val Soft = Color(0xFFDCEFE7)
    val Over = Color(0xFFC4453F)

    /** 판정은 [Tone] 이 한다. 여기서는 색만 고른다 — XML 화면과 같은 규칙을 쓰기 위해서다. */
    fun of(tone: Tone): Color = when (tone) {
        Tone.REMAINING -> Accent
        Tone.OVER -> Over
        Tone.FAILED -> Over
        Tone.NEUTRAL -> Muted
    }
}
