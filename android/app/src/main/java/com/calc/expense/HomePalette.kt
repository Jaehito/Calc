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
    /** 화면 바탕. 색이 섞이지 않은 중립 회색 — 초록은 강조에만 남긴다. */
    val Ground = Color(0xFFF2F4F6)
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

    /** 앱 아이콘(웃는 지갑)의 크림 바탕. 홈 머리 줄의 작은 앱 아이콘이 쓴다. */
    val Cream = Color(0xFFFFF1D6)

    /** 판정은 [Tone] 이 한다. 여기서는 색만 고른다 — XML 화면과 같은 규칙을 쓰기 위해서다. */
    fun of(tone: Tone): Color = when (tone) {
        Tone.REMAINING -> Accent
        Tone.OVER -> Over
        Tone.FAILED -> Over
        Tone.NEUTRAL -> Muted
    }
}
