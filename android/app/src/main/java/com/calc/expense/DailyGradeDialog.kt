package com.calc.expense

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * «어제 등급» 팝업. 앱을 열면 하루에 한 번 뜬다 ([DailyGradeStore] 가 봤는지 기억한다).
 *
 * **오늘이 아니라 어제인 이유**는 오늘이 아직 끝나지 않았기 때문이다. 오후 3시에 «오늘 D» 를
 * 내미는 건 하루가 어떻게 끝날지 모르는 채로 판결하는 것이라 억까가 된다.
 *
 * 그리고 [GradeDelivery] 가 B 이상만 통과시키므로 여기 도착하는 등급은 언제나 좋은 소식이다.
 * 나쁜 날은 조용히 넘어간다 — 숫자는 홈·통계에 그대로 있고, 채점만 삼가는 것이다.
 */
@Composable
fun DailyGradeDialog(grade: SpendingGrade.Graded, saved: Long, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HomePalette.Card,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(text = "어제 등급", color = HomePalette.Ink, fontWeight = FontWeight.Bold, fontSize = 18.sp)
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
                    text = "${StatusText.won(grade.spent)} / 하루치 ${StatusText.won(grade.budget)}",
                    color = HomePalette.Ink2,
                    fontSize = 14.sp,
                )
                // 아낀 돈이 어디로 갔는지 말한다 — 절약의 보상이 다음 날 아침에 온다는
                // 이 앱의 약속을 등급 자리에서 한 번 더 확인시켜 준다.
                if (saved > 0L) {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "${StatusText.won(saved)}이 곳간으로 갔어요",
                        color = HomePalette.Accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(HomePalette.Soft)
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("확인", color = HomePalette.Accent, fontWeight = FontWeight.SemiBold) }
        },
    )
}
