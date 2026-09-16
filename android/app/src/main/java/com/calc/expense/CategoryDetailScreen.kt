package com.calc.expense

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Figures = TextStyle(fontFeatureSettings = "tnum")

/** 「없음」 칩의 이름. 저장되는 값은 빈 문자열이다 ([QuickInputActivity] 와 같은 규칙). */
private const val NONE = "없음"

/**
 * 카테고리 하나를 이름으로 펼친 화면.
 *
 * **이 화면이 이 앱의 «하위 카테고리»다.** 카테고리를 두 층으로 만드는 대신 이미 있는
 * 이름으로 파고든다 — 「식비 > 외식」보다 「식비 > 우아한형제들 312,000원」이 더 많은 것을
 * 말해 주고, 그 이름은 새로 만들 것도 없이 이미 모든 줄에 붙어 있다.
 *
 * 줄을 누르면 칩이 펼쳐지고, 칩을 누르면 **그 이름의 줄 전체**가 옮겨 간다. 한 줄씩 고치게
 * 하지 않는 이유는 분명하다 — 스무 번 간 편의점을 스무 번 고치게 하면 아무도 두 번은 안 한다.
 */
@Composable
fun CategoryDetailScreen(
    detail: CategoryDetail,
    chips: List<String>,
    busyName: String?,
    message: String?,
    onAssign: (group: NameGroup, category: String) -> Unit,
    onClose: () -> Unit,
) {
    var openName: String? by remember { mutableStateOf(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HomePalette.Ground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(detail.label, color = HomePalette.Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "${StatusText.won(detail.total)} · ${detail.count}건",
                    color = HomePalette.Muted,
                    fontSize = 12.sp,
                    style = Figures,
                )
            }
            TextButton(onClick = onClose) { Text("닫기", color = HomePalette.Ink2) }
        }
        Spacer(Modifier.height(16.dp))

        if (detail.loading) {
            CardBox { Text("불러오는 중이에요", color = HomePalette.Ink2, fontSize = 14.sp) }
            return@Column
        }

        val error: String? = detail.error
        if (error != null) {
            CardBox { Text(error, color = HomePalette.Over, fontSize = 14.sp) }
            return@Column
        }

        if (detail.isEmpty) {
            CardBox {
                Text(
                    text =
                        if (detail.category.isBlank()) "분류 안 된 지출이 없어요. 전부 제자리에 있습니다."
                        else "이 카테고리에 든 지출이 없어요.",
                    color = HomePalette.Ink2,
                    fontSize = 14.sp,
                )
            }
            return@Column
        }

        CardBox {
            Text(
                text = "이름을 누르면 그 이름의 지출이 한꺼번에 옮겨 가요.",
                color = HomePalette.Ink2,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "한 번 옮기면 다음에 같은 이름이 와도 다시 묻지 않아요.",
                color = HomePalette.Muted,
                fontSize = 11.5f.sp,
            )
            Spacer(Modifier.height(10.dp))

            for (group in detail.groups) {
                NameRow(
                    group = group,
                    chips = chips,
                    current = detail.category,
                    open = openName == group.name,
                    busy = busyName == group.name,
                    onOpen = { openName = if (openName == group.name) null else group.name },
                    onAssign = { chosen ->
                        openName = null
                        onAssign(group, chosen)
                    },
                )
            }
        }

        if (message != null) {
            Spacer(Modifier.height(12.dp))
            Text(message, color = HomePalette.Accent, fontSize = 12.5f.sp)
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** 이름 한 덩어리. 누르면 칩이 펼쳐진다. */
@Composable
private fun NameRow(
    group: NameGroup,
    chips: List<String>,
    current: String,
    open: Boolean,
    busy: Boolean,
    onOpen: () -> Unit,
    onAssign: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(enabled = !busy, onClick = onOpen)
                .padding(vertical = 9.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = group.name,
                    color = if (busy) HomePalette.Muted else HomePalette.Ink,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (busy) "옮기는 중" else "${group.count}건",
                    color = HomePalette.Muted,
                    fontSize = 11.sp,
                    style = Figures,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = StatusText.won(group.total),
                color = if (busy) HomePalette.Muted else HomePalette.Ink,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                style = Figures,
            )
            Spacer(Modifier.width(6.dp))
            Text(if (open) "⌃" else "⌄", color = HomePalette.Muted, fontSize = 13.sp)
        }

        if (open) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 10.dp),
            ) {
                // 「없음」은 맨 앞에 둔다 — 잘못 분류된 것을 되돌리는 문이고,
                // 되돌릴 길이 없으면 사용자가 칩을 누르기를 망설인다.
                Chip(label = NONE, selected = current.isBlank(), onClick = { onAssign("") })
                for (chip in chips) {
                    Chip(label = chip, selected = chip == current, onClick = { onAssign(chip) })
                }
            }
        }

        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(HomePalette.Line))
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        color = if (selected) HomePalette.Card else HomePalette.Ink2,
        fontSize = 12.5f.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) HomePalette.AccentBright else HomePalette.Chip)
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 7.dp),
    )
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
