package com.calc.expense

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/** 통계 달력 한 달치. 칸은 [MonthCalendar] 가 이미 만들었다. */
data class StatsCalendarUi(
    val month: YearMonth,
    val cells: List<CalendarCell>,
    val total: Long,
    /** 다음 달로 넘길 수 있는가 — 이번 달이면 없다. */
    val canNext: Boolean,
)

/**
 * 통계 첫 카드의 달력 모습(머리줄 버튼으로 «최근 7일» 과 바꾼다).
 *
 * 날짜 아래에 그날 쓴 돈을 적고, 하루치를 넘긴 날만 빨강으로 쓴다. 오늘은 테두리. 날짜를 누르면
 * 그날 목록 화면이 열린다(7일 막대를 눌렀을 때와 같은 화면). 앞날은 눌리지 않는다.
 */
@Composable
fun CalendarCard(
    ui: StatsCalendarUi,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onOpenDay: (LocalDate) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(HomePalette.Card)
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = ui.month.format(L10n.yearMonth()),
                color = HomePalette.Ink,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            MonthStep("‹", enabled = true, onClick = onPrev)
            Spacer(Modifier.width(6.dp))
            MonthStep("›", enabled = ui.canNext, onClick = onNext)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = StatusText.won(ui.total),
            color = HomePalette.Ink,
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.5).sp,
            style = Figures,
        )
        Spacer(Modifier.height(14.dp))

        val firstDay: DayOfWeek = L10n.firstDayOfWeek
        Row(modifier = Modifier.fillMaxWidth()) {
            val sample: LocalDate = LocalDate.of(2026, 1, 4).with(TemporalAdjusters.nextOrSame(firstDay))
            for ((i, weekday) in MonthCalendar.weekdays(firstDay).withIndex()) {
                Text(
                    text = sample.plusDays(i.toLong()).format(L10n.weekday()),
                    color = if (weekday == DayOfWeek.SUNDAY) HomePalette.Over else HomePalette.Muted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        for (week in ui.cells.chunked(7)) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                for (cell in week) DayCell(cell, onOpenDay, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun DayCell(cell: CalendarCell, onOpenDay: (LocalDate) -> Unit, modifier: Modifier) {
    val day: LocalDate? = cell.day
    val shape: RoundedCornerShape = RoundedCornerShape(10.dp)
    var box: Modifier = modifier.height(44.dp).clip(shape)
    if (day != null && !cell.isFuture) box = box.clickable { onOpenDay(day) }
    if (cell.isToday) box = box.border(1.5.dp, HomePalette.AccentBright, shape)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = box.padding(top = 5.dp)) {
        if (day != null) {
            Text(
                text = day.dayOfMonth.toString(),
                color = if (cell.isFuture) HomePalette.Muted else HomePalette.Ink,
                fontSize = 12.5f.sp,
                fontWeight = if (cell.isFuture) FontWeight.Normal else FontWeight.Bold,
                style = Figures,
            )
            if (cell.spent > 0L) {
                Text(
                    text = StatusText.approxShort(cell.spent),
                    color = if (cell.over) HomePalette.Over else HomePalette.Ink2,
                    fontSize = 9.5f.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                    style = Figures,
                )
            }
        }
    }
}

/** 달 넘기기 «‹ ›». 이번 달에서는 «›» 가 흐려지고 눌리지 않는다. */
@Composable
private fun MonthStep(label: String, enabled: Boolean, onClick: () -> Unit) {
    var m: Modifier = Modifier.size(30.dp).clip(CircleShape).background(HomePalette.Chip)
    if (enabled) m = m.clickable(onClick = onClick)
    Box(contentAlignment = Alignment.Center, modifier = m) {
        Text(
            text = label,
            color = if (enabled) HomePalette.Ink2 else HomePalette.Line,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** 통계 머리줄의 보기 바꾸기 단추(톱니 왼쪽). 지금 보는 것의 반대 그림을 보인다. */
@Composable
fun StatsViewToggle(calendarMode: Boolean, onToggle: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(HomePalette.Card)
            .clickable(onClick = onToggle),
    ) {
        Icon(
            painter = painterResource(if (calendarMode) R.drawable.ic_view_bars else R.drawable.ic_view_calendar),
            contentDescription =
                if (calendarMode) tr("최근 7일로 보기", "Show last 7 days", "Ver últimos 7 días")
                else tr("달력으로 보기", "Show calendar", "Ver calendario"),
            tint = HomePalette.Ink2,
            modifier = Modifier.size(20.dp),
        )
    }
}
