package com.calc.expense

import android.content.Context
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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

private val TimeFormat: DateTimeFormatter get() = L10n.dayTime()

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
                    text = tr(
                        "기록 안 한 결제 ${ui.items.size}건",
                        "Unlogged payments: ${ui.items.size}",
                        "Pagos sin anotar: ${ui.items.size}",
                    ),
                    color = HomePalette.Ink,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = tr(
                        "알림에서 읽은 값이에요. 맞는지 보고 기록해 주세요",
                        "Read from notifications — check before logging",
                        "Leído de las notificaciones — revísalo antes de anotar",
                    ),
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
                Text(tr("나중에", "Later", "Más tarde"), color = HomePalette.Ink2, fontWeight = FontWeight.SemiBold)
            }
        },
    )
}

/**
 * 후보 한 건. 금액·이름·곳간·카테고리가 **접힌 채로 다 보이고 그 자리에서 바뀐다.**
 * «이름·금액»을 눌렀을 때만 글자를 고치는 칸과 «안 보기»가 펼쳐진다.
 */
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
    /** 칩을 직접 눌렀으면 이름을 고쳐도 카테고리를 다시 짐작하지 않는다(기록 창과 같다). */
    var categoryPicked: Boolean by remember(item.id) { mutableStateOf(false) }
    val context: Context = LocalContext.current
    var purse: Purse by remember(item.id) { mutableStateOf(ui.purses.firstOrNull() ?: Purse.PERSONAL) }
    /** 「⋯」 을 눌렀을 때만 나오는 것들 — 차단. 이름·금액은 이제 늘 펼쳐져 있다. */
    var moreOpen: Boolean by remember(item.id) { mutableStateOf(false) }

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
        Text(
            text = sourceLine(item),
            color = HomePalette.Muted,
            fontSize = 11.sp,
        )

        // **이름·금액 칸이 처음부터 열려 있다.** 예전에는 글자로만 보이고 「이름·금액」을
        // 눌러야 칸이 나왔는데, 알림에서 읽은 이름은 자주 틀려서 그 한 번이 거의 매번
        // 필요했다. 기록 화면에서 바로 치는 것과 같은 손놀림이 되도록 펼쳐 둔다.
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = name,
            onValueChange = {
                name = it
                // 이름을 고치면 카테고리도 다시 짐작한다 — 기억이 낱말 규칙을 이긴다
                // ([QuickInputActivity.applyAutoCategory] 와 같은 순서).
                if (!categoryPicked) {
                    category = CategoryMemoryStore.recall(context, it, ui.categories)
                        ?: CategoryClassifier.classify(it, ui.categories).orEmpty()
                }
            },
            label = { Text(tr("이름", "Name", "Nombre")) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = mintFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = amountText,
            onValueChange = { amountText = it },
            label = { Text(tr("금액", "Amount", "Importe")) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = RoundedCornerShape(14.dp),
            colors = mintFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )

        // 곳간·카테고리는 **접힌 채로도 보이고 바로 눌린다.** 예전에는 «수정»을 눌러야
        // 나왔는데, 확인하려고 펼치고 다시 접는 동작이 건마다 반복됐다. 수집함은 훑고
        // 넘기는 화면이라 한 번에 보여야 한다.
        if (ui.purses.size > 1) {
            Spacer(Modifier.height(10.dp))
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

        if (ui.categories.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                for ((index, catName) in ui.categories.withIndex()) {
                    Pill(
                        text = L10n.name(catName),
                        on = category == catName,
                        onClick = {
                            categoryPicked = true
                            category = if (category == catName) "" else catName
                        },
                    )
                    if (index < ui.categories.lastIndex) Spacer(Modifier.width(7.dp))
                }
            }
        }

        // 「⋯」 뒤에 남는 것은 차단뿐이다. 한 번 누르면 그 출처가 통째로 사라지는 동작이라
        // 기록·무시와 나란히 두면 잘못 누르기 쉽다.
        if (moreOpen) {
            Spacer(Modifier.height(12.dp))
            Row {
                TextLink(tr("이 발신자 안 보기", "Hide this sender", "Ocultar remitente")) { onBlockSender(item) }
                Spacer(Modifier.width(14.dp))
                TextLink(tr("이 앱 안 보기", "Hide this app", "Ocultar esta app")) { onBlockApp(item) }
            }
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
                    text = if (busy) tr("기록 중…", "Logging…", "Anotando…") else tr("기록", "Log", "Anotar"),
                    color = if (canRecord) Color.White else HomePalette.Muted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.width(10.dp))
            TextLink(tr("무시", "Ignore", "Ignorar")) { onIgnore(item) }
            Spacer(Modifier.width(14.dp))
            TextLink(if (moreOpen) tr("접기", "Less", "Menos") else "⋯") { moreOpen = !moreOpen }
        }
    }
}

/**
 * 출처·시각 한 줄. 어디서 온 알림인지 알아야 «안 보기»를 판단할 수 있다.
 *
 * 발신자가 «1577-8000» 같은 번호면 그 번호로는 아무것도 알 수 없으므로, 내용에서 읽은
 * 은행·카드사 이름을 대신 보인다 ([PendingPayment.sourceName]).
 */
private fun sourceLine(item: PendingPayment): String {
    val time: String = try {
        Instant.ofEpochMilli(item.postedAt).atZone(ZoneId.systemDefault()).format(TimeFormat)
    } catch (_: Exception) {
        ""
    }
    return listOf(item.sourceName, time).filter { it.isNotBlank() }.joinToString(" · ")
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
