package com.calc.expense

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * «나무가 자랐어요!» 팝업. 나무 칸에서 자라는 연출이 끝난 뒤 한 번 뜬다.
 *
 * 나무 칸은 단계 이름을 말하지 않는다 — 다음에 무엇이 될지는 자라서 보는 재미다. 그래서 이름은 여기,
 * 막 그 모습이 된 순간에 한 번만 알려 준다. 다음 단계까지의 물은 말하지 않는다(나무 칸 막대에 있다).
 */
@Composable
fun TreeGrowDialog(stage: Int, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HomePalette.Card,
        shape = RoundedCornerShape(24.dp),
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(140.dp)
                        .clip(CircleShape)
                        .background(Brush.radialGradient(listOf(GlowCream, GlowEdge))),
                ) {
                    Image(
                        painter = painterResource(TREE_ART[stage.coerceIn(0, TREE_ART.lastIndex)]),
                        contentDescription = null,
                        modifier = Modifier.requiredSize(150.dp),
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    text = tr("나무가 자랐어요!", "Your tree grew!", "¡Tu árbol creció!"),
                    color = HomePalette.Ink,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(4.dp))
                Text(text = grownLine(stage), color = HomePalette.Ink2, fontSize = 14.sp, textAlign = TextAlign.Center)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(tr("좋아요", "Nice", "¡Bien!"), color = HomePalette.Accent, fontWeight = FontWeight.SemiBold)
            }
        },
    )
}

/** «묘목이 됐어요». 다 자랐으면 «꽃이 활짝 피었어요». */
private fun grownLine(stage: Int): String {
    if (stage >= TreeGrowth.LAST) return tr("꽃이 활짝 피었어요", "It's in full bloom", "Está en plena flor")
    val ko: String = STAGE_NAMES_KO[stage]
    return tr(
        "$ko${Plant.subjectParticle(ko)} 됐어요",
        "Now: " + STAGE_NAMES_EN[stage],
        "Ahora: " + STAGE_NAMES_ES[stage],
    )
}

private val STAGE_NAMES_KO = listOf("씨앗", "떡잎", "본잎", "묘목", "어린 나무", "나무", "꽃봉오리", "만개")
private val STAGE_NAMES_EN = listOf("seed", "sprout", "true leaves", "sapling", "young tree", "tree", "buds", "full bloom")
private val STAGE_NAMES_ES = listOf("semilla", "brote", "hojas", "plantón", "árbol joven", "árbol", "capullos", "plena flor")

private val GlowCream = Color(0xFFFFF6DA)
private val GlowEdge = Color(0xFFF3F7F2)
