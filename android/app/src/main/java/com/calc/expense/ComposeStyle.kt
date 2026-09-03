package com.calc.expense

import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable

/**
 * 입력칸 색. [HistoryScreen] 의 수정 다이얼로그와 [SettingsScreen] 이 함께 쓴다 — 포커스·커서를
 * 민트로 통일해 두 화면이 같은 화면군(홈·통계·챌린지·내역)과 같은 느낌이 나게 한다.
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
