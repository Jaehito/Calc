package com.calc.expense

import androidx.compose.ui.graphics.Color

/**
 * 곳간 화면 팔레트. `res/values/colors.xml` 과 같은 값이다.
 *
 * 밝은 민트그린을 축으로 둔다 — 곳간이 **쌓이는 것**이라 초록 계열이 축적의 은유에 맞고,
 * 채도를 올려 산뜻하게 잡았다. 중립색도 순회색이 아니라 초록 쪽으로 미세하게 기울여 묶었다.
 *
 * 초록을 둘로 나눈다: [Accent] 는 **글자용**(작은 글자도 읽히게 조금 진하게), [AccentBright] 는
 * **채움용**(버튼·막대·게이지·도넛). 밝은 민트를 작은 글자에 쓰면 흰 바탕에서 잘 안 읽힌다.
 */
object HomePalette {
    val Ground = Color(0xFFEEF3F1)
    val Card = Color(0xFFFFFFFF)
    val Ink = Color(0xFF0F1A17)
    val Ink2 = Color(0xFF5A6B64)
    val Muted = Color(0xFF9AA8A2)
    val Line = Color(0xFFE7EDEA)

    /** 글자용 초록 (Tone.REMAINING). 작은 글자도 흰 바탕에서 읽힌다. */
    val Accent = Color(0xFF0BA06E)

    /** 채움용 밝은 민트 (버튼·막대·게이지·도넛·탭 알약). */
    val AccentBright = Color(0xFF12C08B)

    val Soft = Color(0xFFDFF4EC)
    val Chip = Color(0xFFF2F6F4)
    val Gold = Color(0xFFE9A23B)
    val Over = Color(0xFFF0544B)

    /** 통계 카테고리 세그먼트 색. 큰 것부터 순서대로 돌려 쓴다. */
    val CategoryColors = listOf(
        Color(0xFF12C08B), // 민트
        Color(0xFFE9A23B), // 골드
        Color(0xFF3AA6C4), // 틸
        Color(0xFF8E7BE6), // 바이올렛
        Color(0xFFEC6A8B), // 로즈
        Color(0xFF57B36B), // 그린
        Color(0xFFE0864B), // 오렌지
        Color(0xFF9AA8A2), // 회색(미분류·나머지)
    )

    fun categoryColor(index: Int): Color = CategoryColors[index % CategoryColors.size]

    /** 판정은 [Tone] 이 한다. 여기서는 색만 고른다 — XML 화면과 같은 규칙을 쓰기 위해서다. */
    fun of(tone: Tone): Color = when (tone) {
        Tone.REMAINING -> Accent
        Tone.OVER -> Over
        Tone.FAILED -> Over
        Tone.NEUTRAL -> Muted
    }
}
