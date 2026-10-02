package com.calc.expense

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.format.DateTimeFormatter

/**
 * 주기 리포트 화면 — 한 주기를 **영수증 한 장**으로 보여 준다.
 *
 * 홈은 «오늘»을 문장 하나로 말하는 화면이라, 리포트까지 같은 문장 머리·점 줄을 쓰면 두 화면이 구별되지 않는다.
 * 리포트는 «끝난 기간의 정산»이라 영수증이 맞다 — 카테고리가 품목처럼 찍히고(금액 큰 순, 다섯 줄 넘으면
 * «그 밖 N개»로 접힘), 쓴 돈·예산, 맨 아래 «남김»에 도장(«잘 지켰어요» / «조금 넘겼어요»). 앱의 영수증 스티커,
 * 도감의 잉크 도장과 같은 결이다.
 *
 * 고정비 후보는 영수증 아래 **한 줄 띠**로 접어 둔다. 누르면 아래에서 시트가 올라와 고른다 — 처음부터 펼쳐 두면
 * 영수증보다 후보 목록이 화면을 차지한다. **시트에서는 처음에 모두 골라져 있다.** 앱이 제안하고 사용자가 빼는
 * 순서라야 «하나씩 고르기»라는 일이 생기지 않는다.
 *
 * @param selected 고른 후보의 이름 키([RecurringCosts.normalize])
 */
@Composable
fun CycleReportScreen(
    report: CycleReport?,
    purseLabel: String,
    selected: Set<String>,
    onToggle: (FixedCostCandidate) -> Unit,
    onApply: () -> Unit,
    onEditFixed: () -> Unit,
    onOpenCategory: (String) -> Unit = {},
    onClose: () -> Unit,
) {
    var sheetOpen: Boolean by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = sheetOpen) { sheetOpen = false }

    Box(modifier = Modifier.fillMaxSize().background(HomePalette.Ground)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = TAB_TOP_PADDING, bottom = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(HomePalette.Card)
                        .clickable(onClick = onClose),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_chevron_left),
                        contentDescription = tr("닫기", "Close", "Cerrar"),
                        tint = HomePalette.Ink2,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(tr("지난 주기 리포트", "Last cycle report", "Informe del ciclo anterior"), color = HomePalette.Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(16.dp))

            if (report == null) {
                Text(tr("불러오는 중이에요", "Loading…", "Cargando…"), color = HomePalette.Ink2, fontSize = 14.sp)
                return@Column
            }

            Receipt(report, purseLabel, onOpenCategory)

            if (report.showsFixedCosts) {
                Spacer(Modifier.height(12.dp))
                if (report.hasCandidates) {
                    FixedStrip(
                        title = tr("고정비 ${report.candidates.size}개를 찾았어요", "Found ${report.candidates.size} fixed costs", "Encontramos ${report.candidates.size} gastos fijos"),
                        line = tr("골라 두면 예산이 정확해져요", "Pick them for a truer budget", "Elígelos para un presupuesto más justo"),
                        onClick = { sheetOpen = true },
                    )
                } else {
                    FixedStrip(
                        title = tr("고정비 직접 적기", "Enter fixed costs", "Anotar gastos fijos"),
                        line = tr("월세·통신비처럼 매달 나가는 돈", "Rent, phone — money that leaves monthly", "Alquiler, móvil: lo que sale cada mes"),
                        onClick = onEditFixed,
                    )
                }
            }

            val error: String? = report.error
            if (error != null) {
                Spacer(Modifier.height(12.dp))
                Text(error, color = HomePalette.Muted, fontSize = 12.sp)
            }
        }

        if (report != null) {
            AnimatedVisibility(visible = sheetOpen, enter = fadeIn(), exit = fadeOut()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Dim)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { sheetOpen = false },
                )
            }
            AnimatedVisibility(
                visible = sheetOpen,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                CandidateSheet(report, selected, onToggle, onApply, onEditFixed)
            }
        }
    }
}

