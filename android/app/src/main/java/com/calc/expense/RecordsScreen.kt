package com.calc.expense

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val DayFormat: DateTimeFormatter get() = L10n.monthDay()
private val ShortDayFormat: DateTimeFormatter get() = L10n.shortDay()

/**
 * 내 기록 화면. 예전 도감의 «내 기록» 칸을 한 화면으로 옮긴 것이다 — 통계 맨 아래 «내 기록» 칸에서
 * 들어온다. 최고 기록 다섯 줄과 쌓인 횟수 네 칸. 이 숫자들은 줄지 않는다.
 */
@Composable
fun RecordsScreen(ui: DogamUi, today: LocalDate, onClose: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HomePalette.Ground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                tr("내 기록", "My records", "Mis marcas"),
                color = HomePalette.Ink,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onClose) { Text(tr("닫기", "Close", "Cerrar"), color = HomePalette.Ink2) }
        }
        Spacer(Modifier.height(14.dp))
        RecordsCard(ui, today)
        Spacer(Modifier.height(10.dp))
        TalliesCard(ui.result.tallies)
        Spacer(Modifier.height(12.dp))
        Text(
            text = tr(
                "처음 쓴 첫 주는 금액 기록에서 빠져요. 곳간이 비어도 이 숫자들은 줄지 않아요.",
                "Your very first week doesn't count toward amount records. These numbers never go down, even if your savings run out.",
                "Tu primera semana no cuenta para los récords de importe. Estos números nunca bajan, aunque se acabe el ahorro.",
            ),
            color = HomePalette.Muted,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

@Composable
private fun CardColumn(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(HomePalette.Card)
            .padding(16.dp),
    ) { content() }
}

@Composable
private fun Tag(text: String) {
    Text(
        text = text,
        color = Color.White,
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(HomePalette.AccentBright)
            .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}

/** 나의 기록 다섯 줄. 깨질 수만 있고 줄지 않는다. */
@Composable
fun RecordsCard(ui: DogamUi, today: LocalDate) {
    val bests: PersonalBests = ui.result.bests
    CardColumn {
        val saved: Best? = bests.savedDay
        RecordRow(
            icon = R.drawable.dogam_ic_coin,
            title = tr("가장 많이 아낀 날", "Biggest saving day", "Día de mayor ahorro"),
            detail =
                if (saved != null) "${saved.from.format(DayFormat)} · " + tr("${StatusText.won(saved.spent)} 씀", "spent ${StatusText.won(saved.spent)}", "gastado ${StatusText.won(saved.spent)}")
                else tr("첫 주가 지나면 세기 시작해요", "Starts counting after your first week", "Empieza a contar tras tu primera semana"),
            value = saved?.let { L10n.wonPrefix + StatusText.figure(it.value) }, unit = L10n.wonSuffix,
            fresh = Dogam.isFresh(saved, today),
        )
        Divider()
        RecordRow(
            icon = R.drawable.dogam_ic_shield,
            title = tr("하루치 최장 연속", "Longest on-budget streak", "Racha más larga en presupuesto"),
            detail = bests.keepRun?.let { range(it) } ?: tr(
                "하루치 안에서 마친 날이 이어지면 늘어요",
                "Grows as you end days within the daily amount in a row",
                "Crece al terminar días seguidos dentro de la cantidad diaria",
            ),
            value = bests.keepRun?.value?.toString(), unit = DAYS_UNIT,
            fresh = Dogam.isFresh(bests.keepRun, today),
        )
        Divider()
        val quiet: Best? = bests.noSpendRun
        RecordRow(
            icon = R.drawable.dogam_ic_moon,
            title = tr("무지출 최장 연속", "Longest no-spend streak", "Racha más larga sin gastos"),
            detail = if (quiet != null) {
                val purse: Purse? = quiet.purse
                val where: String = if (purse != null) (ui.purseLabels[purse] ?: purse.defaultLabel) + " · " else ""
                where + range(quiet)
            } else {
                tr(
                    "지갑 하나라도 0원으로 마친 날부터 세요",
                    "Counts from a day when any wallet ends at ₩0",
                    "Cuenta desde un día en que alguna cartera termina en ₩0",
                )
            },
            value = quiet?.value?.toString(), unit = DAYS_UNIT,
            fresh = Dogam.isFresh(quiet, today),
        )
        Divider()
        RecordRow(
            icon = R.drawable.dogam_ic_week,
            title = tr("가장 적게 쓴 주", "Cheapest week", "Semana más barata"),
            detail = bests.cheapestWeek?.let { range(it) } ?: tr(
                "월요일부터 일요일까지 한 주가 지나면 나와요",
                "Shows up after a full Monday–Sunday week",
                "Aparece tras una semana completa de lunes a domingo",
            ),
            value = bests.cheapestWeek?.let { L10n.wonPrefix + StatusText.figure(it.value) }, unit = L10n.wonSuffix,
            fresh = Dogam.isFresh(bests.cheapestWeek, today),
        )
        Divider()
        RecordRow(
            icon = R.drawable.dogam_ic_pen,
            title = tr("기록 최장 연속", "Longest logging streak", "Racha más larga anotando"),
            detail = bests.recordRun?.let { range(it) } ?: tr(
                "하루도 빠짐없이 적으면 늘어요",
                "Grows as you log without missing a day",
                "Crece al anotar sin saltarte ningún día",
            ),
            value = bests.recordRun?.value?.toString(), unit = DAYS_UNIT,
            fresh = Dogam.isFresh(bests.recordRun, today),
        )
    }
}

/** 기록 숫자 뒤 «일». */
private val DAYS_UNIT: String get() = tr("일", " days", " días")

private fun range(best: Best): String =
    if (best.from == best.to) best.from.format(ShortDayFormat)
    else "${best.from.format(ShortDayFormat)} ~ ${best.to.format(ShortDayFormat)}"

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(HomePalette.Line))
}

