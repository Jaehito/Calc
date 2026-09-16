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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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

private val DayFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("M월 d일", Locale.KOREA)

/**
 * 주기 리포트 화면.
 *
 * 화면의 중심은 **고정비 후보**다. «얼마 썼나»는 결산 팝업이 이미 말했고, 사용자가 답을 못
 * 내고 있는 질문은 그 다음 것이다 — «그래서 다음 달은 얼마로 잡아야 하나». 그 답을 막는 것이
 * 자기 고정비를 모르는 것이라, 쓴 돈 요약은 한 카드로 줄이고 후보 목록에 자리를 준다.
 *
 * **처음에 모두 골라져 있다.** 앱이 제안하고 사용자가 빼는 순서라야 «하나씩 고르기»라는 일이
 * 생기지 않는다. 틀린 것을 빼는 일은 열 줄이어도 몇 초지만, 맞는 것을 고르는 일은 그 열 줄을
 * 전부 읽게 만든다.
 *
 * @param selected 고른 후보의 이름 키([RecurringCosts.normalize])
 */
@Composable
fun CycleReportScreen(
    report: CycleReport?,
    selected: Set<String>,
    onToggle: (FixedCostCandidate) -> Unit,
    onApply: () -> Unit,
    onEditFixed: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HomePalette.Ground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text("지난 주기 리포트", color = HomePalette.Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                if (report != null) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = "${report.cycle.start.format(DayFormat)} ~ ${report.cycle.lastDay.format(DayFormat)}",
                        color = HomePalette.Muted,
                        fontSize = 12.sp,
                    )
                }
            }
            TextButton(onClick = onClose) { Text("닫기", color = HomePalette.Ink2) }
        }
        Spacer(Modifier.height(16.dp))

        if (report == null) {
            CardBox { Text("불러오는 중이에요", color = HomePalette.Ink2, fontSize = 14.sp) }
            return@Column
        }

        SpentCard(report)
        Spacer(Modifier.height(12.dp))
        CandidateCard(report, selected, onToggle, onApply, onEditFixed)

        if (report.categories.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            CategoryCard(report)
        }

        val error: String? = report.error
        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Text(error, color = HomePalette.Muted, fontSize = 12.sp)
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** 이 주기에 얼마 썼나. 예산이 있으면 남은 돈, 앞 주기가 있으면 그것과의 차이. */
@Composable
private fun SpentCard(report: CycleReport) {
    CardBox {
        Text("쓴 돈", color = HomePalette.Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = StatusText.won(report.spent),
                color = HomePalette.Ink,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                style = Figures,
                modifier = Modifier.weight(1f),
            )
            if (report.hasBudget) {
                Text(
                    text = "예산 " + StatusText.won(report.budget),
                    color = HomePalette.Muted,
                    fontSize = 12.sp,
                    style = Figures,
                )
            }
        }

        if (report.hasBudget) {
            Spacer(Modifier.height(8.dp))
            Text(
                text =
                    if (report.left >= 0L) StatusText.won(report.left) + " 남기고 끝냈어요"
                    else StatusText.won(-report.left) + " 넘겼어요",
                color = if (report.left >= 0L) HomePalette.Accent else HomePalette.Over,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                style = Figures,
            )
        }

        if (report.hasPrev) {
            Spacer(Modifier.height(6.dp))
            Text(
                text =
                    if (report.diff >= 0L) "앞 주기보다 " + StatusText.won(report.diff) + " 더"
                    else "앞 주기보다 " + StatusText.won(-report.diff) + " 덜",
                color = HomePalette.Ink2,
                fontSize = 12.5f.sp,
                style = Figures,
            )
        }
    }
}

/**
 * 고정비 후보. 이 화면의 본문이다.
 *
 * 맨 아래에 «이걸 빼면 얼마 남나»를 함께 보여준다 — 체크를 하나 뺄 때마다 숫자가 움직여야
 * 사용자가 «이 줄이 내 다음 달을 얼마나 바꾸나»를 느낄 수 있다. 그게 이 화면의 전부다.
 */
@Composable
private fun CandidateCard(
    report: CycleReport,
    selected: Set<String>,
    onToggle: (FixedCostCandidate) -> Unit,
    onApply: () -> Unit,
    onEditFixed: () -> Unit,
) {
    CardBox {
        Text("고정비로 보이는 것", color = HomePalette.Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            text = "기록과 결제 알림에서 달마다 되풀이된 것들이에요. 아닌 건 체크를 빼 주세요.",
            color = HomePalette.Ink2,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(14.dp))

        if (!report.hasCandidates) {
            Text(
                text = "아직 되풀이되는 걸 못 찾았어요. 한 주기를 더 지나면 찾을 수 있어요.",
                color = HomePalette.Muted,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(14.dp))
            TextButton(onClick = onEditFixed, modifier = Modifier.fillMaxWidth()) {
                Text("고정비 직접 적기", color = HomePalette.Accent, fontWeight = FontWeight.SemiBold)
            }
            return@CardBox
        }

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

        Spacer(Modifier.height(14.dp))
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(HomePalette.Line))
        Spacer(Modifier.height(14.dp))

        Row(verticalAlignment = Alignment.Bottom) {
            Text("고른 고정비", color = HomePalette.Ink2, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text(
                text = StatusText.won(CycleReports.candidateTotal(chosen)),
                color = HomePalette.Ink,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                style = Figures,
            )
        }

        val next: Long = report.recommendedWith(chosen)
        if (next > 0L) {
            Spacer(Modifier.height(12.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(HomePalette.Soft)
                    .padding(14.dp),
            ) {
                Text("이번 주기 챌린지 금액", color = HomePalette.Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(3.dp))
                Text(
                    text = StatusText.won(next),
                    color = HomePalette.Ink,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    style = Figures,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "월급 ${StatusText.figure(report.plan.monthlyIncome)} − 고정비 " +
                        StatusText.figure(report.planWith(chosen).fixedTotal),
                    color = HomePalette.Ink2,
                    fontSize = 11.sp,
                    style = Figures,
                )
            }
        } else if (report.plan.monthlyIncome <= 0L) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = "월급을 적어 두면 여기서 바로 다음 주기 금액까지 정할 수 있어요.",
                color = HomePalette.Muted,
                fontSize = 12.sp,
            )
        }

        Spacer(Modifier.height(16.dp))
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
            Text("고정비에 넣기", fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(4.dp))
        TextButton(onClick = onEditFixed, modifier = Modifier.fillMaxWidth()) {
            Text("직접 더하거나 고치기", color = HomePalette.Ink2)
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
                text = "최근 ${RecurringCosts.LOOK_BACK_CYCLES}주기 중 ${candidate.cycles}번" +
                    if (candidate.fromRecord) " · 기록" else " · 알림",
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

/** 어디에 썼나. 통계 탭의 도넛과 달리 막대 한 줄씩 — 리포트에서는 순위만 알면 된다. */
@Composable
private fun CategoryCard(report: CycleReport) {
    CardBox {
        Text("어디에 썼나", color = HomePalette.Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            report.categories.take(5).forEachIndexed { index, slice ->
                Column {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(HomePalette.categoryColor(index)),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = slice.name,
                            color = HomePalette.Ink,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = StatusText.won(slice.amount),
                            color = HomePalette.Ink2,
                            fontSize = 12.sp,
                            style = Figures,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(HomePalette.Line),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(slice.percent.coerceIn(1, 100) / 100f)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(HomePalette.categoryColor(index)),
                        )
                    }
                }
            }
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
