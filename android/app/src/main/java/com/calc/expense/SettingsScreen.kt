package com.calc.expense

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** 설정 폼의 입력칸 값. 저장·검증 로직은 Activity 쪽(순수 상태가 아니라서)에 남는다. */
data class SettingsFormUi(
    val categoriesText: String = "",
    val payDayText: String = "",
    val personalName: String = "",
    val personalBudgetText: String = "",
    val sharedName: String = "",
    val sharedBudgetText: String = "",
)

/** 설정 화면이 그리는 상태 한 벌. */
data class SettingsUi(
    val form: SettingsFormUi = SettingsFormUi(),
    /** 화면 아래 잠깐 뜨는 안내. 같은 문구를 다시 띄울 수 있게 [toastId] 로 구분한다. */
    val toast: String? = null,
    val toastIsError: Boolean = false,
    val toastId: Int = 0,
    /** 저장할 때마다 오른다. 오르면 «저장됨»을 잠깐 띄운다. */
    val savedCount: Int = 0,
    val showStorageNotice: Boolean = false,
    val notificationOn: Boolean = false,
    val reminderOn: Boolean = false,
    val accountEmail: String? = null,
    val householdPaired: Boolean = false,
    val householdCode: String? = null,
    val householdJoinInput: String = "",
    val householdBusy: Boolean = false,
    val householdMessage: String? = null,
    val householdMessageIsError: Boolean = false,
    /** 지금 저장된 고정비 합계. 0 이면 아직 안 적은 것이다. */
    val fixedTotal: Long = 0L,
    /** 수집함에 담지 않는 발신자·앱. 해제할 수 있게 화면에 그대로 보여준다. */
    val blockedSenders: List<String> = emptyList(),
    val blockedPackages: List<String> = emptyList(),
)

/**
 * 설정 화면. 홈·통계·도감·내역과 같은 민트 카드 화면군으로 맞춘다.
 *
 * 네 묶음(예산 · 알림 · 가정 · 기록)에 자주 안 여는 것은 «더보기» 안에 접는다. **저장 버튼이
 * 없다** — 칸에서 벗어나거나 화면을 나갈 때 Activity 가 저장한다([onFieldDone]). 치는 도중의
 * «9» 같은 중간 값이 예산이 되지 않게, 글자마다가 아니라 칸을 떠날 때 저장한다.
 *
 * 폼은 한 데이터클래스([SettingsFormUi])로 오르내린다. 부수효과는 전부 Activity 쪽 콜백이다
 * (SharedPreferences·Firebase 는 Compose 상태가 아니다).
 */
