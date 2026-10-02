package com.calc.expense

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
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
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

/** 스탬프를 올려 두는 칸 — 회색 바탕의 둥근 네모. 받기 팝업과 눌러 보기 팝업이 같이 쓴다. */
@Composable
private fun StampSlot(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
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
 * 새로 받은 스탬프를 찍어 주는 팝업. 빈 칸(못 받은 모양)이 먼저 보이고, 잠시 뒤 잉크 자국이 조금 크고
 * 기운 채 옅은 그림자와 함께 내려와 «쾅» 찍힌다 — 칸이 살짝 눌리고 판이 떨리며 잉크 점 몇 개가 튄다.
 * 도장 손잡이 같은 도구는 그리지 않는다. 찍힌 모양은 도감 칸과 똑같은 [Stamp] 다.
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

    // fall: 0 = 위에 떠 있음, 1 = 닿음. 나머지는 닿은 뒤의 떨림·눌림·잉크 점·글자.
    val fall = remember(index) { Animatable(0f) }
    val shown = remember(index) { Animatable(0f) }
    val press = remember(index) { Animatable(1f) }
    val nudge = remember(index) { Animatable(0f) }
    val specks = remember(index) { Animatable(0f) }
    val title = remember(index) { Animatable(0f) }
    val landed: Boolean = fall.value >= 1f

    LaunchedEffect(index) {
        delay(EMPTY_FIRST_MS)
        launch { shown.animateTo(0.75f, tween(130)) }
        fall.animateTo(1f, tween(FALL_MS, easing = SlamEasing))
        shown.snapTo(1f)
        launch {
            press.snapTo(0.965f)
            press.animateTo(1f, tween(160))
        }
        launch {
            nudge.snapTo(2f)
            nudge.animateTo(-1f, tween(40))
            nudge.animateTo(0f, tween(60))
        }
        launch { specks.animateTo(1f, tween(200, easing = FastOutSlowInEasing)) }
        delay(240)
        title.animateTo(1f, tween(300))
    }

    AlertDialog(
        onDismissRequest = {},
        containerColor = HomePalette.Card,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.offset(y = nudge.value.dp),
        title = {
            Text(tr("새 스탬프", "New stamp", "Nuevo sello"), color = HomePalette.Ink, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                StampSlot(modifier = Modifier.graphicsLayer { scaleX = press.value; scaleY = press.value }) {
                    if (!landed) Stamp(plant, got = false, size = 108.dp)
                    val f: Float = fall.value
                    // 내려오는 동안 칸 위에 옅은 그림자. 닿으면 사라진다.
                    if (!landed && shown.value > 0f) {
                        Canvas(Modifier.size(114.dp)) {
                            drawCircle(
                                Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.22f * f), Color.Transparent)),
                                radius = this.size.minDimension / 2f * (1.25f - 0.23f * f),
                            )
                        }
                    }
                    Stamp(
                        plant,
                        got = true,
                        size = 108.dp,
                        modifier = Modifier
                            .graphicsLayer {
                                val scale: Float = 1.4f - 0.4f * f
                                scaleX = scale
                                scaleY = scale
                                translationY = (-22f * (1f - f)).dp.toPx()
                                rotationZ = -10f * (1f - f)
                                alpha = shown.value
                            }
                            .blur(((1f - f) * 1.5f).dp, BlurredEdgeTreatment.Unbounded),
                    )
                    if (landed) InkSpecks(StampArt.ink(plant.shelf), specks.value)
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.graphicsLayer {
                        alpha = title.value
                        translationY = (6f * (1f - title.value)).dp.toPx()
                    },
                ) {
                    StampTitle(plant.short, plant.story)
                }
            }
        },
        confirmButton = {
            OkButton(if (last) tr("확인", "OK", "Aceptar") else tr("다음", "Next", "Siguiente")) {
                if (last) onDone() else index++
            }
        },
    )
}

/** 닿는 순간 튀는 잉크 점 세 개. [t] 가 0→1 로 가며 가운데서 바깥으로 나간다. */
@Composable
private fun InkSpecks(ink: Color, t: Float) {
    Canvas(Modifier.size(132.dp)) {
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        for ((dx: Float, dy: Float, r: Float) in SPECKS) {
            drawCircle(
                ink.copy(alpha = 0.8f * minOf(1f, t * 3f)),
                radius = r.dp.toPx(),
                center = center + Offset(dx.dp.toPx() * t, dy.dp.toPx() * t),
            )
        }
    }
}

/** 처음에 빈 칸만 보여 주는 시간, 내려오는 시간. */
private const val EMPTY_FIRST_MS = 800L
private const val FALL_MS = 300

/** 천천히 들어와 끝에서 확 꽂힌다 — «쾅». */
private val SlamEasing = CubicBezierEasing(0.7f, 0f, 1f, 0.65f)

/** 잉크 점: 가운데에서 (dx, dy) dp 만큼, 반지름 r dp. 칸 밖으로 나가지 않게 둔다. */
private val SPECKS: List<Triple<Float, Float, Float>> = listOf(
    Triple(-52f, -26f, 2.2f),
    Triple(54f, -22f, 1.7f),
    Triple(48f, 32f, 2.2f),
)
