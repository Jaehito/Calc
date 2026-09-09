package com.calc.expense

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Figures = TextStyle(fontFeatureSettings = "tnum")

/**
 * 통계 탭. 위는 주간 추이 막대 그래프, 아래는 카테고리 도넛.
 *
 * 채점하지 않는다 — 덜/더 썼다는 사실만. 도넛·막대는 라이브러리 없이 Canvas 로 직접 그린다.
 */
@Composable
fun StatsScreen(
    data: StatsData,
    onToggleCategoryMonth: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HomePalette.Ground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
    ) {
        Text(text = "통계", color = HomePalette.Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        TrendCard(data)
        Spacer(Modifier.height(12.dp))
        CategoryCard(data, onToggleCategoryMonth)
    }
}

/**
 * 최근 7일 카드 — 목표 대비를 주로 보여주고, 견줄 지난주가 있을 때만 비교 줄을 덧붙인다.
 *
 * 예전에는 «이전 7일 대비»만 있어서 지난주 기록이 없으면(설치 첫 주) 0원 쓴 주와 견주게 되고,
 * 무조건 «더 썼어요»가 빨갛게 떴다. 판정은 [WeekTrends] 가 한다 — 여기서는 그리기만 한다.
 */
@Composable
private fun TrendCard(data: StatsData) {
    val trend: WeekTrend = WeekTrends.of(
        spent = data.recent7,
        budget = data.week7Budget,
        prevSpent = data.prev7,
    )
    CardBox {
        Text(text = "최근 7일", color = HomePalette.Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))

        if (trend.hasBudget) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = StatusText.won(trend.spent),
                    color = HomePalette.Ink,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    style = Figures,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "목표 " + StatusText.won(trend.budget),
                    color = HomePalette.Muted,
                    fontSize = 12.sp,
                    style = Figures,
                )
            }
            Spacer(Modifier.height(9.dp))
            Gauge(percent = trend.percent)
            Spacer(Modifier.height(9.dp))
            Text(
                text =
                    if (trend.left >= 0L) StatusText.won(trend.left) + " 남았어요"
                    else StatusText.won(-trend.left) + " 넘겼어요",
                color = if (trend.left >= 0L) HomePalette.Accent else HomePalette.Over,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                style = Figures,
            )
            Spacer(Modifier.height(16.dp))
        }

        val bars: List<Long> = data.daily14
        val max: Long = bars.maxOrNull() ?: 0L
        Canvas(modifier = Modifier.fillMaxWidth().height(76.dp)) {
            val n: Int = bars.size.coerceAtLeast(1)
            val gap: Float = 5.dp.toPx()
            val bw: Float = (size.width - gap * (n - 1)) / n
            val radius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
            bars.forEachIndexed { i, v ->
                val h: Float = if (max > 0L) (v.toFloat() / max.toFloat()) * size.height else 0f
                val x: Float = i * (bw + gap)
                val color: Color = if (i >= n - 7) HomePalette.AccentBright else HomePalette.Chip
                drawRoundRect(
                    color = color,
                    topLeft = Offset(x, size.height - h),
                    size = Size(bw, h),
                    cornerRadius = radius,
                )
            }
        }
        Spacer(Modifier.height(9.dp))
        Row {
            Text("이전 7일", color = HomePalette.Muted, fontSize = 11.sp, modifier = Modifier.weight(1f))
            Text("최근 7일", color = HomePalette.Muted, fontSize = 11.sp)
        }

        // 견줄 지난주가 없으면 줄 자체가 없다 — Ledger.vsLastCycle 과 같은 규칙.
        val diff: Long? = trend.vsPrev
        if (diff != null) {
            Spacer(Modifier.height(11.dp))
            Text(
                text = when {
                    diff < 0L -> "이전 7일보다 " + StatusText.won(-diff) + " 덜 썼어요"
                    diff > 0L -> "이전 7일보다 " + StatusText.won(diff) + " 더 썼어요"
                    else -> "이전 7일과 똑같이 썼어요"
                },
                color = when {
                    diff < 0L -> HomePalette.Accent
                    diff > 0L -> HomePalette.Over
                    else -> HomePalette.Muted
                },
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                style = Figures,
            )
        } else if (!trend.hasBudget) {
            Spacer(Modifier.height(11.dp))
            Text(
                text = "설정에서 예산을 정하면 목표 대비로 보여드려요",
                color = HomePalette.Muted,
                fontSize = 12.sp,
            )
        }
    }
}