@Composable
fun SettingsScreen(
    ui: SettingsUi,
    onBack: () -> Unit,
    onFormChange: (SettingsFormUi) -> Unit,
    onFieldDone: () -> Unit,
    onToggleNotification: (Boolean) -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenInput: () -> Unit,
    onToggleReminder: () -> Unit,
    onExport: () -> Unit,
    onExportExpenses: () -> Unit,
    onImport: () -> Unit,
    onSignOut: () -> Unit,
    onHouseholdJoinInputChange: (String) -> Unit,
    onCreateHousehold: () -> Unit,
    onJoinHousehold: () -> Unit,
    onLeaveHousehold: () -> Unit,
    onShareHouseholdCode: () -> Unit,
    onOpenFixedCosts: () -> Unit,
    onUnblockSender: (String) -> Unit,
    onUnblockPackage: (String) -> Unit,
    onToastShown: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().background(HomePalette.Ground)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
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
                Text(text = "설정", color = HomePalette.Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }

            BudgetGroup(ui, onFormChange, onFieldDone, onOpenFixedCosts)
            NotificationGroup(ui, onToggleNotification, onToggleReminder, onOpenInput, onOpenNotificationSettings)
            HouseholdGroup(
                ui, onHouseholdJoinInputChange, onCreateHousehold, onJoinHousehold,
                onLeaveHousehold, onShareHouseholdCode,
            )
            RecordGroup(ui.form, onFormChange, onFieldDone)
            MoreGroup(
                ui, onExportExpenses, onExport, onImport, onSignOut, onUnblockSender, onUnblockPackage,
            )

            if (ui.showStorageNotice) {
                Spacer(Modifier.height(12.dp))
                WarningBanner("이 기기에서 암호화 저장소를 열지 못해 설정이 평문으로 저장됩니다. 앱 전용 영역이라 다른 앱은 읽지 못합니다.")
            }

            // 아래 안내가 마지막 카드를 가리지 않게 여백을 둔다.
            Spacer(Modifier.height(72.dp))
        }

        Toast(ui, onToastShown, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

// ── 예산 ──────────────────────────────────────────────────────────────────────

@Composable
private fun BudgetGroup(
    ui: SettingsUi,
    onFormChange: (SettingsFormUi) -> Unit,
    onFieldDone: () -> Unit,
    onOpenFixedCosts: () -> Unit,
) {
    val form: SettingsFormUi = ui.form
    GroupTitle("예산") { SavedMark(ui.savedCount) }
    CardBox {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MintField(
                value = form.personalName,
                onValueChange = { onFormChange(form.copy(personalName = it.take(Purse.MAX_NAME_LENGTH))) },
                label = "개인 곳간 이름",
                onDone = onFieldDone,
                modifier = Modifier.weight(1f),
            )
            MintField(
                value = form.personalBudgetText,
                onValueChange = { onFormChange(form.copy(personalBudgetText = it)) },
                label = "월 예산",
                keyboardType = KeyboardType.Number,
                onDone = onFieldDone,
                modifier = Modifier.weight(1.2f),
            )
        }
        HelperText("예산을 주기 일수로 나눈 값이 하루치이고, 아낀 만큼 곳간에 쌓입니다. «93만»처럼 적어도 됩니다.")
        Divider()
        LinkRow(
            title = if (ui.fixedTotal > 0L) "고정비 고치고 다시 계산" else "고정비로 계산하기",
            sub = if (ui.fixedTotal > 0L) {
                "지금 적어 둔 고정비 ${StatusText.won(ui.fixedTotal)}"
            } else {
                "월급에서 월세·보험 같은 고정비를 빼 개인 예산을 정해요"
            },
            onClick = onOpenFixedCosts,
        )

        if (ui.householdPaired) {
            Divider()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MintField(
                    value = form.sharedName,
                    onValueChange = { onFormChange(form.copy(sharedName = it.take(Purse.MAX_NAME_LENGTH))) },
                    label = "공용 곳간 이름",
                    onDone = onFieldDone,
                    modifier = Modifier.weight(1f),
                )
                MintField(
                    value = form.sharedBudgetText,
                    onValueChange = { onFormChange(form.copy(sharedBudgetText = it)) },
                    label = "공용 월 예산",
                    keyboardType = KeyboardType.Number,
                    onDone = onFieldDone,
                    modifier = Modifier.weight(1.2f),
                )
            }
            HelperText("공용 곳간 이름·예산과 월급날은 두 폰이 같이 씁니다. 여기서 바꾸면 배우자 폰도 앱을 열 때 따라 바뀝니다.")
        }

        Divider()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "월급날", color = HomePalette.Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    text = "이날부터 다음 월급 전날까지가 한 주기예요. 달력대로 쓰려면 1",
                    color = HomePalette.Muted,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                )
            }
            Spacer(Modifier.width(10.dp))
            MintField(
                value = form.payDayText,
                onValueChange = { onFormChange(form.copy(payDayText = it.filter(Char::isDigit).take(2))) },
                label = "일",
                keyboardType = KeyboardType.Number,
                onDone = onFieldDone,
                modifier = Modifier.width(76.dp),
            )
        }
    }
}

