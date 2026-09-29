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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import java.time.LocalDate

/**
 * 통계 탭. 위는 주간 추이 막대 그래프, 아래는 카테고리 도넛.
 *
 * 채점하지 않는다 — 덜/더 썼다는 사실만. 도넛·막대는 라이브러리 없이 Canvas 로 직접 그린다.
 */
@Composable
fun StatsScreen(
    data: StatsData,
    onToggleCategoryMonth: () -> Unit,
    onOpenReport: () -> Unit = {},
    onOpenCategory: (String) -> Unit = {},
    onOpenSettings: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HomePalette.Ground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = tr("통계", "Stats", "Estadísticas"),
                color = HomePalette.Ink,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            SettingsGear(onOpenSettings)
        }
        Spacer(Modifier.height(16.dp))

        TrendCard(data)
        Spacer(Modifier.height(12.dp))
        CategoryCard(data, onToggleCategoryMonth, onOpenCategory)
        Spacer(Modifier.height(12.dp))
        ReportCard(onOpenReport)
    }
}

/**
 * 주기 리포트로 가는 문.
 *
 * 리포트는 결산 팝업에서도 열리지만 그 팝업은 주기가 바뀐 그 순간 한 번만 뜬다. 자기 고정비가
 * 궁금해지는 때는 대개 그 순간이 아니라 «이번 달도 왜 이렇게 썼지» 하고 통계를 들여다볼 때다.
 * 그래서 늘 있는 자리에 문을 하나 더 둔다.
 */
@Composable
private fun ReportCard(onOpenReport: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(HomePalette.Card)
            .clickable(onClick = onOpenReport)
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        IconBadge(R.drawable.ic_repeat, HomePalette.Gold, size = 38.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(tr("지난 주기 리포트", "Last cycle report", "Informe del ciclo anterior"), color = HomePalette.Ink, fontSize = 15.5f.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            Text(
                text = tr(
                    "달마다 되풀이되는 결제를 찾아 고정비를 정리해요",
                    "Find monthly repeating payments and sort out fixed costs",
                    "Encuentra pagos que se repiten cada mes y ordena tus gastos fijos",
                ),
                color = HomePalette.Ink2,
                fontSize = 12.5f.sp,
            )
        }
        Spacer(Modifier.width(8.dp))
        Chevron(size = 22.dp)
    }
}

/**
 * 최근 7일 카드 — 목표 대비를 주로 보여주고, 견줄 지난주가 있을 때만 비교 줄을 덧붙인다.
 *
 * 판정은 하나만 한다. 예전에는 «남았어요»(초록)와 «이전 7일보다 더 썼어요»(빨강)가 한 카드에
 * 같이 떠서 잘한 건지 못한 건지 알 수 없었다. 지난주 비교는 회색 한 줄로 내린다.
 * 판정은 [WeekTrends] 가 한다 — 여기서는 그리기만 한다.
 */
