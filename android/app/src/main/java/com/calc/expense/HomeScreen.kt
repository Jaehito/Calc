package com.calc.expense

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val DateFormat: DateTimeFormatter get() = L10n.fullDay()

/**
 * 홈 화면.
 *
 * 숫자는 하나만 크게 — **오늘 쓸 수 있는 돈**. 나머지는 전부 그 숫자의 근거로만 존재한다.
 * 남았으면 초록, 넘겼으면 빨강 — 글자를 읽기 전에 눈에 들어온다.
 *
 * 오늘 쓴 항목 목록은 일부러 넣지 않았다. 지금 앱은 날짜별 합계만 캐시하므로
 * 항목을 보여주려면 홈에 들어올 때마다 저장소를 왕복해야 한다.
 */
@Composable
fun HomeScreen(
    today: LocalDate,
    snapshots: List<LedgerSnapshot>,
    notice: String?,
    /** 이번 달 고정비 합계. 0 이면 그 카드를 그리지 않는다 — 안 적은 사람에게 빈 칸을 보이지 않는다. */
    fixedTotal: Long = 0L,
    onOpenSettings: () -> Unit,
    onOpenHistory: (Purse) -> Unit,
    onRecord: () -> Unit,
    onSetBudget: () -> Unit = {},
) {
    // 기록 버튼은 오른쪽 아래에 떠 있는 둥근 버튼이다. 예전의 가로 긴 버튼은 카드 두 장이
    // 뜨면 두 번째 카드를 가렸고, 화면의 주인공인 숫자보다 더 커 보였다.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HomePalette.Ground),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 96.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = today.format(DateFormat),
                    color = HomePalette.Ink2,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                SettingsGear(onOpenSettings)
            }

            Spacer(Modifier.height(16.dp))

            if (snapshots.isEmpty()) {
                EmptyCard(onSetBudget)
            } else {
                for (snapshot in snapshots) {
                    PurseCard(snapshot, onClick = { onOpenHistory(snapshot.purse) })
                    Spacer(Modifier.height(12.dp))
                }
                if (fixedTotal > 0L) {
                    FixedCostCard(fixedTotal, onSetBudget)
                    Spacer(Modifier.height(12.dp))
                }
            }

            if (notice != null) {
                Spacer(Modifier.height(4.dp))
                Text(text = notice, color = HomePalette.Muted, fontSize = 12.sp)
            }
        }

        RecordButton(onRecord, Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 20.dp))
    }
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
 * 지갑 하나. 개인과 공용이 **같은 모양**이다 — 위의 작은 이름표로만 가른다.
 *
 * 큰 숫자 아래에는 그 숫자가 어디서 왔는지 세 줄(하루치 · 곳간 · 오늘 쓴 돈)을 아이콘과 함께
 * 둔다. 예전에는 계산식 타일에 부호(+, −)와 뜻 없는 색 점이 붙어 «−0» 같은 글자가 나왔다.
 * 맨 아래는 월급날까지 남은 돈과 날, 그리고 주기가 얼마나 지났는지 보여 주는 막대다.
 */
@Composable
private fun PurseCard(snapshot: LedgerSnapshot, onClick: () -> Unit) {
    val over: Boolean = snapshot.isOver
    // 넘긴 날은 음수 대신 초과액으로 말한다. 마이너스 부호는 읽는 데 한 박자 더 걸린다.
    val amount: Long = if (over) -snapshot.available else snapshot.available

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 6.dp,
                shape = RoundedCornerShape(22.dp),
                ambientColor = HomePalette.Ink.copy(alpha = 0.10f),
                spotColor = HomePalette.Ink.copy(alpha = 0.10f),
            )
            .clip(RoundedCornerShape(22.dp))
            .background(HomePalette.Card)
            .clickable(onClick = onClick)
            .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = snapshot.label,
                color = HomePalette.Accent,
                fontSize = 12.5f.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(HomePalette.Soft)
                    .padding(horizontal = 10.dp, vertical = 3.dp),
            )
            Spacer(Modifier.weight(1f))
            // 카드 전체가 내역으로 가는 문이다. 여기는 그걸 알려 주는 표시일 뿐이다.
            Text(text = tr("내역", "History", "Historial"), color = HomePalette.Muted, fontSize = 13.sp, maxLines = 1)
            Chevron(size = 16.dp)
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = if (over) tr("오늘 초과", "Over today", "Exceso de hoy") else tr("오늘 쓸 수 있는 돈", "Left to spend today", "Disponible hoy"),
            color = HomePalette.Ink2,
            fontSize = 13.5f.sp,
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = L10n.wonPrefix + StatusText.figure(amount),
                color = HomePalette.of(if (over) Tone.OVER else Tone.REMAINING),
                fontSize = 38.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-1).sp,
                style = Figures,
            )
            if (L10n.wonSuffix.isNotEmpty()) {
                Text(
                    text = " " + L10n.wonSuffix,
                    color = HomePalette.Ink2,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        FactRow(R.drawable.ic_envelope, IconHues.DAILY, tr("하루치", "Daily", "Diario"), snapshot.dailyRate, HomePalette.Ink)
        RowLine()
        FactRow(R.drawable.ic_piggy, IconHues.GOTGAN, tr("곳간", "Savings", "Ahorro"), snapshot.vault, HomePalette.Accent)
        RowLine()
        FactRow(R.drawable.ic_bag, IconHues.SPENT, tr("오늘 쓴 돈", "Spent today", "Gastado hoy"), snapshot.todaySpent, HomePalette.Ink)

        Spacer(Modifier.height(14.dp))
        PeriodLine(snapshot)
    }
}

