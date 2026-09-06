package com.calc.expense

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
 */
@Composable
fun CycleGradeDialog(grade: SpendingGrade.Graded, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HomePalette.Card,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(text = "지난 주기 결산", color = HomePalette.Ink, fontWeight = FontWeight.Bold, fontSize = 18.sp)
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
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("확인", color = HomePalette.Accent, fontWeight = FontWeight.SemiBold) }
        },
    )
}
