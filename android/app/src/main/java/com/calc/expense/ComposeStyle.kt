package com.calc.expense

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

/**
 * 입력칸 색. [HistoryScreen] 의 수정 다이얼로그와 [SettingsScreen] 이 함께 쓴다 — 포커스·커서를
 * 민트로 통일해 두 화면이 같은 화면군(홈·통계·도감·내역)과 같은 느낌이 나게 한다.
 */
@Composable
fun mintFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = HomePalette.AccentBright,
    unfocusedBorderColor = HomePalette.Line,
    focusedLabelColor = HomePalette.Accent,
    unfocusedLabelColor = HomePalette.Muted,
    cursorColor = HomePalette.AccentBright,
    focusedTextColor = HomePalette.Ink,
    unfocusedTextColor = HomePalette.Ink,
)

/**
 * 설정으로 가는 톱니바퀴. 홈·통계·도감 머리 줄 오른쪽 같은 자리에 둔다 — 예전에는 홈에만
 * «설정» 글자가 있어, 통계·도감에서는 홈으로 돌아가야 설정에 갈 수 있었다.
 */
@Composable
fun SettingsGear(onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(HomePalette.Card)
            .clickable(onClick = onClick),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_settings),
            contentDescription = "설정",
            tint = HomePalette.Ink2,
            modifier = Modifier.size(20.dp),
        )
    }
}
