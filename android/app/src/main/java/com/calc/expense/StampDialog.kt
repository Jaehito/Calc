package com.calc.expense

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalDate

/** 스탬프를 올려 두는 칸 — 회색 바탕의 둥근 네모. 받기 팝업과 눌러 보기 팝업이 같이 쓴다. */
@Composable
private fun StampSlot(content: @Composable () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(132.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(HomePalette.Ground),
    ) { content() }
}

/** 이름과 한 줄. 스탬프 이름은 꽃 이름이 아니라 업적의 짧은 이름(«7일 이어 적기»)이다. */
@Composable
private fun StampTitle(name: String, line: String) {
    Spacer(Modifier.height(12.dp))
    Text(name, color = HomePalette.Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    Spacer(Modifier.height(4.dp))
    Text(line, color = HomePalette.Ink2, fontSize = 14.sp, textAlign = TextAlign.Center)
}

@Composable
private fun OkButton(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(text, color = HomePalette.Accent, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * 도감에서 스탬프 하나를 눌렀을 때. 앱의 다른 팝업(어제 등급)과 같은 흰 판·민트 «확인»이다.
 *
 * 받은 것은 언제 받았는지 한 줄만, 못 받은 것은 지금 숫자와 막대 하나만 둔다 — 셀 수 없는
 * 조건(«처음으로 S»)은 막대 없이 조건만.
 */
@Composable
fun StampDetailDialog(plant: Plant, result: DogamResult, onDismiss: () -> Unit) {
    val day: LocalDate? = result.blooms[plant]
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HomePalette.Card,
        shape = RoundedCornerShape(24.dp),
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                StampSlot { Stamp(plant, got = day != null, size = 108.dp) }
                StampTitle(plant.short, if (day != null) plant.story else plant.condition)
                if (day != null) {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = tr(
                            "${day.format(L10n.monthDay())}에 받았어요",
                            "Received on ${day.format(L10n.monthDay())}",
                            "Recibido el ${day.format(L10n.monthDay())}",
                        ),
                        color = HomePalette.Accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        style = Figures,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(HomePalette.Soft)
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                    )
                } else {
                    val tally: Tally? = plant.tally
                    if (tally != null && plant.target > 1) {
                        val count: Int = minOf(result.tallies.of(tally), plant.target)
                        Spacer(Modifier.height(14.dp))
                        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
                            Text(tr("지금까지", "So far", "Hasta ahora"), color = HomePalette.Ink2, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            Text("$count", color = HomePalette.Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold, style = Figures)
                            Text(" / ${plant.target}${tally.unit}", color = HomePalette.Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, style = Figures, modifier = Modifier.padding(bottom = 2.dp))
                        }
                        Spacer(Modifier.height(6.dp))
                        ProgressBar(count.toFloat() / plant.target)
                    }
                }
            }
        },
        confirmButton = { OkButton(tr("확인", "OK", "Aceptar"), onDismiss) },
    )
}

@Composable
private fun ProgressBar(fraction: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(99.dp))
            .background(HomePalette.Ground),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(8.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(HomePalette.Accent),
        )
    }
}

/**
 * 새로 받은 스탬프를 찍어 주는 팝업. 빈 칸이 먼저 보이고, 나무 도장이 내려와 꾹 누르고 올라가면
 * 그 자리에 잉크 자국이 남는다. 파동이나 날짜·번호 같은 덧붙임은 없다.
 *
 * 여러 개를 한꺼번에 받았으면 같은 판에서 «다음»을 누를 때마다 하나씩 찍는다. 마지막 것을 닫으면
 * [onDone] — 부른 쪽이 알린 것으로 적는다.
 */
@Composable
fun StampPressDialog(plants: List<Plant>, onDone: () -> Unit) {
    if (plants.isEmpty()) return
    var index: Int by rememberSaveable { mutableIntStateOf(0) }
    val plant: Plant = plants[index.coerceIn(0, plants.lastIndex)]
    val last: Boolean = index >= plants.lastIndex

    // 도장 위치(dp, 칸 가운데 기준)와 보이는 정도, 잉크가 묻은 정도.
    val drop = remember(index) { Animatable(-96f) }
    val stamperAlpha = remember(index) { Animatable(0f) }
    val ink = remember(index) { Animatable(0f) }

    LaunchedEffect(index) {
        delay(260)
        stamperAlpha.animateTo(1f, tween(120))
        drop.animateTo(0f, tween(320, easing = FastOutSlowInEasing))
        ink.snapTo(1f)
        delay(140)
        drop.animateTo(-110f, tween(300, easing = FastOutSlowInEasing))
        stamperAlpha.animateTo(0f, tween(160))
    }

    AlertDialog(
        onDismissRequest = {},
        containerColor = HomePalette.Card,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(tr("새 스탬프", "New stamp", "Nuevo sello"), color = HomePalette.Ink, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                StampSlot {
                    // 빈 칸 → 잉크 자국. 도장이 닿는 순간 바뀐다.
                    Stamp(plant, got = false, size = 108.dp, modifier = Modifier.alpha(1f - ink.value))
                    Stamp(plant, got = true, size = 108.dp, inkAlpha = ink.value)
                    Stamper(
                        rubber = StampArt.ink(plant.shelf),
                        modifier = Modifier
                            .offset(y = drop.value.dp)
                            .alpha(stamperAlpha.value),
                    )
                }
                StampTitle(plant.short, plant.story)
            }
        },
        confirmButton = {
            OkButton(if (last) tr("확인", "OK", "Aceptar") else tr("다음", "Next", "Siguiente")) {
                if (last) onDone() else index++
            }
        },
    )
}

/** 나무 손잡이 도장. 고무 면이 그 스탬프의 잉크 색이다. */
@Composable
private fun Stamper(rubber: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(width = 64.dp, height = 78.dp)) {
        val u: Float = size.width / 64f
        // 손잡이(둥근 기둥) — 고무 면이 칸 가운데 닿도록 그림 전체가 위로 올라가 있다.
        drawRoundRect(Wood, topLeft = Offset(22f * u, 0f), size = Size(20f * u, 30f * u), cornerRadius = CornerRadius(10f * u))
        drawRoundRect(WoodLight, topLeft = Offset(26f * u, 4f * u), size = Size(5f * u, 20f * u), cornerRadius = CornerRadius(2.5f * u))
        // 받침
        drawRoundRect(WoodDark, topLeft = Offset(15f * u, 28f * u), size = Size(34f * u, 10f * u), cornerRadius = CornerRadius(3f * u))
        // 고무 면
        drawRoundRect(rubber, topLeft = Offset(5f * u, 38f * u), size = Size(54f * u, 9f * u), cornerRadius = CornerRadius(3f * u))
    }
}

private val Wood = Color(0xFFB98A62)
private val WoodLight = Color(0xFFD4A97F)
private val WoodDark = Color(0xFF8E6747)
