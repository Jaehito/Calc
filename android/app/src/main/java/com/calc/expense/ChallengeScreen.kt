package com.calc.expense

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 고정폭 숫자 — 순위·금액이 흔들리지 않게. */
private val Figures = TextStyle(fontFeatureSettings = "tnum")

/** 챌린지 탭이 그리는 상태 한 벌. 순위 계산은 [ChallengeStandings] 가 이미 끝냈다. */
data class ChallengeUi(
    val loading: Boolean = false,
    val joined: Boolean = false,
    val name: String = "",
    val code: String = "",
    val standings: List<Standing> = emptyList(),
    val myUid: String? = null,
    val weekLabel: String = "",
    val daysLeftText: String = "",
    val error: String? = null,
)

/**
 * 챌린지 탭. 부부·친구와 «예산 대비 덜 쓴 사람»으로 이번 주를 겨룬다.
 *
 * 방을 만들거나 코드로 참가하면 순위가 나온다. 뭘 샀는지는 오가지 않고 총액·사용률만 오간다.
 */
@Composable
fun ChallengeScreen(
    ui: ChallengeUi,
    savedName: String,
    onCreate: (roomName: String, myName: String) -> Unit,
    onJoin: (code: String, myName: String) -> Unit,
    onLeave: () -> Unit,
) {
    var showCreate: Boolean by remember { mutableStateOf(false) }
    var showJoin: Boolean by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HomePalette.Ground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
    ) {
        Text(text = "챌린지", color = HomePalette.Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        when {
            ui.joined -> JoinedView(ui, onLeave)
            ui.loading -> CardBox { Text("불러오는 중…", color = HomePalette.Muted, fontSize = 14.sp) }
            else -> EmptyView(onCreateClick = { showCreate = true }, onJoinClick = { showJoin = true })
        }

        if (ui.error != null) {
            Spacer(Modifier.height(12.dp))
            Text(text = ui.error, color = HomePalette.Over, fontSize = 13.sp)
        }
    }

    if (showCreate) {
        NameDialog(
            title = "방 만들기",
            firstLabel = "방 이름",
            firstHint = "우리집 절약 대결",
            firstIsCode = false,
            savedName = savedName,
            confirmLabel = "만들기",
            onConfirm = { first, myName -> showCreate = false; onCreate(first, myName) },
            onDismiss = { showCreate = false },
        )
    }
    if (showJoin) {
        NameDialog(
            title = "코드로 참가",
            firstLabel = "챌린지 코드",
            firstHint = "예: 7K2QP9",
            firstIsCode = true,
            savedName = savedName,
            confirmLabel = "참가",
            onConfirm = { first, myName -> showJoin = false; onJoin(first, myName) },
            onDismiss = { showJoin = false },
        )
    }
}

