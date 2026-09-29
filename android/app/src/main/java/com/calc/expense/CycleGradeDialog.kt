package com.calc.expense

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 등급마다 다른 색. 잘함(S)일수록 특별하게, 못함(D)일수록 경고색으로. */
fun gradeColor(grade: Grade): Color = when (grade) {
    Grade.S -> HomePalette.Gold
    Grade.A -> HomePalette.AccentBright
    Grade.B -> HomePalette.Accent
    Grade.C -> HomePalette.Ink2
    Grade.D -> HomePalette.Over
}

/**
 * «지난 주기 결산» 팝업. 월급날이 지나 새 주기로 넘어간 직후 딱 한 번만 뜬다
 * ([CycleGradeStore] 가 봤는지 기억한다).
 *
 * 새 주기의 추천 챌린지 금액도 **여기 한 줄로** 붙는다. 팝업을 따로 만들지 않는 이유는
 * 앱을 열 때 이미 셋(수집함·주기 결산·어제 등급)이 줄 서 있어서, 넷째를 더하면 앱 열자마자
 * 팝업 넘기기가 되기 때문이다. 결산을 보는 자리가 다음 달 금액을 정하기에도 맞는 자리다.
 *
 * 금액을 정하지 못하는 사람을 위한 문도 여기에 있다 — «고정비가 뭔지 모르겠어요»를 누르면
 * 리포트로 간다([CycleReportActivity]). 팝업은 주기가 바뀐 그 순간 한 번만 뜨는데, 자기
 * 고정비를 모르는 사람에게는 바로 그 순간이 «그래서 얼마로 하지»가 막히는 순간이다.
 *
 * @param recommended 월급 − 고정비로 계산한 금액. 0 이면 그 줄을 통째로 생략한다
 *   (월급을 안 적었거나 고정비가 월급을 넘은 경우)
 */
@Composable
fun CycleGradeDialog(
    grade: SpendingGrade.Graded,
    recommended: Long = 0L,
    fixedTotal: Long = 0L,
    monthlyIncome: Long = 0L,
    onApply: () -> Unit = {},
    onOpenReport: () -> Unit = {},
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HomePalette.Card,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(text = tr("지난 주기 결산", "Last cycle", "Ciclo anterior"), color = HomePalette.Ink, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = grade.grade.name,
                    color = gradeColor(grade.grade),
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "${StatusText.won(grade.spent)} / ${StatusText.won(grade.budget)}",
                    color = HomePalette.Ink2,
                    fontSize = 14.sp,
                )

                if (recommended > 0L) {
                    Spacer(Modifier.height(18.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(HomePalette.Soft)
                            .padding(14.dp),
                    ) {
                        Text(
                            text = tr("이번 주기 예산", "This cycle's budget", "Presupuesto de este ciclo"),
                            color = HomePalette.Accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = StatusText.won(recommended),
                            color = HomePalette.Ink,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = tr("월급", "Income", "Sueldo") + " ${StatusText.figure(monthlyIncome)} − " +
                                tr("고정비", "fixed costs", "gastos fijos") + " ${StatusText.figure(fixedTotal)}",
                            color = HomePalette.Ink2,
                            fontSize = 11.sp,
                        )
                    }
                }

                Spacer(Modifier.height(if (recommended > 0L) 12.dp else 18.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onOpenReport)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(
                        text =
                            if (recommended > 0L) tr("고정비 다시 찾아보기", "Review fixed costs", "Revisar gastos fijos")
                            else tr("고정비가 뭔지 모르겠어요", "What are fixed costs?", "¿Qué son los gastos fijos?"),
                        color = HomePalette.Accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Chevron(tint = HomePalette.Accent, size = 16.dp)
                }
            }
        },
        confirmButton = {
            if (recommended > 0L) {
                TextButton(onClick = onApply) {
                    Text(tr("적용", "Apply", "Aplicar"), color = HomePalette.Accent, fontWeight = FontWeight.Bold)
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text(tr("확인", "OK", "Aceptar"), color = HomePalette.Accent, fontWeight = FontWeight.SemiBold)
                }
            }
        },
        // 람다 안에서 가른다. 파라미터 자리에서 if 로 null 을 넘기면 @Composable 람다 타입 추론이
        // 컴파일러 버전에 따라 흔들린다 — 아무것도 안 그리면 그 자리에 아무것도 안 나온다.
        dismissButton = {
            if (recommended > 0L) {
                TextButton(onClick = onDismiss) { Text(tr("그대로 둘래요", "Keep as is", "Dejar así"), color = HomePalette.Ink2) }
            }
        },
    )
}