@Composable
private fun TrendCard(data: StatsData) {
    val trend: WeekTrend = WeekTrends.of(
        spent = data.recent7,
        budget = data.week7Budget,
        prevSpent = data.prev7,
    )
    CardBox {
        Text(text = tr("최근 7일", "Last 7 days", "Últimos 7 días"), color = HomePalette.Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = StatusText.won(trend.spent),
                color = HomePalette.Ink,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.5).sp,
                style = Figures,
                modifier = Modifier.weight(1f),
            )
            if (trend.hasBudget) {
                // 목표는 어림한 숫자로 — «606,662원» 같은 끝자리는 사람이 정한 목표로 읽히지 않는다.
                Text(
                    text = tr("목표 약 ", "Target about ", "Meta aprox. ") + StatusText.approxWon(trend.budget),
                    color = HomePalette.Muted,
                    fontSize = 12.5f.sp,
                    modifier = Modifier.padding(bottom = 5.dp),
                )
            }
        }

        if (trend.hasBudget) {
            Spacer(Modifier.height(10.dp))
            Gauge(percent = trend.percent)
            Spacer(Modifier.height(8.dp))
            Text(
                text =
                    if (trend.left >= 0L) tr(
                        "목표보다 ${StatusText.approxWon(trend.left)} 덜 썼어요",
                        "${StatusText.approxWon(trend.left)} under target",
                        "${StatusText.approxWon(trend.left)} por debajo de la meta",
                    )
                    else tr(
                        "목표보다 ${StatusText.approxWon(-trend.left)} 더 썼어요",
                        "${StatusText.approxWon(-trend.left)} over target",
                        "${StatusText.approxWon(-trend.left)} por encima de la meta",
                    ),
                color = if (trend.left >= 0L) HomePalette.Accent else HomePalette.Over,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Spacer(Modifier.height(22.dp))
        WeekChart(
            days = data.daily14.takeLast(7),
            dailyTarget = if (trend.hasBudget) trend.budget / 7L else 0L,
        )

        // 견줄 지난주가 없으면 줄 자체가 없다 — Ledger.vsLastCycle 과 같은 규칙.
        val diff: Long? = trend.vsPrev
        if (diff != null) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_trend), contentDescription = null, tint = HomePalette.Muted, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = when {
                        diff < 0L -> tr(
                            "지난 7일보다 ${StatusText.approxWon(diff)} 덜 썼어요",
                            "${StatusText.approxWon(diff)} less than the previous 7 days",
                            "${StatusText.approxWon(diff)} menos que los 7 días anteriores",
                        )
                        diff > 0L -> tr(
                            "지난 7일보다 ${StatusText.approxWon(diff)} 더 썼어요",
                            "${StatusText.approxWon(diff)} more than the previous 7 days",
                            "${StatusText.approxWon(diff)} más que los 7 días anteriores",
                        )
                        else -> tr("지난 7일과 똑같이 썼어요", "Same as the previous 7 days", "Igual que los 7 días anteriores")
                    },
                    color = HomePalette.Ink2,
                    fontSize = 13.sp,
                )
            }
        } else if (!trend.hasBudget) {
            Spacer(Modifier.height(11.dp))
            Text(
                text = tr(
                    "설정에서 예산을 정하면 목표와 비교해 보여 드려요",
                    "Set a budget in Settings to compare against a target",
                    "Define un presupuesto en Ajustes para compararlo con una meta",
                ),
                color = HomePalette.Muted,
                fontSize = 12.sp,
            )
        }
    }
}

/**
 * 요일별 막대 7개. 하루 목표를 점선으로 긋고, 넘긴 날만 빨강으로 칠해 금액을 얹는다.
 *
 * 예전에는 이전 7일까지 14개를 요일도 금액도 없이 그려서 어느 막대가 무슨 날인지 읽을 수 없었다.
 */
@Composable
private fun WeekChart(days: List<Long>, dailyTarget: Long) {
    val chartHeight = 140.dp
    val top: Long = maxOf(days.maxOrNull() ?: 0L, dailyTarget).coerceAtLeast(1L)
    // 가장 큰 막대 위에 금액 글자가 올라갈 자리를 남긴다.
    val scale: Float = 1f / (top * 1.15f)
    val today: LocalDate = LocalDate.now()

    Box(modifier = Modifier.fillMaxWidth().height(chartHeight)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            days.forEach { amount ->
                val fraction: Float = (amount * scale).coerceIn(0f, 1f)
                val over: Boolean = dailyTarget > 0L && amount > dailyTarget
                Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.BottomCenter) {
                    if (amount > 0L) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.68f)
                                .fillMaxHeight(fraction)
                                .clip(RoundedCornerShape(topStart = 7.dp, topEnd = 7.dp, bottomStart = 2.dp, bottomEnd = 2.dp))
                                .background(if (over) HomePalette.Over else HomePalette.AccentBright),
                        )
                    }
                    if (over) {
                        Text(
                            text = StatusText.approxShort(amount),
                            color = HomePalette.Over,
                            fontSize = 10.5f.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            style = Figures,
                            modifier = Modifier.padding(bottom = chartHeight * fraction + 2.dp),
                        )
                    }
                }
            }
        }
        if (dailyTarget > 0L) {
            val targetFraction: Float = (dailyTarget * scale).coerceIn(0f, 1f)
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .align(Alignment.BottomStart)
                    .padding(bottom = 0.dp)
                    .offset(y = -(chartHeight * targetFraction)),
            ) {
                drawLine(
                    color = HomePalette.Muted,
                    start = Offset(0f, size.height / 2f),
                    end = Offset(size.width, size.height / 2f),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
                )
            }
            Text(
                text = tr("하루 목표 ", "Daily target ", "Meta diaria ") + StatusText.approxShort(dailyTarget),
                color = HomePalette.Ink2,
                fontSize = 10.5f.sp,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(y = -(chartHeight * targetFraction) - 4.dp)
                    .background(HomePalette.Card)
                    .padding(start = 4.dp),
            )
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(HomePalette.Line))
    Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        for (i in 0 until days.size) {
            val day: LocalDate = today.minusDays((days.size - 1 - i).toLong())
            Text(
                text = if (i == days.size - 1) tr("오늘", "Today", "Hoy") else day.format(L10n.weekday()),
                color = HomePalette.Ink2,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.weight(1f),
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
            .height(8.dp)
            .clip(RoundedCornerShape(99.dp))
            .background(HomePalette.Chip),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(percent.coerceIn(0, 100) / 100f)
                .height(8.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(if (percent > 100) HomePalette.Over else HomePalette.AccentBright),
        )
    }
}

