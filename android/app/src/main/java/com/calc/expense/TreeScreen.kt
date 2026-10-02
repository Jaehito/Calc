package com.calc.expense

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.sin

/**
 * 나무 탭. 아래 떠 있는 «나무 | 도감» 알약으로 두 칸을 오간다 — 나무 칸은 벚꽃나무 한 그루, 도감 칸은 스탬프 북([DogamScreen]).
 *
 * 나무 칸에는 단계 이름도 단계 줄도 없다. 다음에 어떤 모습이 될지는 자라서 보는 재미로 남기고,
 * «다음 모습까지 물 몇 방울»만 알려 준다. 나무를 누르면 물을 한 방울씩 준다.
 *
 * @param onGive 물 한 방울을 준다. 물이 없으면 null — 그림은 그대로 둔다
 */
@Composable
fun TreeTab(
    tree: TreeState,
    dogam: DogamUi,
    today: LocalDate,
    segment: Int,
    onSegment: (Int) -> Unit,
    onGive: () -> TreeState?,
    onOpenSettings: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (segment == SEGMENT_STAMPS) {
            DogamScreen(ui = dogam, today = today, onOpenSettings = onOpenSettings, header = { TreeHeader(onOpenSettings) })
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(HomePalette.Ground)
                    .padding(start = 20.dp, end = 20.dp, top = TAB_TOP_PADDING, bottom = FLOATING_TOGGLE_SPACE),
            ) {
                TreeHeader(onOpenSettings)
                Spacer(Modifier.height(14.dp))
                TreeScene(tree, onGive, Modifier.fillMaxWidth().weight(1f))
            }
        }
        // 홈·통계의 개인·공용 토글과 같은 모양·같은 자리.
        PillToggle(
            labels = listOf(tr("나무", "Tree", "Árbol"), tr("도감", "Stamps", "Sellos") + " ${dogam.result.blooms.size}"),
            selected = segment,
            onSelect = onSegment,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = FLOATING_TOGGLE_BOTTOM),
        )
    }
}

/** 나무 탭의 두 칸. */
const val SEGMENT_TREE = 0
const val SEGMENT_STAMPS = 1

/** 머리줄. 통계 탭과 같은 모양(제목 + 톱니)이다. */
@Composable
private fun TreeHeader(onOpenSettings: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = tr("나무", "Tree", "Árbol"),
            color = HomePalette.Ink,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        SettingsGear(onOpenSettings)
    }
}

/**
 * 수채 풍경 속 나무. 그림이 칸을 다 쓰고, 막대는 그림 아래쪽에 얹는다.
 *
 * 누르면 물방울이 떨어져 나무가 살짝 출렁인다. 그 한 방울로 단계가 오르면 진짜 자라듯 —
 * 나무가 빛나며 몸을 떨고, 땅으로 움츠러들었다가, 새 모습이 땅에서 솟아 출렁이며 자리 잡고 잎(꽃봉오리부터는
 * 꽃잎)이 흩날린다. 그다음 «나무가 자랐어요!» 팝업([TreeGrowDialog]). 다 자란 뒤에는
 * [TreeGrowth.PETAL_EVERY] 방울마다 꽃잎이 흩날린다.
 */
