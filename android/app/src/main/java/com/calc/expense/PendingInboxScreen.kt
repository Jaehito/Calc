package com.calc.expense

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 수집함 팝업이 그리는 상태 한 벌. */
data class PendingInboxUi(
    val items: List<PendingPayment> = emptyList(),
    /** 연결된 곳간. 둘이면 항목마다 어디에 넣을지 고르게 한다. */
    val purses: List<Purse> = emptyList(),
    val purseLabels: Map<Purse, String> = emptyMap(),
    val categories: List<String> = emptyList(),
    /** 지금 기록 중인 항목 id. 그 줄의 버튼만 잠근다. */
    val busyId: String? = null,
    val message: String? = null,
    val messageIsError: Boolean = false,
)

private val TimeFormat: DateTimeFormatter =
    DateTimeFormatter.ofPattern("M월 d일 a h:mm", Locale.KOREA)

/**
 * 앱을 열었을 때 뜨는 «기록 안 한 결제» 수집함.
 *
 * 알림에서 읽은 값은 **짐작**이라 전부 고칠 수 있게 둔다 — 금액·이름·카테고리·곳간.
 * 자동으로 기록하지 않는 이유가 이것이다: 틀린 금액이 조용히 들어가면 숫자를 믿을 수 없게 된다.
 *
 * 각 줄에서 바로 «기록»하거나 «무시»할 수 있고, 원치 않는 출처는 발신자·앱 단위로 막는다.
 */
@Composable
fun PendingInboxDialog(
    ui: PendingInboxUi,
    onRecord: (item: PendingPayment, purse: Purse, name: String, amount: Long, category: String) -> Unit,
    onIgnore: (PendingPayment) -> Unit,
    onBlockSender: (PendingPayment) -> Unit,
    onBlockApp: (PendingPayment) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HomePalette.Card,
        shape = RoundedCornerShape(24.dp),
        title = {
            Column {
                Text(
                    text = "기록 안 한 결제 ${ui.items.size}건",
                    color = HomePalette.Ink,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "알림에서 읽은 값이라 맞는지 확인하고 기록하세요",
                    color = HomePalette.Muted,
                    fontSize = 12.sp,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                if (ui.message != null) {
                    Text(
                        text = ui.message,
                        color = if (ui.messageIsError) HomePalette.Over else HomePalette.Accent,
                        fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                }

                for (item in ui.items) {
                    PendingRow(
                        item = item,
                        ui = ui,
                        onRecord = onRecord,
                        onIgnore = onIgnore,
                        onBlockSender = onBlockSender,
                        onBlockApp = onBlockApp,
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("나중에", color = HomePalette.Ink2, fontWeight = FontWeight.SemiBold)
            }
        },
    )
}

/** 후보 한 건. 접힌 상태에서는 금액·이름만, «수정»을 누르면 고칠 칸이 펼쳐진다. */
@Composable
private fun PendingRow(
    item: PendingPayment,
    ui: PendingInboxUi,
    onRecord: (PendingPayment, Purse, String, Long, String) -> Unit,
    onIgnore: (PendingPayment) -> Unit,
    onBlockSender: (PendingPayment) -> Unit,
    onBlockApp: (PendingPayment) -> Unit,
) {
    var name: String by remember(item.id) { mutableStateOf(item.merchant) }
    var amountText: String by remember(item.id) { mutableStateOf(item.amount.toString()) }
    var category: String by remember(item.id) { mutableStateOf(item.category) }
    var purse: Purse by remember(item.id) { mutableStateOf(ui.purses.firstOrNull() ?: Purse.PERSONAL) }
    var editing: Boolean by remember(item.id) { mutableStateOf(false) }

    val amount: Long? = ExpenseParser.parseAmount(amountText)
    val canRecord: Boolean = name.isNotBlank() && amount != null && amount > 0L && ui.busyId == null
    val busy: Boolean = ui.busyId == item.id

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(HomePalette.Chip)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = StatusText.won(amount ?: item.amount),
                color = HomePalette.Ink,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = name.ifBlank { "이름 없음" },
                color = if (name.isBlank()) HomePalette.Muted else HomePalette.Ink2,
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 2.dp),
            )
        }

        Spacer(Modifier.height(4.dp))
        Text(
            text = sourceLine(item),
            color = HomePalette.Muted,
            fontSize = 11.sp,
        )

        if (editing) {
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("이름") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = mintFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it },
                label = { Text("금액") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(14.dp),
                colors = mintFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )

            if (ui.purses.size > 1) {
                Spacer(Modifier.height(12.dp))
                Text(text = "곳간", color = HomePalette.Muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Row {
                    for ((index, candidate) in ui.purses.withIndex()) {
                        Pill(
                            text = ui.purseLabels[candidate] ?: candidate.defaultLabel,
                            on = purse == candidate,
                            onClick = { purse = candidate },
                        )
                        if (index < ui.purses.lastIndex) Spacer(Modifier.width(7.dp))
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(text = "카테고리", color = HomePalette.Muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                for ((index, catName) in ui.categories.withIndex()) {
                    Pill(
                        text = catName,
                        on = category == catName,
                        onClick = { category = if (category == catName) "" else catName },
                    )
                    if (index < ui.categories.lastIndex) Spacer(Modifier.width(7.dp))
                }
            }

            Spacer(Modifier.height(12.dp))
            Row {
                TextLink("이 발신자 안 보기") { onBlockSender(item) }
                Spacer(Modifier.width(14.dp))
                TextLink("이 앱 안 보기") { onBlockApp(item) }
            }
        } else if (category.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Pill(text = category, on = true, onClick = { editing = true })
        }

        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = { onRecord(item, purse, name.trim(), amount ?: 0L, category) },
                enabled = canRecord,
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = HomePalette.AccentBright,
                    disabledContainerColor = HomePalette.Line,
                ),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp),
            ) {
                Text(
                    text = if (busy) "기록 중…" else "기록",
                    color = if (canRecord) Color.White else HomePalette.Muted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.width(10.dp))
            TextLink(if (editing) "접기" else "수정") { editing = !editing }
            Spacer(Modifier.width(14.dp))
            TextLink("무시") { onIgnore(item) }
        }
    }
}

/** 발신자·앱·시각 한 줄. 어디서 온 알림인지 알아야 «안 보기»를 판단할 수 있다. */
private fun sourceLine(item: PendingPayment): String {
    val time: String = try {
        Instant.ofEpochMilli(item.postedAt).atZone(ZoneId.systemDefault()).format(TimeFormat)
    } catch (_: Exception) {
        ""
    }
    val who: String = item.sender.ifBlank { item.packageName }
    return listOf(who, time).filter { it.isNotBlank() }.joinToString(" · ")
}

@Composable
private fun Pill(text: String, on: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        color = if (on) HomePalette.Accent else HomePalette.Ink2,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (on) HomePalette.Soft else HomePalette.Card)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

@Composable
private fun TextLink(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        color = HomePalette.Accent,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 4.dp),
    )
}
