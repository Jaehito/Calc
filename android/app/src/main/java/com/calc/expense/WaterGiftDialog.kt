package com.calc.expense

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * «물을 받았어요» 팝업. 하루를 좋게 마친 다음 날 아침, «어제 등급» 팝업을 닫으면 뜬다.
 * 버튼 없이 저절로 준다 — 안 쓴 날을 사람이 알려 주지 않아도 된다([WaterRules]).
 */
@Composable
fun WaterGiftDialog(gift: WaterGift, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HomePalette.Card,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(text = tr("물을 받았어요", "You got water", "Recibiste agua"), color = HomePalette.Ink, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(painterResource(R.drawable.ic_water_drop), contentDescription = null, modifier = Modifier.size(36.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(text = "+${gift.total}", color = HomePalette.Water, fontSize = 42.sp, fontWeight = FontWeight.Bold, style = Figures)
                }
                Spacer(Modifier.height(8.dp))
                Text(text = reasons(gift), color = HomePalette.Ink2, fontSize = 14.sp, textAlign = TextAlign.Center)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(tr("확인", "OK", "Aceptar"), color = HomePalette.Accent, fontWeight = FontWeight.SemiBold) }
        },
    )
}

/** 왜 받았는지 한 줄. 하루만이면 문장으로, 여러 날이 모였으면 «안 쓴 날 +3 · A 등급 +2» 처럼. */
private fun reasons(gift: WaterGift): String {
    if (gift.goodDays + gift.quietDays == 1) {
        return if (gift.quietDays == 1) tr("하나도 안 쓴 날이 있었어요", "You had a no-spend day", "Tuviste un día sin gastos")
        else tr("A 등급 이상으로 하루를 마쳤어요", "You finished a day at grade A or better", "Terminaste un día con nota A o mejor")
    }
    val parts: List<String> = listOfNotNull(
        if (gift.quietDays > 0) part(tr("안 쓴 날", "No-spend", "Sin gastos"), gift.quietDays, WaterRules.QUIET_DAY) else null,
        if (gift.goodDays > 0) part(tr("A 등급", "Grade A", "Nota A"), gift.goodDays, WaterRules.GOOD_DAY) else null,
    )
    return parts.joinToString(" · ")
}

private fun part(label: String, days: Int, each: Int): String =
    if (days == 1) "$label +$each" else "$label ${tr("${days}일", "×$days", "×$days")} +${days * each}"
