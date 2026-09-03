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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Figures = TextStyle(fontFeatureSettings = "tnum")

private val DayFormat: DateTimeFormatter =
    DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREA)

/** 내역 화면이 그리는 상태 한 벌. 묶기·합계는 [ExpenseHistoryGrouping] 이 이미 끝냈다. */
data class HistoryUi(
    val title: String = "",
    /** 요약 줄에 쓰는 달 이름(예: 8월). */
    val monthName: String = "",
    val isThisMonth: Boolean = true,
    /** 공용 곳간이면 "함께 보는 목록" 안내를 띄운다. */
    val shared: Boolean = false,
    val loading: Boolean = false,
    val total: Long = 0L,
    val groups: List<DayGroup> = emptyList(),
    val error: String? = null,
)

/**
 * 곳간 하나의 지출 내역. 홈 카드를 눌러 들어온다.
 *
 * 노션(또는 나중에 다른 DB)에서 읽은 행을 날짜별로 보여준다 — 두 폰이 같은 공용 DB 를 보면
 * 같은 목록이 뜬다. 채점하지 않는다: 무엇을 얼마에 썼는지 사실만 늘어놓는다.
 *
 * 항목을 누르면 수정·삭제 다이얼로그가 뜬다([categories] 는 그 안의 카테고리 칩 목록).
 */
@Composable
fun HistoryScreen(
    ui: HistoryUi,
    categories: List<String>,
    onBack: () -> Unit,
    onToggleMonth: () -> Unit,
    onRefresh: () -> Unit,
    onEditRow: (row: ExpenseRow, name: String, amount: Long, category: String) -> Unit,
    onDeleteRow: (row: ExpenseRow) -> Unit,
) {
    var editingRow: ExpenseRow? by remember { mutableStateOf(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HomePalette.Ground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "←",
                color = HomePalette.Ink2,
                fontSize = 22.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .clickable(onClick = onBack)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(text = ui.title, color = HomePalette.Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(
                text = "새로고침",
                color = HomePalette.Accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(HomePalette.Soft)
                    .clickable(onClick = onRefresh)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }

        Spacer(Modifier.height(14.dp))

        // 요약 — 이 달 합계 + 달 토글
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(HomePalette.Card)
                .padding(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "${ui.monthName} 지출", color = HomePalette.Ink2, fontSize = 12.sp)
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(text = StatusText.figure(ui.total), color = HomePalette.Ink, fontSize = 30.sp, fontWeight = FontWeight.Bold, style = Figures)
                        Text(text = " 원", color = HomePalette.Ink2, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 4.dp))
                    }
                }
                Text(
                    text = if (ui.isThisMonth) "지난 달" else "이번 달",
                    color = HomePalette.Accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(HomePalette.Soft)
                        .clickable(onClick = onToggleMonth)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }

        if (ui.shared) {
            Spacer(Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(HomePalette.Chip)
                    .padding(horizontal = 12.dp, vertical = 9.dp),
            ) {
                Box(modifier = Modifier.width(7.dp).height(7.dp).clip(RoundedCornerShape(4.dp)).background(HomePalette.AccentBright))
                Spacer(Modifier.width(7.dp))
                Text(text = "공용 곳간을 함께 보는 목록이에요", color = HomePalette.Ink2, fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(6.dp))

        when {
            ui.loading -> Note("불러오는 중…")
            ui.error != null -> Note(ui.error, HomePalette.Over)
            ui.groups.isEmpty() -> Note("이 달에는 기록이 없어요.")
            else -> for (group in ui.groups) {
                DaySection(group, onRowClick = { row -> editingRow = row })
            }
        }

        Spacer(Modifier.height(24.dp))
    }

    val row: ExpenseRow? = editingRow
    if (row != null) {
        EditRowDialog(
            row = row,
            categories = categories,
            onSave = { name, amount, category ->
                onEditRow(row, name, amount, category)
                editingRow = null
            },
            onDelete = {
                onDeleteRow(row)
                editingRow = null
            },
            onDismiss = { editingRow = null },
        )
    }
}

@Composable
private fun DaySection(group: DayGroup, onRowClick: (ExpenseRow) -> Unit) {
    Spacer(Modifier.height(14.dp))
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)) {
        Text(text = group.date.format(DayFormat), color = HomePalette.Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text(text = StatusText.won(group.total), color = HomePalette.Ink2, fontSize = 12.sp, style = Figures)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(HomePalette.Card),
    ) {
        group.rows.forEachIndexed { index, row ->
            ExpenseItem(row, onClick = { onRowClick(row) })
            if (index < group.rows.lastIndex) {
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(HomePalette.Line))
            }
        }
    }
}

/** 지출 한 줄. 누르면 수정·삭제 다이얼로그가 뜬다. */
@Composable
private fun ExpenseItem(row: ExpenseRow, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 13.dp),
    ) {
        Text(text = row.name.ifBlank { "(이름 없음)" }, color = HomePalette.Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        if (row.category.isNotBlank()) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = row.category,
                color = HomePalette.Ink2,
                fontSize = 10.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(HomePalette.Chip)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        Text(text = StatusText.figure(row.amount), color = HomePalette.Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold, style = Figures)
    }
}

/**
 * 항목 하나 수정·삭제. «저장» 은 새 값이 있어야 눌린다(이름 필수·금액 0보다 커야).
 *
 * 삭제는 되묻지 않는다 — 빠른 입력 화면의 ✕ 와 같은 방식이라 배울 게 없다.
 */
@Composable
private fun EditRowDialog(
    row: ExpenseRow,
    categories: List<String>,
    onSave: (name: String, amount: Long, category: String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name: String by remember { mutableStateOf(row.name) }
    var amountText: String by remember { mutableStateOf(row.amount.toString()) }
    var category: String by remember { mutableStateOf(row.category) }

    val amount: Long? = ExpenseParser.parseAmount(amountText)
    val canSave: Boolean = name.isNotBlank() && amount != null && amount > 0L

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HomePalette.Card,
        shape = RoundedCornerShape(24.dp),
        title = { Text("내역 수정", color = HomePalette.Ink, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("이름") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = mintFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
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
                Spacer(Modifier.height(14.dp))
                Text(text = "카테고리", color = HomePalette.Muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(7.dp))
                Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    for ((index, catName) in categories.withIndex()) {
                        val on: Boolean = category == catName
                        Text(
                            text = catName,
                            color = if (on) HomePalette.Accent else HomePalette.Ink2,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(if (on) HomePalette.Soft else HomePalette.Chip)
                                .clickable { category = if (on) "" else catName }
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                        )
                        if (index < categories.lastIndex) Spacer(Modifier.width(7.dp))
                    }
                }
                Spacer(Modifier.height(18.dp))
                Text(
                    text = "삭제",
                    color = HomePalette.Over,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(HomePalette.Over.copy(alpha = 0.09f))
                        .clickable(onClick = onDelete)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name.trim(), amount ?: 0L, category) },
                enabled = canSave,
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = HomePalette.AccentBright,
                    disabledContainerColor = HomePalette.Chip,
                ),
            ) {
                Text("저장", color = if (canSave) Color.White else HomePalette.Muted, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소", color = HomePalette.Ink2) }
        },
    )
}

@Composable
private fun Note(text: String, color: Color = HomePalette.Ink2) {
    Spacer(Modifier.height(10.dp))
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(HomePalette.Card).padding(20.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = text, color = color, fontSize = 13.sp)
    }
}
