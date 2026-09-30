package com.calc.expense

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 토글과 화면이 함께 움직이는 시간. 둘이 같아야 알약과 화면이 한 동작으로 보인다. */
private const val SWITCH_MS = 280

/**
 * 개인·공용 토글. 홈과 통계 아래 가운데 떠 있다 — 한 번에 한 지갑만 보인다.
 *
 * 칸 폭은 모두 같다(가장 긴 이름에 맞춤). 그래야 진한 알약이 칸 하나만큼 옆으로 미끄러진다.
 */
@Composable
fun PurseToggle(
    purses: List<Purse>,
    selected: Purse,
    labels: Map<Purse, String>,
    onSelect: (Purse) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pill = RoundedCornerShape(999.dp)
    val index: Int = purses.indexOf(selected).coerceAtLeast(0)
    val slide: Float by animateFloatAsState(index.toFloat(), tween(SWITCH_MS), label = "pursePill")

    Box(
        modifier = modifier
            .shadow(elevation = 6.dp, shape = pill)
            .clip(pill)
            .background(ToggleTrack)
            .padding(4.dp)
            .width(IntrinsicSize.Max),
    ) {
        Box(Modifier.matchParentSize()) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(1f / purses.size.coerceAtLeast(1))
                    .graphicsLayer { translationX = size.width * slide }
                    .clip(pill)
                    .background(ToggleOn),
            )
        }
        Row(Modifier.fillMaxWidth()) {
            for (p in purses) {
                val color: Color by animateColorAsState(
                    if (p == selected) Color.White else ToggleIdle,
                    tween(SWITCH_MS),
                    label = "purseLabel",
                )
                Text(
                    text = labels[p] ?: p.defaultLabel,
                    color = color,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier
                        .weight(1f)
                        .clip(pill)
                        .clickable { onSelect(p) }
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
        }
    }
}

/**
 * 지갑이 바뀔 때 화면을 옆으로 민다. 토글에서 오른쪽 칸(공용)을 고르면 화면이 왼쪽으로,
 * 왼쪽 칸(개인)을 고르면 오른쪽으로 간다 — 손가락이 간 쪽에서 새 화면이 들어온다.
 */
@Composable
fun PurseSlide(
    purse: Purse,
    purses: List<Purse>,
    modifier: Modifier = Modifier,
    content: @Composable (Purse) -> Unit,
) {
    AnimatedContent(
        targetState = purse,
        transitionSpec = {
            val dir: Int = if (purses.indexOf(targetState) > purses.indexOf(initialState)) 1 else -1
            (slideInHorizontally(tween(SWITCH_MS)) { w -> dir * w } + fadeIn(tween(SWITCH_MS)))
                .togetherWith(slideOutHorizontally(tween(SWITCH_MS)) { w -> -dir * w } + fadeOut(tween(SWITCH_MS)))
        },
        modifier = modifier,
        label = "purseSlide",
    ) { p -> content(p) }
}

private val ToggleTrack = Color(0xFFE5E8EB)
private val ToggleOn = Color(0xFF333D4B)
private val ToggleIdle = Color(0xFF4E5968)