/** 영수증 한 장. 지갑·기간 → 품목(카테고리) → 쓴 돈·예산 → 남김과 도장. */
@Composable
private fun Receipt(report: CycleReport, purseLabel: String, onOpenCategory: (String) -> Unit) {
    var expanded: Boolean by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = CARD_ELEVATION, shape = ReceiptShape)
            .clip(ReceiptShape)
            .background(HomePalette.Card)
            .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 26.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.ic_sticker_receipt),
                contentDescription = null,
                modifier = Modifier.size(38.dp).rotate(-8f),
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(purseLabel, color = HomePalette.Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = report.cycle.start.format(ReceiptDate) + " – " + report.cycle.lastDay.format(ReceiptDate),
                    color = HomePalette.Muted,
                    fontSize = 11.5f.sp,
                    style = Mono,
                )
            }
        }
        DashLine()

        val slices: List<CategorySlice> = report.categories
        if (slices.isEmpty()) {
            Text(tr("적은 게 없어요", "Nothing logged", "Nada anotado"), color = HomePalette.Muted, fontSize = 13.5f.sp, modifier = Modifier.padding(vertical = 4.dp))
        } else {
            slices.take(RECEIPT_LINES).forEach { ItemLine(it, light = false, onOpenCategory) }
            val rest: List<CategorySlice> = slices.drop(RECEIPT_LINES)
            if (rest.isNotEmpty()) {
                if (expanded) rest.forEach { ItemLine(it, light = true, onOpenCategory) }
                MoreLine(
                    label = if (expanded) tr("접기", "Less", "Menos") + " ▲"
                    else tr("그 밖 ${rest.size}개", "${rest.size} more", "${rest.size} más") + " ▼",
                    amount = if (expanded) null else rest.sumOf { it.amount },
                    onClick = { expanded = !expanded },
                )
            }
        }
        DashLine()

        ReceiptLine(tr("쓴 돈", "Spent", "Gastado"), StatusText.figure(report.spent), strong = true)
        if (report.hasBudget) {
            ReceiptLine(tr("예산", "Budget", "Presupuesto"), StatusText.figure(report.budget), strong = false)
            DashLine()
            val kept: Boolean = report.left >= 0L
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                Text(
                    text = if (kept) tr("남김", "Left", "Sobró") else tr("넘김", "Over", "De más"),
                    color = if (kept) HomePalette.Accent else HomePalette.Over,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = StatusText.figure(if (kept) report.left else -report.left),
                    color = if (kept) HomePalette.Accent else HomePalette.Over,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Black,
                    style = Mono,
                )
            }
            Box(contentAlignment = Alignment.CenterEnd, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                InkStamp(
                    lines = if (kept) listOf(tr("잘", "Well", "Bien"), tr("지켰어요", "kept", "hecho"))
                    else listOf(tr("조금", "Went", "Te"), tr("넘겼어요", "over", "pasaste")),
                    ink = if (kept) HomePalette.Accent else HomePalette.Over,
                    size = 64.dp,
                    tilt = -12f,
                    seed = report.cycle.start.dayOfYear,
                )
            }
        }
    }
}

/** 품목 한 줄. 누르면 그 카테고리가 이름별로 펼쳐진다(통계 탭과 같은 문). */
@Composable
private fun ItemLine(slice: CategorySlice, light: Boolean, onOpenCategory: (String) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(if (light) LightRow else Color.Transparent)
            .clickable { onOpenCategory(slice.name) }
            .padding(horizontal = if (light) 6.dp else 0.dp, vertical = 5.dp),
    ) {
        Text(L10n.name(slice.name), color = HomePalette.Ink2, fontSize = 13.5f.sp, modifier = Modifier.weight(1f))
        Text(StatusText.figure(slice.amount), color = HomePalette.Ink, fontSize = 13.5f.sp, fontWeight = FontWeight.Bold, style = Mono)
    }
}

@Composable
private fun MoreLine(label: String, amount: Long?, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
    ) {
        Text(label, color = HomePalette.Accent, fontSize = 13.5f.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (amount != null) Text(StatusText.figure(amount), color = HomePalette.Ink2, fontSize = 13.5f.sp, fontWeight = FontWeight.Bold, style = Mono)
    }
}

@Composable
private fun ReceiptLine(label: String, value: String, strong: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            label,
            color = if (strong) HomePalette.Ink else HomePalette.Ink2,
            fontSize = if (strong) 14.5f.sp else 13.5f.sp,
            fontWeight = if (strong) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            color = if (strong) HomePalette.Ink else HomePalette.Ink2,
            fontSize = if (strong) 14.5f.sp else 13.5f.sp,
            fontWeight = if (strong) FontWeight.Bold else FontWeight.Normal,
            style = Mono,
        )
    }
}

