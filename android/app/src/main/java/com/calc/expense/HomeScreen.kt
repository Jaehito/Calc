package com.calc.expense

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 홈 화면. 한 번에 지갑 하나만 보인다 — 개인·공용은 아래 가운데 떠 있는 토글로 바꾼다.
 *
 * 맨 위는 앱 아이콘과 설정, 그 아래 첫 줄이 **오늘 쓸 수 있는 돈**을 문장으로 말한다
 * («오늘은 112,300원까지 써도 괜찮아요»). 넘겼으면 넘긴 금액만 빨강. 날짜·지갑 이름 줄은 없다 —
 * 날짜는 폰 위에 이미 있고, 지갑은 토글이 말한다.
 *
 * 그 아래 카드가 그 돈이 어디서 왔는지(하루치·곳간·오늘 쓴 돈)와 월급날까지의 막대, 맨 아래 두 칸이
 * 그 숫자를 정하는 두 값(한 달 예산·월급날)이다. 두 칸은 누르면 그 값을 고치는 창이 바로 뜬다.
 *
 * 오늘 쓴 항목 목록은 일부러 넣지 않았다. 지금 앱은 날짜별 합계만 캐시하므로
 * 항목을 보여주려면 홈에 들어올 때마다 저장소를 왕복해야 한다.
 */
@Composable
fun HomeScreen(
    /** 고른 지갑의 숫자. 그 지갑에 예산이 없으면 null — 예산을 정하라는 카드를 보인다. */
    snapshot: LedgerSnapshot?,
    purse: Purse,
    /** 연결된 지갑들. 둘이면 아래에 개인·공용 토글이 뜬다. */
    purses: List<Purse>,
    purseLabels: Map<Purse, String>,
    payDay: Int,
    notice: String?,
    onSelectPurse: (Purse) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHistory: (Purse) -> Unit,
    onRecord: () -> Unit,
    /** 개인 예산이 없을 때 — 첫 시작 흐름(월급·고정비)으로 보낸다. */
    onSetBudget: () -> Unit,
    onEditBudget: (Purse) -> Unit,
    onEditPayday: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HomePalette.Ground),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                // 아래 토글·기록 버튼이 마지막 카드를 가리지 않게 넉넉히 비운다.
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 120.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppLogo()
                Spacer(Modifier.weight(1f))
                SettingsGear(onOpenSettings)
            }
            Spacer(Modifier.height(18.dp))

            if (snapshot == null) {
                if (purse == Purse.SHARED) {
                    EmptyCard(
                        title = tr("공용 예산을 아직 정하지 않았어요", "No shared budget yet", "Aún no hay presupuesto compartido"),
                        body = tr(
                            "정하면 배우자와 같이 쓰는 오늘 쓸 수 있는 돈이 여기에 보여요.",
                            "Once set, what you two can spend today shows up here.",
                            "Cuando lo fijes, aquí verás lo que podéis gastar hoy.",
                        ),
                        button = tr("공용 예산 정하기", "Set shared budget", "Fijar presupuesto compartido"),
                        onClick = { onEditBudget(Purse.SHARED) },
                    )
                } else {
                    EmptyCard(
                        title = tr("아직 한 달 예산을 정하지 않았어요", "No monthly budget yet", "Aún no tienes presupuesto mensual"),
                        body = tr(
                            "월급에서 고정비를 빼면 한 달 예산이 나와요. " +
                                "정하면 오늘 쓸 수 있는 돈이 여기에 보여요.",
                            "Your income minus fixed costs is what you can spend in a month. " +
                                "Once set, what you can spend today shows up here.",
                            "Tu sueldo menos los gastos fijos es lo que puedes gastar al mes. " +
                                "Cuando lo fijes, aquí verás lo que puedes gastar hoy.",
                        ),
                        button = tr("한 달 예산 정하기", "Set monthly budget", "Fijar presupuesto"),
                        onClick = onSetBudget,
                    )
                }
            } else {
                HeadLine(snapshot)
                Spacer(Modifier.height(16.dp))
                SourceCard(snapshot, onClick = { onOpenHistory(snapshot.purse) })
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SettingBox(
                        label = if (snapshot.purse == Purse.SHARED) tr("공용 예산", "Shared budget", "Presupuesto compartido")
                        else tr("한 달 예산", "Monthly budget", "Presupuesto mensual"),
                        value = StatusText.won(snapshot.monthlyBudget),
                        icon = R.drawable.ic_budget_wallet,
                        tilt = -10f,
                        onClick = { onEditBudget(snapshot.purse) },
                        modifier = Modifier.weight(1f),
                    )
                    SettingBox(
                        label = tr("월급날", "Payday", "Día de cobro"),
                        value = StatusText.payday(payDay),
                        icon = R.drawable.ic_payday_calendar,
                        tilt = 9f,
                        onClick = onEditPayday,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            if (notice != null) {
                Spacer(Modifier.height(10.dp))
                Text(text = notice, color = HomePalette.Muted, fontSize = 12.sp)
            }
        }

        if (purses.size > 1) {
            PurseToggle(
                purses = purses,
                selected = purse,
                labels = purseLabels,
                onSelect = onSelectPurse,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 26.dp),
            )
        }
        RecordButton(onRecord, Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 20.dp))
    }
}