/** 저장할 때마다 잠깐 떴다 사라지는 «저장됨». 처음 그릴 때(0)는 띄우지 않는다. */
@Composable
private fun SavedMark(savedCount: Int) {
    var visible: Boolean by remember { mutableStateOf(false) }
    LaunchedEffect(savedCount) {
        if (savedCount == 0) return@LaunchedEffect
        visible = true
        delay(1800)
        visible = false
    }
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
        Text(text = "저장됨 ✓", color = HomePalette.Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

// ── 알림 ──────────────────────────────────────────────────────────────────────

@Composable
private fun NotificationGroup(
    ui: SettingsUi,
    onToggleNotification: (Boolean) -> Unit,
    onToggleReminder: () -> Unit,
    onOpenInput: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
) {
    GroupTitle("알림")
    CardBox {
        SwitchRow(
            title = "잠금화면 알림",
            sub = "잠금화면에 오늘 쓸 수 있는 돈을 띄우고, 눌러서 바로 적어요",
            checked = ui.notificationOn,
            onCheckedChange = onToggleNotification,
        )
        Divider()
        SwitchRow(
            title = "결제 알림 읽기",
            sub = "카드·은행 알림을 보면 금액과 가게를 잠깐 띄워요. «알림 접근» 권한이 필요해요",
            checked = ui.reminderOn,
            onCheckedChange = { onToggleReminder() },
        )
        Divider()
        LinkRow(title = "입력 화면 열어보기", sub = null, onClick = onOpenInput)
        LinkRow(title = "시스템 알림 설정 열기", sub = "잠금화면에 내용이 안 보일 때", onClick = onOpenNotificationSettings)
    }
}

// ── 가정 ──────────────────────────────────────────────────────────────────────

@Composable
private fun HouseholdGroup(
    ui: SettingsUi,
    onJoinInputChange: (String) -> Unit,
    onCreate: () -> Unit,
    onJoin: () -> Unit,
    onLeave: () -> Unit,
    onShare: () -> Unit,
) {
    GroupTitle("가정")
    CardBox {
        if (ui.householdPaired) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "가정 코드", color = HomePalette.Muted, fontSize = 11.sp)
                    Text(
                        text = ui.householdCode ?: "불러오는 중…",
                        color = HomePalette.Ink,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 3.sp,
                    )
                }
                Text(
                    text = "연결됨",
                    color = HomePalette.Accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(HomePalette.Soft)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            PillButton(text = "코드 공유하기", onClick = onShare, enabled = ui.householdCode != null)
            Spacer(Modifier.height(6.dp))
            TextLink("연결 해제", onLeave, color = HomePalette.Muted)
        } else {
            Text(
                text = "배우자와 가정 코드로 묶으면 공용 곳간이 생겨요",
                color = HomePalette.Ink,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            HelperText("한 사람이 코드를 만들어 보내고, 다른 사람이 그 코드를 넣으면 됩니다. 개인 곳간에는 영향이 없어요.")
            Spacer(Modifier.height(10.dp))
            PillButton(text = "코드 만들기", onClick = onCreate, enabled = !ui.householdBusy)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                MintField(
                    value = ui.householdJoinInput,
                    onValueChange = onJoinInputChange,
                    label = "받은 코드",
                    onDone = {},
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                OutlinedPillButton(
                    text = "연결",
                    onClick = onJoin,
                    enabled = !ui.householdBusy && ui.householdJoinInput.isNotBlank(),
                )
            }
        }
        val message: String? = ui.householdMessage
        if (message != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = message,
                color = if (ui.householdMessageIsError) HomePalette.Over else HomePalette.Accent,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
        }
    }
}

// ── 기록 ──────────────────────────────────────────────────────────────────────

@Composable
private fun RecordGroup(
    form: SettingsFormUi,
    onFormChange: (SettingsFormUi) -> Unit,
    onFieldDone: () -> Unit,
) {
    var open: Boolean by rememberSaveable { mutableStateOf(false) }
    GroupTitle("기록")
    CardBox {
        LinkRow(
            title = "카테고리 칩",
            sub = form.categoriesText.ifBlank { "기본 목록" },
            trailing = if (open) "접기" else "고치기",
            onClick = { open = !open },
        )
        if (open) {
            Spacer(Modifier.height(4.dp))
            MintField(
                value = form.categoriesText,
                onValueChange = { onFormChange(form.copy(categoriesText = it)) },
                label = "쉼표로 구분",
                singleLine = false,
                onDone = onFieldDone,
            )
            HelperText("기록할 때 뜨는 칩입니다. 적은 순서대로 나옵니다. 비우면 기본 목록으로 돌아갑니다.")
        }
    }
}

// ── 더보기 ────────────────────────────────────────────────────────────────────

@Composable
private fun MoreGroup(
    ui: SettingsUi,
    onExportExpenses: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onSignOut: () -> Unit,
    onUnblockSender: (String) -> Unit,
    onUnblockPackage: (String) -> Unit,
) {
    var open: Boolean by rememberSaveable { mutableStateOf(false) }
    var blockedOpen: Boolean by rememberSaveable { mutableStateOf(false) }
    var backupOpen: Boolean by rememberSaveable { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 18.dp, bottom = 6.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable { open = !open }
            .padding(horizontal = 4.dp, vertical = 4.dp),
    ) {
        Text(
            text = "더보기",
            color = HomePalette.Ink2,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        Text(text = if (open) "▴" else "▾", color = HomePalette.Ink2, fontSize = 13.sp)
    }
    if (!open) return

    CardBox {
        val blockedCount: Int = ui.blockedSenders.size + ui.blockedPackages.size
        LinkRow(
            title = "수집함에서 안 보는 출처",
            sub = "결제 알림을 모을 때 건너뛰는 발신자·앱",
            trailing = if (blockedCount > 0) "$blockedCount" else "없음",
            onClick = { blockedOpen = !blockedOpen },
        )
        if (blockedOpen) {
            if (blockedCount == 0) {
                HelperText("수집함 팝업에서 «이 발신자 안 보기»·«이 앱 안 보기»를 누르면 여기에 생깁니다.")
            }
            for (sender in ui.blockedSenders) BlockedRow(label = sender) { onUnblockSender(sender) }
            for (packageName in ui.blockedPackages) BlockedRow(label = packageName) { onUnblockPackage(packageName) }
            Spacer(Modifier.height(6.dp))
        }
        Divider()
        LinkRow(
            title = "지출 내보내기 (CSV)",
            sub = "적은 지출 전부를 «다운로드» 폴더에 표 파일로 저장해요",
            onClick = onExportExpenses,
        )
        Divider()
        LinkRow(
            title = "설정 백업 코드",
            sub = "예산·이름·월급날을 코드로 옮겨요",
            trailing = if (backupOpen) "접기" else "열기",
            onClick = { backupOpen = !backupOpen },
        )
        if (backupOpen) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedPillButton("내보내기", onExport, modifier = Modifier.weight(1f))
                OutlinedPillButton("불러오기", onImport, modifier = Modifier.weight(1f))
            }
            HelperText("«내보내기»는 지금 설정을 코드로 만들어 클립보드에 복사합니다. 메모에 붙여 두면 새 기기에서 «불러오기»로 한 번에 되돌립니다.")
            Spacer(Modifier.height(6.dp))
        }
        Divider()
        LinkRow(
            title = ui.accountEmail ?: "로그인 정보 없음",
            sub = "구글 계정",
            trailing = "로그아웃",
            onClick = onSignOut,
        )
    }
}

