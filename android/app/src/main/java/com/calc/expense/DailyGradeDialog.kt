package com.calc.expense

import androidx.compose.foundation.background
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

/**
 * «어제 등급» 팝업. 앱을 열면 하루에 한 번 뜬다 ([DailyGradeStore] 가 봤는지 기억한다).
 *
 * **오늘이 아니라 어제인 이유**는 오늘이 아직 끝나지 않았기 때문이다. 오후 3시에 «오늘 D» 를
 * 내미는 건 하루가 어떻게 끝날지 모르는 채로 판결하는 것이라 억까가 된다.
 *
 * 그리고 [GradeDelivery] 가 B 이상만 통과시키므로 여기 도착하는 등급은 언제나 좋은 소식이다.
 * 나쁜 날은 조용히 넘어간다 — 숫자는 홈·통계에 그대로 있고, 채점만 삼가는 것이다.
 *
 * 모양은 홈의 말투를 따른다 — 왼쪽 문장 하나(«하루치의 41%만 썼어요»)와 숫자 두 줄. 등급은 도감과 같은 잉크
 * 도장([InkStamp])으로 찍는다.
 *
 * 새로 받은 스탬프는 여기에 붙이지 않는다 — 이 팝업을 닫은 뒤 [StampPressDialog] 가 따로 찍어 준다.
 */
@Composable
fun DailyGradeDialog(
    grade: SpendingGrade.Graded,
    saved: Long,
    day: LocalDate,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HomePalette.Card,
        shape = RoundedCornerShape(24.dp),
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = tr("어제", "Yesterday", "Ayer") + " · " + day.format(L10n.monthDay()),
                    color = HomePalette.Muted,
                    fontSize = 12.5f.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = headline(grade),
                        color = HomePalette.Ink,
                        fontSize = 21.sp,
                        lineHeight = 28.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    InkStamp(listOf(grade.grade.name), gradeColor(grade.grade), size = 66.dp, tilt = -8f)
                }
                Spacer(Modifier.height(10.dp))
                Line(IconHues.SPENT, tr("쓴 돈", "Spent", "Gastado"), StatusText.won(grade.spent), HomePalette.Ink)
                // 아낀 돈이 어디로 갔는지 말한다 — 절약의 보상이 다음 날 아침에 온다는 약속을 등급 자리에서 한 번 더.
                if (saved > 0L) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(HomePalette.Line))
                    Line(IconHues.GOTGAN, tr("곳간으로", "To savings", "Al ahorro"), "+" + StatusText.won(saved), HomePalette.Accent)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(tr("확인", "OK", "Aceptar"), color = HomePalette.Accent, fontWeight = FontWeight.SemiBold) }
        },
    )
}

/** «하루치의 41%만 썼어요». 숫자만 초록. 한 푼도 안 썼으면 그 말로. */
private fun headline(grade: SpendingGrade.Graded): AnnotatedString {
    if (grade.spent <= 0L) {
        return AnnotatedString(tr("하루치를\n하나도 안 썼어요", "You didn't spend\nanything", "No gastaste\nnada"))
    }
    val pct: Long = if (grade.budget > 0L) grade.spent * 100L / grade.budget else 100L
    val figure = "$pct%"
    val only: Boolean = pct <= ONLY_UNDER_PERCENT
    val (before: String, after: String) = when (L10n.lang) {
        Lang.KO -> "하루치의\n" to (if (only) "만 썼어요" else " 썼어요")
        Lang.EN -> (if (only) "You used only\n" else "You used\n") to " of your daily"
        Lang.ES -> (if (only) "Usaste solo el\n" else "Usaste el\n") to " de tu diario"
    }
    return buildAnnotatedString {
        append(before)
        withStyle(SpanStyle(color = HomePalette.Accent)) { append(figure) }
        append(after)
    }
}

/** 이 비율 이하면 «만»을 붙인다 — 하루치를 거의 다 쓴 날에 «92%만»은 어색하다. */
private const val ONLY_UNDER_PERCENT = 80L

@Composable
private fun Line(hue: Int, label: String, value: String, valueColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 9.dp)) {
        Box(Modifier.padding(start = 2.dp).size(9.dp).clip(CircleShape).background(Color(hue)))
        Spacer(Modifier.width(12.dp))
        Text(text = label, color = HomePalette.Ink2, fontSize = 13.5f.sp, modifier = Modifier.weight(1f))
        Text(text = value, color = valueColor, fontSize = 14.5f.sp, fontWeight = FontWeight.Bold, maxLines = 1, style = Figures)
    }
}