/**
 * 카테고리 카드 — 위에 도넛, 아래에 목록.
 *
 * 예전에는 도넛과 목록을 나란히 둬서 목록이 길면 도넛 위아래가 비었다. 큰 것 다섯 개만 따로
 * 두고 나머지는 한 줄로 묶는다([CategoryBreakdown.topWithRest]) — 색이 8가지뿐이라 다 그리면
 * 같은 색이 두 번 나왔다. 합계는 도넛 가운데 한 번만 쓴다.
 */
@Composable
private fun CategoryCard(data: StatsData, onToggle: () -> Unit, onOpenCategory: (String) -> Unit) {
    CardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = tr("카테고리", "Categories", "Categorías"),
                color = HomePalette.Ink,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            CycleSwitch(data.categoryCycleLabel, onToggle)
        }
        // 월급날이 달 중간이면 «이번 주기»가 달력 달과 어긋난다. 어느 날부터 어느 날까지인지
        // 밝히지 않으면 이 숫자가 무엇을 센 것인지 알 수 없다.
        Text(text = data.categoryCycleRange, color = HomePalette.Muted, fontSize = 12.5f.sp, style = Figures)

        Spacer(Modifier.height(12.dp))
        when {
            data.error != null ->
                Text(text = data.error, color = HomePalette.Over, fontSize = 13.sp)
            data.loadingCategories ->
                Text(text = tr("불러오는 중…", "Loading…", "Cargando…"), color = HomePalette.Muted, fontSize = 13.sp)
            data.categories.isEmpty() ->
                Text(
                    text = tr(
                        "이 주기에는 기록이 없어요. 지출을 적으면 여기에 나와요.",
                        "Nothing logged this cycle. Your spending will show up here.",
                        "No hay gastos en este ciclo. Aquí aparecerán cuando anotes.",
                    ),
                    color = HomePalette.Ink2,
                    fontSize = 13.sp,
                )
            else -> {
                val groups: List<CategoryGroup> = CategoryBreakdown.topWithRest(data.categories)
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Donut(groups, data.categoryTotal, modifier = Modifier.size(196.dp))
                }
                Spacer(Modifier.height(10.dp))
                groups.forEachIndexed { i, group ->
                    if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(HomePalette.Line))
                    CategoryRow(group, groupColor(i, group), onOpenCategory)
                }
            }
        }
    }
}

/** 큰 것들은 팔레트 앞쪽 색을 차례로, «나머지»는 회색. */
private fun groupColor(index: Int, group: CategoryGroup): Color =
    if (group.isRest) HomePalette.CategoryColors.last() else HomePalette.categoryColor(index)