/** 목표 대비 게이지. 넘긴 만큼은 빨강으로 채운다 — 넘겼다는 사실을 색으로도 말한다. */
@Composable
private fun Gauge(percent: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(7.dp)
            .clip(RoundedCornerShape(99.dp))
            .background(HomePalette.Chip),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(percent.coerceIn(0, 100) / 100f)
                .height(7.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(if (percent > 100) HomePalette.Over else HomePalette.AccentBright),
        )
    }
}

/** 카테고리 도넛 + 범례. 저장소에서 읽어 온 카테고리별 합계를 그린다. */
@Composable
private fun CategoryCard(data: StatsData, onToggle: () -> Unit) {
    CardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "카테고리 · ${data.categoryMonthLabel}",
                color = HomePalette.Ink,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (data.categoryMonthLabel == "이번 달") "지난 달 보기" else "이번 달 보기",
                color = HomePalette.Accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(HomePalette.Soft)
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
        Spacer(Modifier.height(16.dp))

        when {
            data.error != null ->
                Text(text = data.error, color = HomePalette.Over, fontSize = 13.sp)
            data.loadingCategories ->
                Text(text = "불러오는 중…", color = HomePalette.Muted, fontSize = 13.sp)
            data.categories.isEmpty() ->
                Text(
                    text = "이 달에는 카테고리 지출이 없습니다. 기록할 때 카테고리 칩을 고르면 여기에 나옵니다.",
                    color = HomePalette.Ink2,
                    fontSize = 13.sp,
                )
            else -> DonutAndLegend(data)
        }
    }
}

@Composable
private fun DonutAndLegend(data: StatsData) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Donut(data.categories, modifier = Modifier.size(128.dp))
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            data.categories.forEachIndexed { i, slice ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(HomePalette.categoryColor(i)),
                    )
                    Spacer(Modifier.width(9.dp))
                    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
                        Text(slice.name, color = HomePalette.Ink, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.width(6.dp))
                        Text(StatusText.figure(slice.amount), color = HomePalette.Muted, fontSize = 10.sp, style = Figures)
                    }
                    Text("${slice.percent}%", color = HomePalette.Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, style = Figures)
                }
            }
        }
    }
    Spacer(Modifier.height(16.dp))
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(HomePalette.Line))
    Spacer(Modifier.height(14.dp))
    Text(
        text = "이 달 합계 ${StatusText.won(data.categoryTotal)}",
        color = HomePalette.Ink2,
        fontSize = 12.5f.sp,
        style = Figures,
    )
}

/** 카테고리 비중 도넛. 각 세그먼트 sweep 은 금액 비례, 가운데는 비워 총액 텍스트를 올린다. */
@Composable
private fun Donut(slices: List<CategorySlice>, modifier: Modifier) {
    val total: Long = slices.sumOf { it.amount }.coerceAtLeast(1L)
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokePx: Float = 15.dp.toPx()
            val gapDeg = 2.5f
            val diameter: Float = size.minDimension - strokePx
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val arcSize = Size(diameter, diameter)
            var start = -90f
            slices.forEachIndexed { i, slice ->
                val sweep: Float = slice.amount.toFloat() / total.toFloat() * 360f
                drawArc(
                    color = HomePalette.categoryColor(i),
                    startAngle = start + gapDeg / 2f,
                    sweepAngle = (sweep - gapDeg).coerceAtLeast(0f),
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx),
                )
                start += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(StatusText.won(total), color = HomePalette.Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold, style = Figures)
            Text("합계", color = HomePalette.Muted, fontSize = 10.5f.sp)
        }
    }
}

@Composable
private fun CardBox(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(HomePalette.Card)
            .padding(20.dp),
    ) {
        content()
    }
}