/** 참가 전 — 방 만들기 / 코드로 참가. */
@Composable
private fun EmptyView(onCreateClick: () -> Unit, onJoinClick: () -> Unit) {
    CardBox {
        Text(
            text = "아직 챌린지가 없어요",
            color = HomePalette.Ink,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "방을 만들어 코드를 공유하거나, 받은 코드로 참가하세요. " +
                "부부·친구와 이번 주 «예산 대비 덜 쓴 사람»을 겨뤄요.",
            color = HomePalette.Ink2,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = onCreateClick,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = HomePalette.AccentBright),
        ) {
            Text("방 만들기", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = onJoinClick,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = HomePalette.Chip,
                contentColor = HomePalette.Ink,
            ),
        ) {
            Text("코드로 참가", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** 참가한 상태 — 방 정보 + 이번 주 순위. */
@Composable
private fun JoinedView(ui: ChallengeUi, onLeave: () -> Unit) {
    CardBox {
        Text(text = ui.name.ifBlank { "챌린지" }, color = HomePalette.Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = ui.weekLabel, color = HomePalette.Ink2, fontSize = 13.sp, style = Figures, modifier = Modifier.weight(1f))
            if (ui.daysLeftText.isNotBlank()) {
                Text(
                    text = ui.daysLeftText,
                    color = HomePalette.Ink2,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(HomePalette.Chip)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }

    Spacer(Modifier.height(12.dp))

    CardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "순위", color = HomePalette.Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(text = "예산 대비 사용률", color = HomePalette.Muted, fontSize = 11.sp)
        }
        Spacer(Modifier.height(14.dp))

        if (ui.standings.isEmpty()) {
            Text(text = "아직 성적이 없어요. 이번 주 기록이 쌓이면 여기에 나와요.", color = HomePalette.Ink2, fontSize = 13.sp)
        } else {
            ui.standings.forEachIndexed { index, s ->
                StandingRow(s, index, isMe = s.uid == ui.myUid)
                if (index < ui.standings.lastIndex) Spacer(Modifier.height(14.dp))
            }
        }

        Spacer(Modifier.height(14.dp))
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(HomePalette.Line))
        Spacer(Modifier.height(12.dp))
        Text(
            text = "예산 대비 덜 쓴 사람이 앞서요. 일요일 밤 마감 후 새 주가 시작돼요.",
            color = HomePalette.Ink2,
            fontSize = 12.sp,
        )
    }

    Spacer(Modifier.height(12.dp))

    CardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "초대 코드", color = HomePalette.Muted, fontSize = 11.sp)
                Spacer(Modifier.height(3.dp))
                Text(text = ui.code, color = HomePalette.Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold, style = Figures)
            }
            Text(
                text = "나가기",
                color = HomePalette.Ink2,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(HomePalette.Chip)
                    .clickable(onClick = onLeave)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

/** 순위 한 줄 — 등수·이름·사용률·막대. */
@Composable
private fun StandingRow(s: Standing, index: Int, isMe: Boolean) {
    val fill: Float = (s.percent.toFloat() / 100f).coerceIn(0f, 1f)
    val barColor: Color = when {
        !s.hasBudget -> HomePalette.Muted
        s.percent > 100 -> HomePalette.Over
        s.isLeader -> HomePalette.AccentBright
        else -> HomePalette.Gold
    }
    val avatarColor: Color = HomePalette.categoryColor(index)
    val initial: String = s.name.trim().take(1).ifBlank { "?" }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (s.isLeader) HomePalette.Gold else HomePalette.Chip),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "${s.rank}",
                    color = if (s.isLeader) Color.White else HomePalette.Ink2,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    style = Figures,
                )
            }
            Spacer(Modifier.width(10.dp))
            Box(
                modifier = Modifier.size(30.dp).clip(RoundedCornerShape(999.dp)).background(avatarColor),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = initial, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = s.name, color = HomePalette.Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    if (isMe) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "나",
                            color = HomePalette.Accent,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(HomePalette.Soft)
                                .padding(horizontal = 7.dp, vertical = 2.dp),
                        )
                    }
                }
                Spacer(Modifier.height(1.dp))
                Text(
                    text = if (s.hasBudget) {
                        "예산 ${StatusText.figure(s.budget)} 중 ${StatusText.figure(s.spent)}"
                    } else {
                        "예산 미설정 · ${StatusText.figure(s.spent)} 씀"
                    },
                    color = HomePalette.Muted,
                    fontSize = 11.sp,
                    style = Figures,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (s.hasBudget) "${s.percent}%" else "—",
                color = if (s.isLeader) HomePalette.Accent else HomePalette.Ink,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                style = Figures,
            )
        }
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(HomePalette.Soft),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fill)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(5.dp))
                    .background(barColor),
            )
        }
    }
}

/**
 * 방 만들기·참가 공용 입력창. 위 칸(방 이름 또는 코드) + 내 이름.
 * [firstIsCode] 면 위 칸을 코드로 다루어 대문자로 올리고 6자리를 검사한다.
 */
@Composable
private fun NameDialog(
    title: String,
    firstLabel: String,
    firstHint: String,
    firstIsCode: Boolean,
    savedName: String,
    confirmLabel: String,
    onConfirm: (first: String, myName: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var first: String by remember { mutableStateOf("") }
    var myName: String by remember { mutableStateOf(savedName) }

    val firstOk: Boolean =
        if (firstIsCode) ChallengeCode.isValid(ChallengeCode.normalize(first)) else first.isNotBlank()
    val canConfirm: Boolean = firstOk && myName.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = first,
                    onValueChange = { first = if (firstIsCode) ChallengeCode.normalize(it) else it },
                    label = { Text(firstLabel) },
                    placeholder = { Text(firstHint) },
                    singleLine = true,
                    keyboardOptions = if (firstIsCode) {
                        KeyboardOptions(capitalization = KeyboardCapitalization.Characters)
                    } else {
                        KeyboardOptions.Default
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = myName,
                    onValueChange = { myName = it.take(ChallengeStore.MAX_NAME_LENGTH) },
                    label = { Text("내 이름") },
                    placeholder = { Text("순위에 표시돼요") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(first, myName) }, enabled = canConfirm) {
                Text(confirmLabel, color = if (canConfirm) HomePalette.Accent else HomePalette.Muted, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소", color = HomePalette.Ink2) }
        },
    )
}

@Composable
private fun CardBox(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(HomePalette.Card)
            .padding(20.dp),
    ) {
        content()
    }
}