@Composable
private fun TreeScene(tree: TreeState, onGive: () -> TreeState?, modifier: Modifier) {
    val scope = rememberCoroutineScope()
    val drop = remember { Animatable(1f) }
    val bounce = remember { Animatable(1f) }
    val petals = remember { Animatable(1f) }
    // 자람: 빛(0→1→0), 떨림(도), 움츠림(0→1), 솟음(0→1, 스프링이라 1을 살짝 넘는다), 잎 흩날림(0→1).
    val halo = remember { Animatable(0f) }
    val shake = remember { Animatable(0f) }
    val sink = remember { Animatable(0f) }
    val rise = remember { Animatable(1f) }
    val burst = remember { Animatable(1f) }
    val stage: Int = tree.stage
    // 지금 그리고 있는 단계. 자라는 동안에는 옛 모습을 들고 있다가 움츠러든 뒤 바꾼다.
    var shown: Int by remember { mutableIntStateOf(stage) }
    var growing: Boolean by remember { mutableStateOf(false) }
    var grewTo: Int? by remember { mutableStateOf(null) }
    val progress: Float by animateFloatAsState(TreeGrowth.progress(tree.given), tween(320), label = "treeProgress")

    // 물 주기 말고 단계가 바뀐 경우(계정에서 되찾음 등)는 연출 없이 맞춘다.
    LaunchedEffect(stage) { if (!growing) shown = stage }

    val grow: suspend (Int) -> Unit = { next ->
        coroutineScope {
            launch { halo.animateTo(1f, tween(300)) }
            repeat(3) {
                shake.animateTo(2.4f, tween(50))
                shake.animateTo(-2.4f, tween(50))
            }
            shake.animateTo(0f, tween(40))
            sink.animateTo(1f, tween(220, easing = FastOutLinearInEasing))
            shown = next
            sink.snapTo(0f)
            rise.snapTo(0f)
            launch {
                burst.snapTo(0f)
                burst.animateTo(1f, tween(BURST_MS, easing = LinearOutSlowInEasing))
            }
            rise.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = 240f))
            launch { halo.animateTo(0f, tween(500)) }
            delay(260)
        }
        grewTo = next
        growing = false
    }

    val give: () -> Unit = {
        val before: Int = shown
        val after: TreeState? = if (growing) null else onGive()
        if (after != null) {
            scope.launch {
                drop.snapTo(0f)
                drop.animateTo(1f, tween(DROP_MS, easing = FastOutLinearInEasing))
            }
            if (after.stage > before) {
                growing = true
                scope.launch {
                    delay(DROP_MS.toLong())
                    grow(after.stage)
                }
            } else {
                scope.launch {
                    delay(DROP_MS * 4L / 5L)
                    bounce.snapTo(1f)
                    bounce.animateTo(1.04f, tween(110))
                    bounce.animateTo(1f, spring(dampingRatio = 0.4f))
                }
            }
            if (TreeGrowth.petalsAt(after.given)) {
                scope.launch {
                    petals.snapTo(0f)
                    petals.animateTo(1f, tween(PETAL_MS, easing = LinearEasing))
                }
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(Paper)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = give),
    ) {
        Image(
            painter = painterResource(if (shown >= BLOSSOM_STAGE) R.drawable.tree_wash_pink else R.drawable.tree_wash_green),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        // 그림의 흙(그림 높이의 87%)이 막대 바로 위에 오게 놓는다.
        val soilY: Dp = maxHeight - BAR_SPACE
        val art: Dp = minOf(maxWidth * 0.92f, (soilY - 44.dp) / SOIL_AT)
        val top: Dp = soilY - art * SOIL_AT

        // 자랄 때 나무 뒤로 번지는 빛.
        if (halo.value > 0f) {
            Canvas(Modifier.fillMaxSize()) {
                val center = Offset(this.size.width / 2f, (top + art * 0.55f).toPx())
                drawCircle(
                    Brush.radialGradient(
                        listOf(HaloCream.copy(alpha = 0.95f * halo.value), Color.Transparent),
                        center = center,
                        radius = (art * 0.62f).toPx(),
                    ),
                    radius = (art * 0.62f).toPx(),
                    center = center,
                )
            }
        }

        Image(
            painter = painterResource(TREE_ART[shown]),
            contentDescription = tr("벚꽃나무", "Cherry tree", "Cerezo"),
            modifier = Modifier
                .offset(x = (maxWidth - art) / 2, y = top)
                .size(art)
                .graphicsLayer {
                    val s: Float = sink.value
                    val r: Float = rise.value
                    scaleX = bounce.value * (1f - 0.1f * s) * (0.5f + 0.5f * r)
                    scaleY = bounce.value * (1f - 0.3f * s) * (0.12f + 0.88f * r)
                    alpha = 1f - s
                    rotationZ = shake.value
                    transformOrigin = TransformOrigin(0.5f, SOIL_AT)
                },
        )

        if (burst.value < 1f) {
            val t: Float = burst.value
            val colors: List<Color> = if (shown >= BLOSSOM_STAGE) PetalColors else LeafColors
            Canvas(Modifier.fillMaxSize()) {
                val origin = Offset(this.size.width / 2f, (top + art * 0.5f).toPx())
                val leaf = Size(10.dp.toPx(), 6.dp.toPx())
                BURST.forEachIndexed { i, (dx, dy) ->
                    // 위로 퍼졌다가 천천히 떨어진다.
                    val x: Float = origin.x + dx.dp.toPx() * t
                    val y: Float = origin.y + dy.dp.toPx() * t + 70.dp.toPx() * t * t
                    rotate(degrees = i * 47f + t * 260f, pivot = Offset(x, y)) {
                        drawOval(
                            colors[i % colors.size].copy(alpha = (1f - t).coerceIn(0f, 1f)),
                            topLeft = Offset(x - leaf.width / 2, y - leaf.height / 2),
                            size = leaf,
                        )
                    }
                }
            }
        }

        if (drop.value < 1f) {
            val t: Float = drop.value
            Image(
                painter = painterResource(R.drawable.ic_water_drop),
                contentDescription = null,
                modifier = Modifier
                    .offset(x = maxWidth / 2 - 14.dp, y = lerp(18.dp, top + art * 0.55f, t))
                    .size(28.dp)
                    .alpha(if (t > 0.85f) (1f - t) / 0.15f else 1f),
            )
        }

        if (petals.value < 1f) {
            val t: Float = petals.value
            Canvas(Modifier.fillMaxSize()) {
                val petal = Size(10.dp.toPx(), 7.dp.toPx())
                for (i in 0 until PETAL_COUNT) {
                    val x: Float = size.width * ((i * 37 % 100) / 100f) + sin(t * 9f + i) * 14.dp.toPx()
                    val y: Float = size.height * (-0.08f - (i % 4) * 0.07f + t * 1.2f)
                    rotate(degrees = i * 40f + t * 300f, pivot = Offset(x, y)) {
                        drawOval(PetalPink.copy(alpha = 1f - t * 0.4f), topLeft = Offset(x - petal.width / 2, y - petal.height / 2), size = petal)
                    }
                }
            }
        }

        Hint(
            text = if (tree.water > 0) tr("나무를 눌러 물을 주세요", "Tap the tree to water it", "Toca el árbol para regarlo")
            else tr("기록하면 물이 생겨요", "Log spending to get water", "Anota gastos para conseguir agua"),
            strong = tree.water > 0,
            modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
        )
        WaterPill(tree.water, Modifier.align(Alignment.TopEnd).padding(12.dp))

        GrowthBar(tree.given, progress, Modifier.align(Alignment.BottomCenter).padding(12.dp))
    }

    val grown: Int? = grewTo
    if (grown != null) TreeGrowDialog(grown, onDismiss = { grewTo = null })
}

@Composable
private fun Hint(text: String, strong: Boolean, modifier: Modifier) {
    Text(
        text = text,
        color = if (strong) HomePalette.Water else HomePalette.Ink2,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = 0.9f))
            .padding(horizontal = 11.dp, vertical = 5.dp),
    )
}