/** 영수증의 점선. */
@Composable
private fun DashLine() {
    Canvas(Modifier.fillMaxWidth().padding(vertical = 10.dp).height(1.5.dp)) {
        drawLine(
            color = DashColor,
            start = Offset(0f, size.height / 2f),
            end = Offset(size.width, size.height / 2f),
            strokeWidth = size.height,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())),
        )
    }
}

/** 영수증 아래 한 줄 띠. 고정비 후보가 있으면 시트를, 없으면 직접 적기를 연다. */
@Composable
private fun FixedStrip(title: String, line: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = CARD_ELEVATION, shape = shape)
            .clip(shape)
            .background(HomePalette.Card)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Image(painterResource(R.drawable.ic_budget_wallet), contentDescription = null, modifier = Modifier.size(40.dp).rotate(-8f))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = HomePalette.Ink, fontSize = 14.5f.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(line, color = HomePalette.Ink2, fontSize = 12.sp, maxLines = 1)
        }
        Chevron(size = 18.dp)
    }
}

/**
 * 아래에서 올라오는 고정비 시트. 맨 아래에 «이걸 빼면 얼마 남나»를 함께 보여준다 — 체크를 하나 뺄 때마다
 * 숫자가 움직여야 사용자가 «이 줄이 내 다음 달을 얼마나 바꾸나»를 느낄 수 있다.
 */
@Composable
private fun CandidateSheet(
    report: CycleReport,
    selected: Set<String>,
    onToggle: (FixedCostCandidate) -> Unit,
    onApply: () -> Unit,
    onEditFixed: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
            .background(HomePalette.Card)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .navigationBarsPadding()
            .heightIn(max = 620.dp)
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 16.dp),
    ) {
        Box(Modifier.align(Alignment.CenterHorizontally).size(width = 40.dp, height = 4.dp).clip(CircleShape).background(HomePalette.Line))
        Spacer(Modifier.height(14.dp))
        Text(tr("고정비로 보여요", "Looks like fixed costs", "Parecen gastos fijos"), color = HomePalette.Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(3.dp))
        // 근거가 다르면 안내도 달라야 한다. 「되풀이를 찾았다」와 「아직 한 주기뿐」을 같은 말로 소개하면
        // 사용자가 뒤쪽을 앞쪽만큼 믿어 버린다.
        val confirmed: Boolean = report.candidates.any { it.repeated }
        Text(
            text = if (confirmed) tr("아닌 건 체크를 빼 주세요", "Uncheck any that aren't fixed", "Desmarca los que no sean fijos")
            else tr("아직 한 주기뿐이라 큰 금액부터 늘어놨어요", "Only one cycle so far — largest first", "Solo un ciclo: de mayor a menor"),
            color = HomePalette.Ink2,
            fontSize = 12.5f.sp,
        )
        Spacer(Modifier.height(8.dp))

        val chosen: List<FixedCostCandidate> =
            report.candidates.filter { RecurringCosts.normalize(it.name) in selected }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            for (candidate in report.candidates) {
                CandidateRow(
                    candidate = candidate,
                    checked = RecurringCosts.normalize(candidate.name) in selected,
                    onToggle = { onToggle(candidate) },
                )
            }
        }

        // 기록에서 온 줄은 사용자가 지금까지 「지출」로 적어 오던 것이다. 고정비로 옮기고도 계속 적으면
        // 예산에서 한 번, 지출에서 또 한 번 빠져 두 번 깎인다. 화면이 말해 주지 않으면 알 방법이 없다.
        if (chosen.any { it.fromRecord }) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = tr(
                    "‘기록’에서 온 건 고정비에 넣은 뒤로는 따로 적지 않아도 돼요. 둘 다 하면 같은 돈이 두 번 빠져요.",
                    "Once an ‘entry’ item is a fixed cost, stop logging it — doing both counts it twice.",
                    "Cuando un «gasto» pasa a fijo, deja de anotarlo: si haces ambas cosas, se descuenta dos veces.",
                ),
                color = HomePalette.Muted,
                fontSize = 11.5f.sp,
            )
        }

        val next: Long = report.recommendedWith(chosen)
        if (next > 0L) {
            Spacer(Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(HomePalette.Soft)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Text(tr("이번 주기 예산", "This cycle's budget", "Presupuesto de este ciclo"), color = HomePalette.Accent, fontSize = 12.5f.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(StatusText.won(next), color = HomePalette.Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold, style = Figures)
            }
        } else if (report.plan.monthlyIncome <= 0L) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = tr(
                    "월급을 적어 두면 여기서 다음 주기 예산까지 정할 수 있어요.",
                    "Enter your income to set next cycle's amount right here.",
                    "Anota tu sueldo para fijar aquí mismo la meta del próximo ciclo.",
                ),
                color = HomePalette.Muted,
                fontSize = 12.sp,
            )
        }

        Spacer(Modifier.height(14.dp))
        Button(
            onClick = onApply,
            enabled = chosen.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = HomePalette.AccentBright,
                contentColor = HomePalette.Card,
                disabledContainerColor = HomePalette.Chip,
                disabledContentColor = HomePalette.Muted,
            ),
        ) {
            Text(tr("고정비에 넣기", "Add to fixed costs", "Añadir a gastos fijos"), fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        TextButton(onClick = onEditFixed, modifier = Modifier.fillMaxWidth()) {
            Text(tr("직접 더하거나 고치기", "Add or edit yourself", "Añadir o editar a mano"), color = HomePalette.Ink2)
        }
    }
}

