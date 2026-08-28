package com.calc.expense

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Figures = TextStyle(fontFeatureSettings = "tnum")

private val DayFormat: DateTimeFormatter =
    DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREA)

/** 내역 화면이 그리는 상태 한 벌. 묶기·합계는 [ExpenseHistoryGrouping] 이 이미 끝냈다. */
data class HistoryUi(
    val title: String = "",
    /** 요약 줄에 쓰는 달 이름(예: 8월). */
    val monthName: String = "",
    val isThisMonth: Boolean = true,
    /** 공용 곳간이면 "함께 보는 목록" 안내를 띄운다. */
    val shared: Boolean = false,
    val loading: Boolean = false,
    val total: Long = 0L,
    val groups: List<DayGroup> = emptyList(),
    val error: String? = null,
)

/**
 * 곳간 하나의 지출 내역. 홈 카드를 눌러 들어온다.
 *
 * 노션(또는 나중에 다른 DB)에서 읽은 행을 날짜별로 보여준다 — 두 폰이 같은 공용 DB 를 보면
 * 같은 목록이 뜬다. 채점하지 않는다: 무엇을 얼마에 썼는지 사실만 늘어놓는다.
 */
@Composable
fun HistoryScreen(
    ui: HistoryUi,
    onBack: () -> Unit,
    onToggleMonth: () -> Unit,
    onRefresh: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HomePalette.Ground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "←",
                color = HomePalette.Ink2,
                fontSize = 22.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .clickable(onClick = onBack)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(text = ui.title, color = HomePalette.Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(
                text = "새로고침",
                color = HomePalette.Accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(HomePalette.Soft)
                    .clickable(onClick = onRefresh)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }

        Spacer(Modifier.height(14.dp))

        // 요약 — 이 달 합계 + 달 토글
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(HomePalette.Card)
                .padding(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "${ui.monthName} 지출", color = HomePalette.Ink2, fontSize = 12.sp)
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(text = StatusText.figure(ui.total), color = HomePalette.Ink, fontSize = 30.sp, fontWeight = FontWeight.Bold, style = Figures)
                        Text(text = " 원", color = HomePalette.Ink2, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 4.dp))
                    }
                }
                Text(
                    text = if (ui.isThisMonth) "지난 달" else "이번 달",
                    color = HomePalette.Accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(HomePalette.Soft)
                        .clickable(onClick = onToggleMonth)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }

        if (ui.shared) {
            Spacer(Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(HomePalette.Chip)
                    .padding(horizontal = 12.dp, vertical = 9.dp),
            ) {
                Box(modifier = Modifier.width(7.dp).height(7.dp).clip(RoundedCornerShape(4.dp)).background(HomePalette.AccentBright))
                Spacer(Modifier.width(7.dp))
                Text(text = "공용 곳간을 함께 보는 목록이에요", color = HomePalette.Ink2, fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(6.dp))

        when {
            ui.loading -> Note("불러오는 중…")
            ui.error != null -> Note(ui.error, HomePalette.Over)
            ui.groups.isEmpty() -> Note("이 달에는 기록이 없어요.")
            else -> for (group in ui.groups) DaySection(group)
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun DaySection(group: DayGroup) {
    Spacer(Modifier.height(14.dp))
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)) {
        Text(text = group.date.format(DayFormat), color = HomePalette.Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text(text = StatusText.won(group.total), color = HomePalette.Ink2, fontSize = 12.sp, style = Figures)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(HomePalette.Card),
    ) {
        group.rows.forEachIndexed { index, row ->
            ExpenseItem(row)
            if (index < group.rows.lastIndex) {
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(HomePalette.Line))
            }
        }
    }
}

@Composable
private fun ExpenseItem(row: ExpenseRow) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 13.dp),
    ) {
        Text(text = row.name.ifBlank { "(이름 없음)" }, color = HomePalette.Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        if (row.category.isNotBlank()) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = row.category,
                color = HomePalette.Ink2,
                fontSize = 10.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(HomePalette.Chip)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        Text(text = StatusText.figure(row.amount), color = HomePalette.Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold, style = Figures)
    }
}

@Composable
private fun Note(text: String, color: androidx.compose.ui.graphics.Color = HomePalette.Ink2) {
    Spacer(Modifier.height(10.dp))
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(HomePalette.Card).padding(20.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = text, color = color, fontSize = 13.sp)
    }
}