@Composable
private fun RecordRow(
    @DrawableRes icon: Int,
    title: String,
    detail: String,
    value: String?,
    unit: String,
    fresh: Boolean,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 12.dp)) {
        // 통계·기록 창과 같은 흰 동그라미 + 옅은 그림자 + 직접 그린 여러 색 그림.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(38.dp).iconCircle(),
        ) {
            Image(painterResource(icon), contentDescription = null, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 제목이 길면(스페인어) 제목만 줄바꿈되고 «신기록» 표시는 자기 폭을 지킨다.
                Text(title, color = HomePalette.Ink, fontSize = 13.5f.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f, fill = false))
                if (fresh && value != null) {
                    Spacer(Modifier.width(6.dp))
                    Tag(tr("신기록", "New record", "Nuevo récord"))
                }
            }
            Text(detail, color = HomePalette.Muted, fontSize = 11.5f.sp, style = Figures)
        }
        Spacer(Modifier.width(8.dp))
        if (value == null) {
            Text("—", color = HomePalette.Muted, fontSize = 18.sp)
        } else {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, color = HomePalette.Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold, style = Figures)
                Text(unit, color = HomePalette.Ink, fontSize = 12.5f.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 1.dp, bottom = 2.dp))
            }
        }
    }
}

/** 더해지기만 하는 횟수 넷. 「다시 지킨 날」은 많이 쓴 다음 날을 칭찬하는 숫자다. */
@Composable
fun TalliesCard(tallies: Tallies) {
    CardColumn {
        Text(tr("지금까지 쌓인 횟수", "Counts so far", "Totales hasta ahora"), color = HomePalette.Ink2, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TallyBox(tallies.sDays, tr("S 등급", "S grades", "Notas S"), Modifier.weight(1f))
            TallyBox(tallies.noSpendDays, tr("무지출한 날", "No-spend days", "Días sin gastos"), Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TallyBox(tallies.keptDays, tr("하루치 지킨 날", "On-budget days", "Días en presupuesto"), Modifier.weight(1f))
            TallyBox(tallies.comebacks, tr("다시 지킨 날", "Comeback days", "Días de recuperación"), Modifier.weight(1f))
        }
    }
}

@Composable
private fun TallyBox(count: Int, label: String, modifier: Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(HomePalette.Chip)
            .padding(vertical = 11.dp),
    ) {
        Text(count.toString(), color = HomePalette.Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold, style = Figures)
        Text(label, color = HomePalette.Ink2, fontSize = 11.5f.sp)
    }
}