/** ‹ 이번 주기 › — 두 주기(이번·지난) 사이를 오간다. 갈 곳이 없는 쪽 꺾쇠는 흐리게. */
@Composable
private fun CycleSwitch(label: String, onToggle: () -> Unit) {
    val onThisCycle: Boolean = label == tr("이번 주기", "This cycle", "Este ciclo")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(HomePalette.Soft)
            .clickable(onClick = onToggle)
            .padding(horizontal = 4.dp, vertical = 3.dp),
    ) {
        Icon(
            painterResource(R.drawable.ic_chevron_left),
            contentDescription = tr("지난 주기", "Last cycle", "Ciclo anterior"),
            tint = if (onThisCycle) HomePalette.Accent else HomePalette.Accent.copy(alpha = 0.3f),
            modifier = Modifier.size(22.dp).padding(3.dp),
        )
        Text(text = label, color = HomePalette.Accent, fontSize = 12.5f.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Icon(
            painterResource(R.drawable.ic_chevron_right),
            contentDescription = tr("이번 주기", "This cycle", "Este ciclo"),
            tint = if (onThisCycle) HomePalette.Accent.copy(alpha = 0.3f) else HomePalette.Accent,
            modifier = Modifier.size(22.dp).padding(3.dp),
        )
    }
}

/** 목록 한 줄. 누르면 그 카테고리가 이름별로 펼쳐진다 — «나머지» 줄은 묶음이라 누르지 않는다. */
@Composable
private fun CategoryRow(group: CategoryGroup, color: Color, onOpenCategory: (String) -> Unit) {
    val name: String? = group.name
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (name != null) Modifier.clickable { onOpenCategory(name) } else Modifier)
            .padding(vertical = 10.dp),
    ) {
        if (name != null) CategoryBadge(name, color) else IconBadge(R.drawable.ic_cat_other, color, size = 34.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (name != null) L10n.name(name) else tr("나머지 ${group.restNames.size}개", "${group.restNames.size} more", "${group.restNames.size} más"),
                color = HomePalette.Ink,
                fontSize = 14.5f.sp,
                fontWeight = FontWeight.Medium,
            )
            if (name == null) {
                Text(
                    text = group.restNames.joinToString(" · ") { L10n.name(it) },
                    color = HomePalette.Muted,
                    fontSize = 11.5f.sp,
                    lineHeight = 15.sp,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(StatusText.won(group.amount), color = HomePalette.Ink, fontSize = 14.5f.sp, fontWeight = FontWeight.Bold, maxLines = 1, style = Figures)
            Text("${group.percent}%", color = HomePalette.Muted, fontSize = 11.5f.sp, style = Figures)
        }
    }
}

/** 카테고리 아이콘. 기본 카테고리는 그림으로, 사용자가 만든 카테고리는 첫 글자로. */
@Composable
private fun CategoryBadge(storedName: String, color: Color) {
    val icon: Int? = CategoryIcons.of(storedName)
    if (icon != null) {
        IconBadge(icon, color, size = 34.dp)
        return
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(color.copy(alpha = 0.14f)),
    ) {
        Text(text = L10n.name(storedName).take(1), color = color, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

/** 연한 색 바탕의 둥근 칸 안에 같은 색 선 아이콘. */
@Composable
private fun IconBadge(icon: Int, color: Color, size: Dp) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(size).clip(RoundedCornerShape(size / 3)).background(color.copy(alpha = 0.14f)),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = color, modifier = Modifier.size(size / 2))
    }
}

/** 카테고리 비중 도넛. 각 세그먼트 sweep 은 금액 비례, 가운데에 이 주기 합계를 한 번만 쓴다. */
@Composable
private fun Donut(groups: List<CategoryGroup>, total: Long, modifier: Modifier) {
    val sum: Long = groups.sumOf { it.amount }.coerceAtLeast(1L)
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokePx: Float = 22.dp.toPx()
            val gapDeg = 2f
            val diameter: Float = size.minDimension - strokePx
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val arcSize = Size(diameter, diameter)
            var start = -90f
            groups.forEachIndexed { i, group ->
                val sweep: Float = group.amount.toFloat() / sum.toFloat() * 360f
                drawArc(
                    color = groupColor(i, group),
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
            Text(StatusText.won(total), color = HomePalette.Ink, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, style = Figures)
            Text(tr("이번 주기 합계", "Cycle total", "Total del ciclo"), color = HomePalette.Muted, fontSize = 11.5f.sp)
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