/** 남은 물. 기록 창 오른쪽 위 알약과 같은 모양이다. */
@Composable
fun WaterPill(water: Int, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(HomePalette.WaterSoft)
            .padding(start = 7.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
    ) {
        Image(painterResource(R.drawable.ic_water_drop), contentDescription = tr("물", "Water", "Agua"), modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(3.dp))
        Text(text = "$water", color = HomePalette.Water, fontSize = 13.5f.sp, fontWeight = FontWeight.Bold, style = Figures)
    }
}

/** «다음 모습까지 · 물 N». 다 자랐으면 꽃잎 이야기로 바뀐다. */
@Composable
private fun GrowthBar(given: Int, progress: Float, modifier: Modifier) {
    val pill = RoundedCornerShape(999.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.92f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        val next: Int? = TreeGrowth.toNext(given)
        if (next == null) {
            Text(
                text = tr("다 자랐어요", "Fully grown", "Ya creció del todo"),
                color = HomePalette.Ink,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = tr(
                    "${TreeGrowth.PETAL_EVERY}방울마다 꽃잎이 흩날려요",
                    "Petals fall every ${TreeGrowth.PETAL_EVERY} drops",
                    "Caen pétalos cada ${TreeGrowth.PETAL_EVERY} gotas",
                ),
                color = HomePalette.Ink2,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            return@Column
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(7.dp)
                .clip(pill)
                .background(HomePalette.Ground),
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .clip(pill)
                    .background(HomePalette.Accent),
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = tr("다음 모습까지", "Until the next look", "Hasta la próxima forma"),
                color = HomePalette.Ink2,
                fontSize = 12.5f.sp,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = tr("물 $next", "$next drops", "$next gotas"),
                color = HomePalette.Ink,
                fontSize = 12.5f.sp,
                fontWeight = FontWeight.Bold,
                style = Figures,
            )
        }
    }
}

/** 단계별 그림. 목업(수채 C, 꽃봉오리는 새잎 G2)에서 그려 넣었다. 자람 팝업([TreeGrowDialog])도 쓴다. */
internal val TREE_ART: List<Int> = listOf(
    R.drawable.tree_stage_0,
    R.drawable.tree_stage_1,
    R.drawable.tree_stage_2,
    R.drawable.tree_stage_3,
    R.drawable.tree_stage_4,
    R.drawable.tree_stage_5,
    R.drawable.tree_stage_6,
    R.drawable.tree_stage_7,
)

/** 이 단계부터 풍경이 분홍빛이 된다(꽃봉오리). */
private const val BLOSSOM_STAGE = 6

/** 그림 위에서 흙이 놓인 높이(그림 높이 대비). */
private const val SOIL_AT = 0.87f

/** 그림 아래 막대가 차지하는 자리. */
private val BAR_SPACE: Dp = 86.dp

private const val DROP_MS = 520
private const val PETAL_MS = 2400
private const val BURST_MS = 1100
private const val PETAL_COUNT = 16

private val Paper = Color(0xFFFBF8F3)
private val PetalPink = Color(0xFFF7B6CA)
private val HaloCream = Color(0xFFFFF4CC)
private val LeafColors = listOf(Color(0xFF5DBB7A), Color(0xFFA6DDB0), Color(0xFF3E9A62))
private val PetalColors = listOf(Color(0xFFF7B6CA), Color(0xFFFBCFDD), Color(0xFFF49AB8))

/** 자랄 때 흩날리는 잎: 가운데에서 (dx, dy) dp 쪽으로. 위로 퍼지게 둔다. */
private val BURST: List<Pair<Float, Float>> = listOf(
    -90f to -60f, 80f to -80f, -60f to -120f, 70f to -40f, -110f to -20f, 100f to -110f,
    -20f to -140f, 30f to -100f, -40f to -70f, 120f to -50f, -130f to -90f, 10f to -60f,
)
