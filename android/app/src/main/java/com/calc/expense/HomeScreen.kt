package com.calc.expense

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 자릿수가 바뀌어도 폭이 흔들리지 않게 고정폭 숫자를 쓴다. */
private val Figures = TextStyle(fontFeatureSettings = "tnum")

private val DateFormat: DateTimeFormatter =
    DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREA)

/**
 * 홈 화면.
 *
 * 숫자는 하나만 크게 — **오늘 쓸 수 있는 돈**. 나머지는 전부 그 숫자의 근거로만 존재한다.
 * 남았으면 초록, 넘겼으면 빨강 — 글자를 읽기 전에 눈에 들어온다.
 *
 * 오늘 쓴 항목 목록은 일부러 넣지 않았다. 지금 앱은 날짜별 합계만 캐시하므로
 * 항목을 보여주려면 홈에 들어올 때마다 Notion 을 왕복해야 한다.
 */
@Composable
fun HomeScreen(
    today: LocalDate,
    snapshots: List<LedgerSnapshot>,
    notice: String?,
    onOpenSettings: () -> Unit,
    onOpenHistory: (Purse) -> Unit,
    onRecord: () -> Unit,
) {
    // 위(카드·내역)는 스크롤하고, 기록하기 버튼은 아래에 고정한다.
    // 곳간 카드가 둘이면 스크롤이 길어지는데, 버튼이 스크롤 안에 있으면 하단 탭에 가려진다.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HomePalette.Ground),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = today.format(DateFormat),
                    color = HomePalette.Ink2,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "설정",
                    color = HomePalette.Accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(HomePalette.Soft)
                        .clickable(onClick = onOpenSettings)
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                )
            }

            Spacer(Modifier.height(16.dp))

            if (snapshots.isEmpty()) {
                EmptyCard()
            } else {
                for (snapshot in snapshots) {
                    PurseCard(snapshot, showLabel = snapshots.size > 1, onClick = { onOpenHistory(snapshot.purse) })
                    Spacer(Modifier.height(12.dp))
                }
            }

            if (notice != null) {
                Spacer(Modifier.height(4.dp))
                Text(text = notice, color = HomePalette.Muted, fontSize = 12.sp)
            }
        }

        // 하단 고정 — 늘 보인다.
        Button(
            onClick = onRecord,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp)
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = HomePalette.AccentBright),
        ) {
            Text(text = "기록하기", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** 곳간 하나. 큰 숫자 하나와 그 숫자가 어떻게 나왔는지. 카드를 누르면 그 곳간 내역이 열린다. */
@Composable
private fun PurseCard(snapshot: LedgerSnapshot, showLabel: Boolean, onClick: () -> Unit) {
    val tone: Tone = if (snapshot.isOver) Tone.OVER else Tone.REMAINING
    val caption: String = when {
        snapshot.isOver && showLabel -> "${snapshot.label} · 오늘 초과"
        snapshot.isOver -> "오늘 초과"
        showLabel -> "${snapshot.label} · 오늘 쓸 수 있는 돈"
        else -> "오늘 쓸 수 있는 돈"
    }
    // 넘긴 날은 음수 대신 초과액으로 말한다. 마이너스 부호는 읽는 데 한 박자 더 걸린다.
    val amount: Long = if (snapshot.isOver) -snapshot.available else snapshot.available

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(HomePalette.Card)
            .clickable(onClick = onClick)
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = caption, color = HomePalette.Ink2, fontSize = 13.sp, modifier = Modifier.weight(1f))
            // 카드를 눌러 내역으로 갈 수 있다는 표시.
            Text(
                text = "내역 ›",
                color = HomePalette.Accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(HomePalette.Soft)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = StatusText.figure(amount),
                color = HomePalette.of(tone),
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                style = Figures,
            )
            Text(
                text = " 원",
                color = HomePalette.Ink2,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FactCell("하루치", StatusText.figure(snapshot.dailyRate), HomePalette.Ink, HomePalette.AccentBright, Modifier.weight(1f))
            FactCell("곳간", "+" + StatusText.figure(snapshot.vault), HomePalette.Accent, HomePalette.Gold, Modifier.weight(1f))
            FactCell("오늘 씀", "−" + StatusText.figure(snapshot.todaySpent), HomePalette.Ink, HomePalette.Over, Modifier.weight(1f))
        }

        Spacer(Modifier.height(16.dp))
        VaultBar(snapshot)

        Spacer(Modifier.height(16.dp))
        Text(
            text = StatusText.untilTarget(snapshot),
            color = HomePalette.Muted,
            fontSize = 12.sp,
            style = Figures,
        )

        // 지난 주기 이맘때와의 비교. 견줄 기록이 없으면 줄 자체가 없다.
        val comparison: String? = StatusText.comparison(snapshot)
        if (comparison != null) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 지난 주기보다 덜 썼으면 초록, 더 썼으면 빨강 — 과거의 나와 겨루는 신호.
                val diff: Long = snapshot.vsLastCycle ?: 0L
                val dot: Color = when {
                    diff < 0L -> HomePalette.Accent
                    diff > 0L -> HomePalette.Over
                    else -> HomePalette.Muted
                }
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(dot),
                )
                Spacer(Modifier.width(7.dp))
                Text(text = comparison, color = HomePalette.Ink2, fontSize = 12.sp, style = Figures)
            }
        }
    }
}

/**
 * 곳간에 얼마나 찼는지.
 *
 * 상한은 하루치의 [Budget.VAULT_CAP_DAYS] 배다 — 무한히 쌓여 예산이 무의미해지는 것과
 * 주기 끝에 "어차피 사라지니 쓰자"가 되는 것을 둘 다 막는 값이다.
 */
@Composable
private fun VaultBar(snapshot: LedgerSnapshot) {
    val cap: Long = snapshot.dailyRate * Budget.VAULT_CAP_DAYS
    val filled: Float =
        if (cap > 0L) (snapshot.vault.toFloat() / cap.toFloat()).coerceIn(0f, 1f) else 0f

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = "곳간", color = HomePalette.Ink2, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(
            text = "상한 " + StatusText.won(cap),
            color = HomePalette.Muted,
            fontSize = 12.sp,
            style = Figures,
        )
    }
    Spacer(Modifier.height(8.dp))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(HomePalette.Soft),
    ) {
        // 0 이면 폭이 0 이라 아무것도 안 그려진다 — 빈 곳간을 빈 막대로 보여주는 게 맞다.
        Box(
            modifier = Modifier
                .fillMaxWidth(filled)
                .fillMaxHeight()
                .clip(RoundedCornerShape(5.dp))
                .background(HomePalette.AccentBright),
        )
    }
}

@Composable
private fun EmptyCard() {
    CardBox {
        Text(
            text = "아직 연결된 곳간이 없습니다",
            color = HomePalette.Ink,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "설정에서 Notion 토큰과 DB를 넣고 월 예산을 정하면 " +
                "오늘 쓸 수 있는 돈이 여기에 나옵니다.",
            color = HomePalette.Ink2,
            fontSize = 13.sp,
        )
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

/** 큰 숫자의 근거 한 칸. 바탕색을 깔아 카드 안에서 한 덩어리로 읽히게 한다. */
@Composable
private fun FactCell(label: String, value: String, valueColor: Color, dot: Color, modifier: Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(HomePalette.Chip)
            .padding(horizontal = 11.dp, vertical = 11.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(dot),
            )
            Spacer(Modifier.width(6.dp))
            Text(text = label, color = HomePalette.Muted, fontSize = 11.sp)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            color = valueColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            style = Figures,
        )
    }
}
