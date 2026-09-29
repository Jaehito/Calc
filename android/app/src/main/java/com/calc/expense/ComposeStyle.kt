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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
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
            contentDescription = tr("설정", "Settings", "Ajustes"),
            tint = HomePalette.Ink2,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * 화면 머리의 뒤로가기. 예전에는 «←» 글자였다 — 글꼴마다 굵기·높이가 달라 화면마다 어긋났다.
 */
@Composable
fun BackButton(onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_back),
            contentDescription = tr("뒤로", "Back", "Atrás"),
            tint = HomePalette.Ink2,
            modifier = Modifier.size(22.dp),
        )
    }
}

/** 눌러서 들어가는 줄의 오른쪽 꺾쇠. 예전의 «›» 글자를 대신한다. */
@Composable
fun Chevron(tint: Color = HomePalette.Muted, size: Dp = 20.dp) {
    Icon(
        painter = painterResource(R.drawable.ic_chevron_right),
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(size),
    )
}

/**
 * 금액 숫자 글꼴. Pretendard 에서 숫자·금액 기호만 잘라 이름을 바꾼 것이다
 * (`android/third_party/figures-font`). 한글은 이 글꼴에 없어 시스템 글꼴로 넘어간다 —
 * 같은 줄에서도 숫자만 이 글꼴로 보인다.
 */
val FiguresFont: FontFamily = FontFamily(
    Font(R.font.figures_regular, FontWeight.Normal),
    Font(R.font.figures_medium, FontWeight.Medium),
    Font(R.font.figures_semibold, FontWeight.SemiBold),
    Font(R.font.figures_bold, FontWeight.Bold),
)

/** 금액 글자 모양. 자릿수가 바뀌어도 폭이 흔들리지 않게 고정폭 숫자(tnum)를 쓴다. */
val Figures: TextStyle = TextStyle(fontFamily = FiguresFont, fontFeatureSettings = "tnum")