/** 카드·칸에 두는 아주 옅은 그림자. 바탕이 회색이라 흰 카드가 떠 보이는 정도로만. */
private fun Modifier.softShadow(shape: Shape): Modifier = this.shadow(
    elevation = 3.dp,
    shape = shape,
    ambientColor = HomePalette.Ink.copy(alpha = 0.05f),
    spotColor = HomePalette.Ink.copy(alpha = 0.08f),
)

/** 머리 줄 왼쪽의 앱 아이콘 — 크림 바탕에 웃는 지갑. 어떤 앱인지 첫눈에 보이게. */
@Composable
private fun AppLogo() {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(HomePalette.Cream),
    ) {
        // 앞 레이어는 108 칸 중 가운데 72 칸에 그림이 있다 — 그만큼 키워서 가장자리를 잘라 낸다.
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = tr("하루치", "Haruchi", "Haruchi"),
            modifier = Modifier.requiredSize(54.dp),
        )
    }
}

/** 첫 줄 문장. 금액만 초록(넘겼으면 빨강)으로 칠한다. */
@Composable
private fun HeadLine(snapshot: LedgerSnapshot) {
    val line: StatusText.Headline = StatusText.headline(snapshot.available)
    Text(
        text = buildAnnotatedString {
            append(line.before)
            withStyle(SpanStyle(color = HomePalette.of(if (line.over) Tone.OVER else Tone.REMAINING))) { append(line.amount) }
            append(line.after)
        },
        color = HomePalette.Ink,
        fontSize = 25.sp,
        lineHeight = 34.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.4).sp,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

/** 기록하기. 연필 하나 — 이 앱에서 가장 자주 누르는 버튼이라 글자 없이 모양으로 알아보게 한다. */
@Composable
private fun RecordButton(onClick: () -> Unit, modifier: Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(58.dp)
            .shadow(elevation = 8.dp, shape = CircleShape, ambientColor = HomePalette.Accent, spotColor = HomePalette.Accent)
            .clip(CircleShape)
            .background(HomePalette.AccentBright)
            .clickable(onClick = onClick),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_pencil),
            contentDescription = tr("기록하기", "Log spending", "Anotar gasto"),
            tint = Color.White,
            modifier = Modifier.size(24.dp),
        )
    }
}

/**
 * 오늘 쓸 수 있는 돈이 어디서 왔는지 — 하루치 · 곳간 · 오늘 쓴 돈 세 줄과 월급날까지의 막대.
 * 카드 전체가 그 지갑의 내역으로 가는 문이다.
 */
@Composable
private fun SourceCard(snapshot: LedgerSnapshot, onClick: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .softShadow(shape)
            .clip(shape)
            .background(HomePalette.Card)
            .clickable(onClick = onClick)
            .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = tr("어디서 온 돈인가요", "Where it comes from", "De dónde sale"),
                color = HomePalette.Ink,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Text(text = tr("내역", "History", "Historial"), color = HomePalette.Muted, fontSize = 13.sp, maxLines = 1)
            Chevron(size = 16.dp)
        }
        Spacer(Modifier.height(6.dp))
        FactRow(IconHues.DAILY, tr("하루치", "Daily", "Diario"), snapshot.dailyRate, HomePalette.Ink)
        RowLine()
        FactRow(IconHues.GOTGAN, tr("곳간", "Savings", "Ahorro"), snapshot.vault, HomePalette.Accent)
        RowLine()
        FactRow(IconHues.SPENT, tr("오늘 쓴 돈", "Spent today", "Gastado hoy"), snapshot.todaySpent, HomePalette.Ink)

        Spacer(Modifier.height(14.dp))
        PeriodLine(snapshot)
    }
}

/**
 * 오늘 숫자를 정하는 값 하나(한 달 예산 · 월급날). 누르면 그 값을 고치는 창이 바로 뜬다.
 * 칸이 커서 그림도 크게 — 오른쪽 아래 모서리에 살짝 기울여 걸치고, 칸 밖은 잘린다.
 */