/** 후보 한 줄. 줄 전체가 누를 수 있는 자리다 — 작은 체크박스만 노리게 하지 않는다. */
@Composable
private fun CandidateRow(candidate: FixedCostCandidate, checked: Boolean, onToggle: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onToggle)
            .padding(vertical = 4.dp),
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(
                checkedColor = HomePalette.AccentBright,
                uncheckedColor = HomePalette.Muted,
                checkmarkColor = HomePalette.Card,
            ),
        )
        Spacer(Modifier.width(4.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = candidate.name,
                color = HomePalette.Ink,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                // 출처와 «평균인가»를 함께 밝힌다. 평균이라는 말이 없으면 사용자는 그 숫자를
                // 지난달에 실제로 나간 돈으로 읽고, 다르다고 생각해 그 줄을 빼 버린다.
                // 되풀이를 확인하지 못한 줄은 그 사실을 앞세운다 — 근거가 다르면 말도 달라야 한다.
                text = (
                    if (candidate.repeated) tr("${candidate.cycles}번 나갔어요", "Paid ${candidate.cycles} times", "Pagado ${candidate.cycles} veces")
                    else tr("아직 한 번", "Only once so far", "Solo una vez")
                    ) +
                    (if (candidate.fromRecord) tr(" · 기록", " · entry", " · gasto") else tr(" · 알림", " · alert", " · aviso")) +
                    (if (candidate.averaged) tr(" · 평균", " · average", " · media") else ""),
                color = HomePalette.Muted,
                fontSize = 11.sp,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = StatusText.won(candidate.amount),
            color = HomePalette.Ink,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            style = Figures,
        )
    }
}


/** 영수증 모양: 위는 둥글고 아래는 톱니. */
private object ReceiptShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val tooth: Float = with(density) { 8.dp.toPx() }
        val corner: Float = with(density) { 16.dp.toPx() }
        val w: Float = size.width
        val h: Float = size.height
        val teeth: Int = maxOf(1, (w / (tooth * 2f)).toInt())
        val step: Float = w / teeth
        val path = Path().apply {
            moveTo(0f, corner)
            cubicTo(0f, corner * 0.45f, corner * 0.45f, 0f, corner, 0f)
            lineTo(w - corner, 0f)
            cubicTo(w - corner * 0.45f, 0f, w, corner * 0.45f, w, corner)
            lineTo(w, h - tooth)
            for (i in 0 until teeth) {
                val right: Float = w - i * step
                lineTo(right - step / 2f, h)
                lineTo(right - step, h - tooth)
            }
            close()
        }
        return Outline.Generic(path)
    }
}

/** 영수증 숫자 — 고정폭이라 자릿수가 세로로 맞는다. */
private val Mono: TextStyle = TextStyle(fontFamily = FontFamily.Monospace, fontFeatureSettings = "tnum")

private const val RECEIPT_LINES = 5
private val ReceiptDate: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd")
private val DashColor = Color(0xFFD7DEDA)
private val LightRow = Color(0xFFF6FBF8)
private val Dim = Color(0x730F1A17)