/** 막아 둔 출처 한 줄. 이름과 «해제». */
@Composable
private fun BlockedRow(label: String, onUnblock: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Text(text = label, color = HomePalette.Ink2, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(
            text = "해제",
            color = HomePalette.Accent,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(HomePalette.Soft)
                .clickable(onClick = onUnblock)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

// ── 아래 안내 ─────────────────────────────────────────────────────────────────

/**
 * 결과 안내. 예전에는 저장 버튼 근처에만 떠서, 아래쪽 알림 스위치를 누른 사람은 보지 못했다.
 * 이제 어디서 눌렀든 화면 아래에 뜨고, 길이에 맞춰 조금 머물다 사라진다.
 */
@Composable
private fun Toast(ui: SettingsUi, onShown: () -> Unit, modifier: Modifier = Modifier) {
    val text: String? = ui.toast
    LaunchedEffect(ui.toastId) {
        if (text == null) return@LaunchedEffect
        delay(2500L + text.length * 40L)
        onShown()
    }
    AnimatedVisibility(visible = text != null, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        Text(
            text = text.orEmpty(),
            color = Color.White,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(if (ui.toastIsError) HomePalette.Over else HomePalette.Ink)
                .clickable(onClick = onShown)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

// ── 조각들 ─────────────────────────────────────────────────────────────────────

/** 묶음 제목. 오른쪽에 «저장됨» 같은 작은 표시를 붙일 수 있다. */
@Composable
private fun GroupTitle(text: String, trailing: @Composable () -> Unit = {}) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 18.dp, bottom = 6.dp),
    ) {
        Text(
            text = text,
            color = HomePalette.Ink2,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        trailing()
    }
}

@Composable
private fun SwitchRow(title: String, sub: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = HomePalette.Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(text = sub, color = HomePalette.Muted, fontSize = 11.sp, lineHeight = 15.sp)
        }
        Spacer(Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = HomePalette.AccentBright,
                checkedThumbColor = Color.White,
                uncheckedTrackColor = HomePalette.Line,
                uncheckedThumbColor = HomePalette.Muted,
                uncheckedBorderColor = HomePalette.Line,
            ),
        )
    }
}

/** 눌러서 다른 곳으로 가거나 펼치는 줄. 오른쪽 [trailing] 이 없으면 «›». */
@Composable
private fun LinkRow(title: String, sub: String?, onClick: () -> Unit, trailing: String? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = HomePalette.Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            if (sub != null) {
                Text(text = sub, color = HomePalette.Muted, fontSize = 11.sp, lineHeight = 15.sp, maxLines = 2)
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = trailing ?: "›",
            color = if (trailing == null) HomePalette.Muted else HomePalette.Accent,
            fontSize = if (trailing == null) 20.sp else 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun Divider() {
    HorizontalDivider(color = HomePalette.Line, modifier = Modifier.padding(vertical = 6.dp))
}

@Composable
private fun HelperText(text: String) {
    Spacer(Modifier.height(6.dp))
    Text(text = text, color = HomePalette.Muted, fontSize = 12.sp, lineHeight = 17.sp)
}

/**
 * 입력칸. 칸을 떠날 때(다른 칸을 누르거나 키보드의 완료) [onDone] 을 부른다 — 설정은 그때
 * 저장한다. 여러 줄 칸은 완료 키가 줄바꿈이라 떠날 때만 부른다.
 */
@Composable
private fun MintField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    onDone: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    val focusManager = LocalFocusManager.current
    var focused: Boolean by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = singleLine,
        maxLines = if (singleLine) 1 else 3,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = if (singleLine) ImeAction.Done else ImeAction.Default,
        ),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        shape = RoundedCornerShape(14.dp),
        colors = mintFieldColors(),
        modifier = modifier.onFocusChanged {
            if (focused && !it.isFocused) onDone()
            focused = it.isFocused
        },
    )
}

@Composable
private fun PillButton(text: String, onClick: () -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = HomePalette.AccentBright,
            disabledContainerColor = HomePalette.Chip,
        ),
        modifier = modifier.fillMaxWidth().height(50.dp),
    ) {
        Text(text = text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun OutlinedPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = HomePalette.Ink),
        modifier = modifier.height(48.dp),
    ) {
        Text(text = text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun TextLink(text: String, onClick: () -> Unit, color: Color = HomePalette.Accent) {
    Text(
        text = text,
        color = color,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    )
}

@Composable
private fun WarningBanner(text: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(HomePalette.Gold.copy(alpha = 0.12f))
            .padding(14.dp),
    ) {
        Text(text = text, color = HomePalette.Ink2, fontSize = 12.sp, lineHeight = 17.sp)
    }
}

@Composable
private fun CardBox(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(HomePalette.Card)
            .padding(horizontal = 18.dp, vertical = 12.dp),
    ) {
        content()
    }
}