@Composable
private fun SettingBox(
    label: String,
    value: String,
    icon: Int,
    tilt: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier = modifier
            .height(112.dp)
            .softShadow(shape)
            .clip(shape)
            .background(HomePalette.Card)
            .clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 30.dp)) {
            Text(text = label, color = HomePalette.Ink2, fontSize = 12.5f.sp, maxLines = 1)
            Spacer(Modifier.height(2.dp))
            Text(text = value, color = HomePalette.Ink, fontSize = 16.5f.sp, fontWeight = FontWeight.Bold, maxLines = 1, style = Figures)
        }
        Icon(
            painter = painterResource(R.drawable.ic_pencil),
            contentDescription = tr("고치기", "Edit", "Editar"),
            tint = HomePalette.Muted,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 14.dp, end = 14.dp).size(14.dp),
        )
        // 직접 그린 색 있는 그림이라 tint 하지 않는다.
        Image(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 10.dp, y = 12.dp)
                .rotate(tilt)
                .size(64.dp),
        )
    }
}

/** 개인·공용 토글. 아래 가운데 떠 있다 — 한 번에 한 지갑만 보인다. */
@Composable
private fun PurseToggle(
    purses: List<Purse>,
    selected: Purse,
    labels: Map<Purse, String>,
    onSelect: (Purse) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pill = RoundedCornerShape(999.dp)
    Row(
        modifier = modifier
            .shadow(elevation = 6.dp, shape = pill, ambientColor = HomePalette.Ink.copy(alpha = 0.12f), spotColor = HomePalette.Ink.copy(alpha = 0.16f))
            .clip(pill)
            .background(ToggleTrack)
            .padding(4.dp),
    ) {
        for (p in purses) {
            val on: Boolean = p == selected
            Text(
                text = labels[p] ?: p.defaultLabel,
                color = if (on) Color.White else ToggleIdle,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier
                    .clip(pill)
                    .background(if (on) ToggleOn else Color.Transparent)
                    .clickable { onSelect(p) }
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
    }
}

private val ToggleTrack = Color(0xFFE5E8EB)
private val ToggleOn = Color(0xFF333D4B)
private val ToggleIdle = Color(0xFF4E5968)

/**
 * 세 줄 중 하나. 작은 색 점 + 이름 + 오른쪽 끝 금액. 아이콘 없이 점 색([hue])만으로 셋을 가른다 —
 * 하루치 초록 · 곳간 분홍 · 오늘 쓴 돈 주황. 아이콘+글자 줄이 이어지면 다른 화면 목록과 똑같아 보였다.
 */
@Composable
private fun FactRow(hue: Int, label: String, value: Long, valueColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 9.dp)) {
        Box(Modifier.padding(start = 2.dp).size(9.dp).clip(CircleShape).background(Color(hue)))
        Spacer(Modifier.width(13.dp))
        Text(text = label, color = HomePalette.Ink2, fontSize = 13.5f.sp, modifier = Modifier.weight(1f))
        Text(
            text = StatusText.won(value),
            color = valueColor,
            fontSize = 14.5f.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            style = Figures,
        )
    }
}

@Composable
private fun RowLine() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(HomePalette.Line))
}

/** 월급날까지 남은 돈과 날, 그리고 주기가 얼마나 지났는지. */
@Composable
private fun PeriodLine(snapshot: LedgerSnapshot) {
    val left: Long = snapshot.untilTarget
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = StatusText.periodLeft(snapshot),
            color = if (left >= 0L) HomePalette.Ink2 else HomePalette.Over,
            fontSize = 13.sp,
            style = Figures,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = StatusText.daysLeft(snapshot),
            color = HomePalette.Accent,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
    if (snapshot.cycleDays > 0) {
        Spacer(Modifier.height(7.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(HomePalette.Ground),
        ) {
            // 주기 첫날이면 폭이 0 이라 아무것도 안 그려진다 — 아직 지나간 날이 없다는 뜻 그대로다.
            Box(
                modifier = Modifier
                    .fillMaxWidth(snapshot.cycleElapsed)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(HomePalette.AccentBright),
            )
        }
    }
}

@Composable
private fun EmptyCard(title: String, body: String, button: String, onClick: () -> Unit) {
    CardBox {
        Sticker(R.drawable.ic_sticker_haruchi, size = 64.dp)
        Spacer(Modifier.height(10.dp))
        Text(text = title, color = HomePalette.Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(text = body, color = HomePalette.Ink2, fontSize = 13.sp, lineHeight = 20.sp)
        Spacer(Modifier.height(14.dp))
        Text(
            text = button,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(HomePalette.AccentBright)
                .clickable(onClick = onClick)
                .padding(horizontal = 18.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun CardBox(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .softShadow(RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
            .background(HomePalette.Card)
            .padding(20.dp),
    ) {
        content()
    }
}