/**
 * 세 줄 중 하나. 자리 색 동그라미 안의 직접 그린 그림(용돈 봉투·돼지 저금통·쇼핑백) + 이름 + 오른쪽 끝 금액.
 * 그림 선은 [hue] 를 짙게 한 한 색으로 이미 칠해져 있어 tint 하지 않는다 — 바탕만 [IconHues.back].
 */
@Composable
private fun FactRow(icon: Int, hue: Int, label: String, value: Long, valueColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 7.dp)) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(34.dp).clip(CircleShape).background(Color(IconHues.back(hue)))) {
            Image(painterResource(icon), contentDescription = null, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.width(10.dp))
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
                .background(HomePalette.Soft),
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
private fun EmptyCard(onSetBudget: () -> Unit) {
    CardBox {
        Sticker(R.drawable.ic_sticker_haruchi, size = 64.dp)
        Spacer(Modifier.height(10.dp))
        Text(
            text = tr("아직 한 달 예산을 정하지 않았어요", "No monthly budget yet", "Aún no tienes presupuesto mensual"),
            color = HomePalette.Ink,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = tr(
                "월급에서 고정비를 빼면 한 달 예산이 나와요. " +
                    "정하면 오늘 쓸 수 있는 돈이 여기에 보여요.",
                "Your income minus fixed costs is what you can spend in a month. " +
                    "Once set, what you can spend today shows up here.",
                "Tu sueldo menos los gastos fijos es lo que puedes gastar al mes. " +
                    "Cuando lo fijes, aquí verás lo que puedes gastar hoy.",
            ),
            color = HomePalette.Ink2,
            fontSize = 13.sp,
            lineHeight = 20.sp,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = tr("한 달 예산 정하기", "Set monthly budget", "Fijar presupuesto"),
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(HomePalette.AccentBright)
                .clickable(onClick = onSetBudget)
                .padding(horizontal = 18.dp, vertical = 10.dp),
        )
    }
}

/**
 * 이번 달 고정비 합계. 큰 숫자가 아니라 **근거**로 존재한다 — 오늘 쓸 수 있는 돈이 왜 그
 * 금액인지 설명하는 줄이다. 그래서 곳간 카드보다 조용하게 그린다.
 */
@Composable
private fun FixedCostCard(fixedTotal: Long, onEdit: () -> Unit) {
    QuietBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = tr("이번 달 고정비", "Fixed costs this month", "Gastos fijos del mes"), color = HomePalette.Ink2, fontSize = 13.sp)
                Spacer(Modifier.height(2.dp))
                Text(
                    text = StatusText.won(fixedTotal),
                    color = HomePalette.Ink,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                text = tr("다시 계산", "Recalculate", "Recalcular"),
                color = HomePalette.Accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(HomePalette.Soft)
                    .clickable(onClick = onEdit)
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            )
        }
    }
}

/** 근거·보조 정보용 카드. 흰 바탕 대신 옅은 테두리만 둬서 곳간 카드보다 한 단계 낮게 보인다. */
@Composable
private fun QuietBox(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, HomePalette.Line, RoundedCornerShape(16.dp))
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        content()
    }
}

@Composable
private fun CardBox(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(HomePalette.Card)
            .padding(20.dp),
    ) {
        content()
    }
}
