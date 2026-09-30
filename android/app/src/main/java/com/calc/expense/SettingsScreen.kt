package com.calc.expense

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** 설정 폼의 값. 저장·검증 로직은 Activity 쪽(순수 상태가 아니라서)에 남는다. */
data class SettingsFormUi(
    val categoriesText: String = "",
    /** 개인 목표날(주기 마지막 날, 말일 = 31). 저장할 때 새 주기 시작일로 바꾼다([Payday.fromTarget]). */
    val payDayText: String = "",
    /** 공용 목표날. 두 폰이 같이 쓴다. */
    val sharedPayDayText: String = "",
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
    val notificationOn: Boolean = false,
    val reminderOn: Boolean = false,
    /** ‘다른 앱 위에 표시’ 권한. 있으면 결제 팝업이 시스템 팝업 줄을 서지 않고 바로 뜬다. */
    val overlayAllowed: Boolean = false,
    val accountEmail: String? = null,
    /** 계정을 지우는 중. 삭제 줄이 눌리지 않게 막는다. */
    val accountBusy: Boolean = false,
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
    /** 알림 이름 → 사용자가 고친 이름. 열쇠는 띄어쓰기를 뺀 알림 이름이다([NameMemories]). */
    val nameMemories: List<Pair<String, String>> = emptyList(),
    /** 고른 언어. null 이면 폰 언어를 따른다. */
    val language: Lang? = null,
)

/** 첫 화면에서 들어가는 하위 화면. 한 화면에 입력칸·설명·스위치를 다 늘어놓지 않으려고 나눴다. */
private enum class SettingsPage { MAIN, SHARED, PAYMENT, ACCOUNT }

/** 값 하나를 고치는 창. 첫 화면에는 값만 보이고, 누르면 이 창이 뜬다. */
private enum class EditField { BUDGET, PAYDAY, PERSONAL_NAME, SHARED_NAME, SHARED_BUDGET, SHARED_PAYDAY, CATEGORIES, LANGUAGE }

/**
 * 설정 화면. 홈·통계·도감·내역과 같은 민트 카드 화면군으로 맞춘다.
 *
 * **첫 화면은 이름과 지금 값만 한 줄씩 보인다.** 예전에는 입력칸·설명문·스위치가 한 화면에
 * 길게 이어져 원하는 줄을 찾기 어려웠다. 이제 값은 누르면 뜨는 창에서 고치고, 여러 줄이 필요한
 * 것(공용 지갑·결제 알림 정리·계정)은 하위 화면으로 들어간다.
 *
 * **저장 버튼이 없다** — 창에서 ‘저장’을 누르면 Activity 가 바로 저장한다([onFieldDone]).
 * 부수효과는 전부 Activity 쪽 콜백이다(SharedPreferences·Firebase 는 Compose 상태가 아니다).
 */
@Composable
fun SettingsScreen(
    ui: SettingsUi,
    onBack: () -> Unit,
    onFormChange: (SettingsFormUi) -> Unit,
    onFieldDone: () -> Unit,
    onToggleNotification: (Boolean) -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onToggleReminder: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onExportExpenses: () -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit,
    onHouseholdJoinInputChange: (String) -> Unit,
    onCreateHousehold: () -> Unit,
    onJoinHousehold: () -> Unit,
    onLeaveHousehold: () -> Unit,
    onShareHouseholdCode: () -> Unit,
    onOpenFixedCosts: () -> Unit,
    onUnblockSender: (String) -> Unit,
    onUnblockPackage: (String) -> Unit,
    onForgetName: (String) -> Unit,
    onToastShown: () -> Unit,
    onLanguageChange: (Lang?) -> Unit,
    /** 홈 두 칸에서 들어왔으면 그 값의 편집 창 이름([EditField]). 창을 닫으면 [onQuickEditClosed]. */
    startEdit: String? = null,
    onQuickEditClosed: () -> Unit = {},
) {
    var page: SettingsPage by rememberSaveable { mutableStateOf(SettingsPage.MAIN) }
    val quickEdit: EditField? = remember(startEdit) { EditField.entries.firstOrNull { it.name == startEdit } }
    var editing: EditField? by rememberSaveable { mutableStateOf<EditField?>(quickEdit) }

    // 하위 화면에서 뒤로가기는 첫 화면으로. 첫 화면에서는 Activity 의 기본 동작(닫기)에 맡긴다.
    BackHandler(enabled = page != SettingsPage.MAIN) { page = SettingsPage.MAIN }

    Box(modifier = Modifier.fillMaxSize().background(HomePalette.Ground)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BackButton(onClick = { if (page == SettingsPage.MAIN) onBack() else page = SettingsPage.MAIN })
                Spacer(Modifier.width(6.dp))
                Text(
                    text = when (page) {
                        SettingsPage.MAIN -> tr("설정", "Settings", "Ajustes")
                        SettingsPage.SHARED -> tr("공용 지갑", "Shared wallet", "Cartera compartida")
                        SettingsPage.PAYMENT -> tr("결제 알림 정리", "Payment alerts", "Avisos de pago")
                        SettingsPage.ACCOUNT -> tr("계정", "Account", "Cuenta")
                    },
                    color = HomePalette.Ink,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(12.dp))

            when (page) {
                SettingsPage.MAIN -> MainPage(
                    ui = ui,
                    onEdit = { editing = it },
                    onOpenPage = { page = it },
                    onToggleNotification = onToggleNotification,
                    onToggleReminder = onToggleReminder,
                    onOpenFixedCosts = onOpenFixedCosts,
                    onExportExpenses = onExportExpenses,
                    onOpenNotificationSettings = onOpenNotificationSettings,
                )
                SettingsPage.SHARED -> SharedPage(
                    ui = ui,
                    onEdit = { editing = it },
                    onJoinInputChange = onHouseholdJoinInputChange,
                    onCreate = onCreateHousehold,
                    onJoin = onJoinHousehold,
                    onLeave = onLeaveHousehold,
                    onShare = onShareHouseholdCode,
                )
                SettingsPage.PAYMENT -> PaymentPage(
                    ui = ui,
                    onOpenOverlaySettings = onOpenOverlaySettings,
                    onUnblockSender = onUnblockSender,
                    onUnblockPackage = onUnblockPackage,
                    onForgetName = onForgetName,
                )
                SettingsPage.ACCOUNT -> AccountPage(ui = ui, onSignOut = onSignOut, onDeleteAccount = onDeleteAccount)
            }

            // 아래 안내가 마지막 카드를 가리지 않게 여백을 둔다.
            Spacer(Modifier.height(72.dp))
        }

        Toast(ui, onToastShown, modifier = Modifier.align(Alignment.BottomCenter))
    }

    val field: EditField? = editing
    if (field != null) {
        EditDialog(
            field = field,
            ui = ui,
            onFormChange = onFormChange,
            onFieldDone = onFieldDone,
            onLanguageChange = onLanguageChange,
            onOpenFixedCosts = onOpenFixedCosts,
            onDismiss = {
                editing = null
                // 홈에서 값 하나만 고치러 왔으면 설정 화면을 거치지 않고 바로 돌아간다.
                if (quickEdit != null) onQuickEditClosed()
            },
        )
    }
}

// ── 첫 화면 ───────────────────────────────────────────────────────────────────

@Composable
private fun MainPage(
    ui: SettingsUi,
    onEdit: (EditField) -> Unit,
    onOpenPage: (SettingsPage) -> Unit,
    onToggleNotification: (Boolean) -> Unit,
    onToggleReminder: () -> Unit,
    onOpenFixedCosts: () -> Unit,
    onExportExpenses: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
) {
    val form: SettingsFormUi = ui.form
    AccountCard(email = ui.accountEmail, sub = tr("구글 계정", "Google account", "Cuenta de Google"), onClick = { onOpenPage(SettingsPage.ACCOUNT) })

    // 개인·공용 예산과 월급날을 한 묶음에 둔다. 공용 예산이 «공용 지갑» 안쪽 화면에만 있을 때는
    // 설정에 없는 것처럼 보였다. 월급날은 하나 — 개인·공용이 같은 날에 끊긴다.
    Group(tr("예산", "Budget", "Presupuesto")) {
        ValueRow(
            if (ui.householdPaired) tr("개인 한 달 예산", "Personal monthly budget", "Presupuesto mensual personal")
            else tr("한 달 예산", "Monthly budget", "Presupuesto mensual"),
            budgetLabel(form.personalBudgetText),
        ) { onEdit(EditField.BUDGET) }
        RowDivider()
        ValueRow(
            tr("고정비", "Fixed costs", "Gastos fijos"),
            if (ui.fixedTotal > 0L) StatusText.won(ui.fixedTotal) else tr("정하지 않음", "Not set", "Sin definir"),
            onClick = onOpenFixedCosts,
        )
        if (ui.householdPaired) {
            RowDivider()
            ValueRow(tr("공용 한 달 예산", "Shared monthly budget", "Presupuesto mensual compartido"), budgetLabel(form.sharedBudgetText)) {
                onEdit(EditField.SHARED_BUDGET)
            }
        }
        RowDivider()
        ValueRow(
            if (ui.householdPaired) tr("개인 목표날", "Personal target day", "Día objetivo personal")
            else tr("목표날", "Target day", "Día objetivo"),
            paydayLabel(form.payDayText),
        ) { onEdit(EditField.PAYDAY) }
        if (ui.householdPaired) {
            RowDivider()
            ValueRow(tr("공용 목표날", "Shared target day", "Día objetivo compartido"), paydayLabel(form.sharedPayDayText)) {
                onEdit(EditField.SHARED_PAYDAY)
            }
        }
    }
    if (ui.householdPaired) {
        Note(
            tr(
                "공용 예산·공용 목표날은 배우자 폰에도 같이 바뀌어요.",
                "The shared budget and shared target day also change on your partner's phone.",
                "El presupuesto y el día objetivo compartidos también cambian en el teléfono de tu pareja.",
            ),
        )
    }

    Group(tr("지갑", "Wallets", "Carteras")) {
        ValueRow(
            tr("개인 지갑 이름", "Personal wallet name", "Nombre de la cartera personal"),
            form.personalName.ifBlank { Purse.PERSONAL.defaultLabel },
        ) { onEdit(EditField.PERSONAL_NAME) }
        RowDivider()
        ValueRow(
            tr("공용 지갑", "Shared wallet", "Cartera compartida"),
            if (ui.householdPaired) tr("배우자와 연결됨", "Linked with partner", "Vinculada con tu pareja")
            else tr("연결 안 됨", "Not linked", "Sin vincular"),
        ) { onOpenPage(SettingsPage.SHARED) }
    }

    Group(tr("기록", "Logging", "Registro")) {
        SwitchRow(tr("잠금화면 카드", "Lock screen card", "Tarjeta en pantalla de bloqueo"), ui.notificationOn, onToggleNotification)
        RowDivider()
        SwitchRow(tr("결제 알림 읽기", "Read payment alerts", "Leer avisos de pago"), ui.reminderOn) { onToggleReminder() }
        RowDivider()
        ValueRow(tr("카테고리", "Categories", "Categorías"), categoryCountLabel(form.categoriesText)) { onEdit(EditField.CATEGORIES) }
        RowDivider()
        ValueRow(
            tr("결제 알림 정리", "Payment alerts", "Avisos de pago"),
            // 할 일이 남았을 때만 값 자리에 말한다 — 권한을 켜면 팝업이 기다리지 않고 뜬다.
            if (ui.reminderOn && !ui.overlayAllowed) tr("팝업 권한 필요", "Popup needs permission", "Falta permiso") else null,
            valueColor = HomePalette.Accent,
        ) { onOpenPage(SettingsPage.PAYMENT) }
    }

    Group(tr("기타", "Other", "Otros")) {
        ValueRow(tr("언어", "Language", "Idioma"), languageLabel(ui.language)) { onEdit(EditField.LANGUAGE) }
        RowDivider()
        ValueRow(tr("지출 내보내기", "Export spending", "Exportar gastos"), "CSV", onClick = onExportExpenses)
        RowDivider()
        ValueRow(tr("잠금화면에 안 보일 때", "Not showing on lock screen", "No aparece en la pantalla de bloqueo"), null, onClick = onOpenNotificationSettings)
    }
}

private fun budgetLabel(text: String): String {
    val amount: Long = ExpenseParser.parseAmount(text.trim()) ?: 0L
    return if (amount > 0L) StatusText.won(amount) else tr("정하지 않음", "Not set", "Sin definir")
}

/** 목표날 편집 창의 도움말. */
private fun targetHelper(): String = tr(
    "이날까지가 한 주기예요. 다음 날부터 새 주기가 시작돼요. 말일로 하려면 31을 적어 주세요.",
    "A cycle ends on this day; the next one starts the day after. Use 31 for the end of the month.",
    "Un ciclo termina este día y el siguiente empieza al día siguiente. Usa 31 para fin de mes.",
)

/** 목표날 칸(사람이 적은 목표날)을 «매달 14일»·«매달 말일»로. */
private fun paydayLabel(text: String): String {
    val target: Int = text.trim().toIntOrNull() ?: 31
    return StatusText.targetDay(Payday.fromTarget(target))
}

private fun categoryCountLabel(text: String): String {
    val count: Int = Categories.parse(text).size.takeIf { it > 0 } ?: Categories.DEFAULT.size
    return tr("${count}개", "$count", "$count")
}

private fun languageLabel(lang: Lang?): String = when (lang) {
    null -> tr("폰 설정", "System", "Sistema")
    Lang.KO -> "한국어"
    Lang.EN -> "English"
    Lang.ES -> "Español"
}

// ── 공용 지갑 ─────────────────────────────────────────────────────────────────

@Composable
private fun SharedPage(
    ui: SettingsUi,
    onEdit: (EditField) -> Unit,
    onJoinInputChange: (String) -> Unit,
    onCreate: () -> Unit,
    onJoin: () -> Unit,
    onLeave: () -> Unit,
    onShare: () -> Unit,
) {
    if (ui.householdPaired) {
        CardBox {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = tr("가정 코드", "Household code", "Código de hogar"), color = HomePalette.Muted, fontSize = 12.sp)
                    Text(
                        text = ui.householdCode ?: tr("불러오는 중…", "Loading…", "Cargando…"),
                        color = HomePalette.Ink,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 3.sp,
                        style = Figures,
                    )
                }
                Text(
                    text = tr("연결됨", "Linked", "Vinculado"),
                    color = HomePalette.Accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(HomePalette.Soft)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            PillButton(text = tr("코드 공유하기", "Share code", "Compartir código"), onClick = onShare, enabled = ui.householdCode != null)
            Spacer(Modifier.height(6.dp))
        }

        Group(tr("같이 쓰는 값", "Shared with your partner", "Compartido con tu pareja")) {
            ValueRow(
                tr("공용 지갑 이름", "Shared wallet name", "Nombre de la cartera compartida"),
                ui.form.sharedName.ifBlank { Purse.SHARED.defaultLabel },
            ) { onEdit(EditField.SHARED_NAME) }
            RowDivider()
            ValueRow(tr("공용 한 달 예산", "Shared monthly budget", "Presupuesto mensual compartido"), budgetLabel(ui.form.sharedBudgetText)) {
                onEdit(EditField.SHARED_BUDGET)
            }
            RowDivider()
            ValueRow(tr("공용 목표날", "Shared target day", "Día objetivo compartido"), paydayLabel(ui.form.sharedPayDayText)) {
                onEdit(EditField.SHARED_PAYDAY)
            }
        }
        Note(
            tr(
                "이름·예산·목표날을 바꾸면 배우자 폰에도 같이 바뀌어요.",
                "Changes to the name, budget and target day also apply on your partner's phone.",
                "Los cambios de nombre, presupuesto y día objetivo también se aplican en el teléfono de tu pareja.",
            ),
        )
        Spacer(Modifier.height(18.dp))
        CardBox {
            ValueRow(tr("연결 해제", "Leave household", "Salir del hogar"), null, titleColor = HomePalette.Over, onClick = onLeave)
        }
    } else {
        CardBox {
            Spacer(Modifier.height(8.dp))
            Text(
                text = tr(
                    "배우자와 가정 코드로 연결하면 공용 지갑이 생겨요",
                    "Link with your partner using a household code to get a shared wallet",
                    "Vincúlate con tu pareja con un código de hogar para tener una cartera compartida",
                ),
                color = HomePalette.Ink,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 21.sp,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = tr(
                    "한 사람이 코드를 만들어 보내고, 다른 사람이 그 코드를 넣으면 돼요. 개인 지갑은 그대로예요.",
                    "One of you creates a code and sends it; the other enters it. Your personal wallet stays as it is.",
                    "Uno crea un código y lo envía; el otro lo introduce. Tu cartera personal no cambia.",
                ),
                color = HomePalette.Ink2,
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )
            Spacer(Modifier.height(14.dp))
            PillButton(text = tr("코드 만들기", "Create code", "Crear código"), onClick = onCreate, enabled = !ui.householdBusy)
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = ui.householdJoinInput,
                    onValueChange = onJoinInputChange,
                    label = { Text(tr("받은 코드", "Received code", "Código recibido")) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = mintFieldColors(),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = onJoin,
                    enabled = !ui.householdBusy && ui.householdJoinInput.isNotBlank(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = HomePalette.Ink),
                    modifier = Modifier.height(52.dp),
                ) {
                    Text(text = tr("연결", "Join", "Unirse"), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }

    val message: String? = ui.householdMessage
    if (message != null) {
        Spacer(Modifier.height(10.dp))
        Note(message, color = if (ui.householdMessageIsError) HomePalette.Over else HomePalette.Accent)
    }
}

// ── 결제 알림 정리 ────────────────────────────────────────────────────────────

@Composable
private fun PaymentPage(
    ui: SettingsUi,
    onOpenOverlaySettings: () -> Unit,
    onUnblockSender: (String) -> Unit,
    onUnblockPackage: (String) -> Unit,
    onForgetName: (String) -> Unit,
) {
    // 결제 알림을 켤 때 권한을 한 번 물었는데 건너뛴 사람을 위한 두 번째 문.
    // 이미 켰으면 안 보인다 — 할 일이 없는 줄은 두지 않는다.
    if (ui.reminderOn && !ui.overlayAllowed) {
        CardBox {
            ValueRow(tr("결제 팝업 바로 띄우기", "Show payment popup right away", "Mostrar el aviso de pago al momento"), null, onClick = onOpenOverlaySettings)
        }
        Note(
            tr(
                "‘다른 앱 위에 표시’를 켜면 카드사 알림을 기다리지 않고 바로 떠요.",
                "Turn on ‘Display over other apps’ so it shows without waiting for the card alert.",
                "Activa «Mostrar sobre otras apps» para que salga sin esperar al aviso de la tarjeta.",
            ),
        )
    }

    Group(tr("숨긴 출처", "Hidden sources", "Fuentes ocultas")) {
        val sources: List<Pair<String, () -> Unit>> =
            ui.blockedSenders.map { sender -> sender to { onUnblockSender(sender) } } +
                ui.blockedPackages.map { pkg -> pkg to { onUnblockPackage(pkg) } }
        if (sources.isEmpty()) {
            EmptyLine(
                tr(
                    "수집함에서 ‘이 발신자 안 보기’를 누르면 여기에 생겨요.",
                    "Appears here when you tap ‘Hide this sender’ in the inbox.",
                    "Aparece aquí al tocar «Ocultar remitente» en la bandeja.",
                ),
            )
        }
        sources.forEachIndexed { index, (label, onUnblock) ->
            if (index > 0) RowDivider()
            ActionRow(label = label, action = tr("다시 보기", "Unhide", "Mostrar"), onAction = onUnblock)
        }
    }

    Group(tr("바꿔 띄우는 이름", "Renamed alerts", "Avisos renombrados")) {
        if (ui.nameMemories.isEmpty()) {
            EmptyLine(
                tr(
                    "수집함에서 알림 이름을 고쳐 기록하면 여기에 생겨요. 쇼핑몰이나 카드사 이름만 읽힌 결제는 기억하지 않아요.",
                    "Appears here when you correct an alert's name in the inbox. Payments that only show a store like Coupang or a card company aren't remembered.",
                    "Aparece aquí al corregir el nombre de un aviso en la bandeja. No se recuerdan los pagos que solo muestran una tienda como Coupang o la emisora de la tarjeta.",
                ),
            )
        }
        ui.nameMemories.forEachIndexed { index, (from, to) ->
            if (index > 0) RowDivider()
            ActionRow(label = "$from → $to", action = tr("지우기", "Remove", "Borrar")) { onForgetName(from) }
        }
    }
}

// ── 계정 ──────────────────────────────────────────────────────────────────────

@Composable
private fun AccountPage(ui: SettingsUi, onSignOut: () -> Unit, onDeleteAccount: () -> Unit) {
    var confirming: Boolean by rememberSaveable { mutableStateOf(false) }

    AccountCard(email = ui.accountEmail, sub = tr("구글로 로그인함", "Signed in with Google", "Sesión iniciada con Google"), onClick = null)
    Spacer(Modifier.height(14.dp))
    CardBox {
        ValueRow(tr("로그아웃", "Sign out", "Cerrar sesión"), null, onClick = onSignOut)
        RowDivider()
        ValueRow(
            if (ui.accountBusy) tr("지우는 중…", "Deleting…", "Borrando…") else tr("계정과 기록 삭제", "Delete account and records", "Eliminar cuenta y registros"),
            null,
            titleColor = HomePalette.Over,
            onClick = { if (!ui.accountBusy) confirming = true },
        )
    }
    Note(
        tr(
            "삭제하면 이 계정에 저장된 지출 기록과 설정이 모두 지워지고 되돌릴 수 없어요. 공용 지갑 기록은 배우자 쪽에 남아요.",
            "Deleting removes all spending records and settings saved to this account, and it can't be undone. Shared wallet records stay with your partner.",
            "Al eliminarla se borran todos los gastos y ajustes guardados en esta cuenta, y no se puede deshacer. Los gastos de la cartera compartida se quedan con tu pareja.",
        ),
    )

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            containerColor = HomePalette.Card,
            title = {
                Text(tr("계정을 삭제할까요?", "Delete your account?", "¿Eliminar tu cuenta?"), color = HomePalette.Ink, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    tr(
                        "지출 기록과 설정이 지워지고 되돌릴 수 없어요. 지우기 전에 구글 계정을 한 번 더 확인해요.",
                        "Your spending records and settings will be deleted for good. You'll confirm your Google account once more first.",
                        "Tus gastos y ajustes se borrarán para siempre. Antes confirmarás tu cuenta de Google una vez más.",
                    ),
                    color = HomePalette.Ink2,
                    lineHeight = 20.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirming = false
                    onDeleteAccount()
                }) {
                    Text(tr("삭제", "Delete", "Eliminar"), color = HomePalette.Over, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) {
                    Text(tr("취소", "Cancel", "Cancelar"), color = HomePalette.Ink2)
                }
            },
        )
    }
}

// ── 값 고치는 창 ──────────────────────────────────────────────────────────────

@Composable
private fun EditDialog(
    field: EditField,
    ui: SettingsUi,
    onFormChange: (SettingsFormUi) -> Unit,
    onFieldDone: () -> Unit,
    onLanguageChange: (Lang?) -> Unit,
    onOpenFixedCosts: () -> Unit,
    onDismiss: () -> Unit,
) {
    val form: SettingsFormUi = ui.form
    // 저장은 폼을 바꾼 뒤 Activity 의 자동 저장을 부르는 한 길뿐이다.
    val save: (SettingsFormUi) -> Unit = { next ->
        onFormChange(next)
        onFieldDone()
        onDismiss()
    }

    when (field) {
        EditField.LANGUAGE -> LanguageDialog(current = ui.language, onPick = { lang ->
            onDismiss()
            if (lang != ui.language) onLanguageChange(lang)
        }, onDismiss = onDismiss)

        EditField.BUDGET -> TextEditDialog(
            title = tr("한 달 예산", "Monthly budget", "Presupuesto mensual"),
            initial = form.personalBudgetText,
            keyboardType = KeyboardType.Number,
            helper = tr(
                "하루치는 이 금액을 주기 날수로 나눠 정해요. ‘93만’처럼 적어도 돼요.",
                "Your daily amount is this budget divided by the days in the cycle. You can also type ‘93만’ (= 930,000).",
                "Tu cantidad diaria es este presupuesto dividido entre los días del ciclo. También puedes escribir «93만» (= 930.000).",
            ),
            extraLink = tr("월급·고정비로 계산하기", "Calculate from income and fixed costs", "Calcular con sueldo y gastos fijos") to {
                onDismiss()
                onOpenFixedCosts()
            },
            onSave = { save(form.copy(personalBudgetText = it)) },
            onDismiss = onDismiss,
        )

        EditField.PAYDAY -> TextEditDialog(
            title = tr("목표날", "Target day", "Día objetivo"),
            initial = form.payDayText,
            keyboardType = KeyboardType.Number,
            helper = targetHelper(),
            filter = { it.filter(Char::isDigit).take(2) },
            onSave = { save(form.copy(payDayText = it)) },
            onDismiss = onDismiss,
        )

        EditField.SHARED_PAYDAY -> TextEditDialog(
            title = tr("공용 목표날", "Shared target day", "Día objetivo compartido"),
            initial = form.sharedPayDayText,
            keyboardType = KeyboardType.Number,
            helper = targetHelper() + " " + tr(
                "배우자 폰에도 같이 바뀌어요.",
                "It also changes on your partner's phone.",
                "También cambia en el teléfono de tu pareja.",
            ),
            filter = { it.filter(Char::isDigit).take(2) },
            onSave = { save(form.copy(sharedPayDayText = it)) },
            onDismiss = onDismiss,
        )

        EditField.PERSONAL_NAME -> TextEditDialog(
            title = tr("개인 지갑 이름", "Personal wallet name", "Nombre de la cartera personal"),
            initial = form.personalName,
            helper = tr(
                "잠금화면 버튼과 홈 카드에 이 이름이 나와요. ${Purse.MAX_NAME_LENGTH}자까지 돼요.",
                "Shown on the lock screen button and home card. Up to ${Purse.MAX_NAME_LENGTH} characters.",
                "Aparece en el botón de la pantalla de bloqueo y en la tarjeta de inicio. Hasta ${Purse.MAX_NAME_LENGTH} caracteres.",
            ),
            filter = { it.take(Purse.MAX_NAME_LENGTH) },
            onSave = { save(form.copy(personalName = it)) },
            onDismiss = onDismiss,
        )

        EditField.SHARED_NAME -> TextEditDialog(
            title = tr("공용 지갑 이름", "Shared wallet name", "Nombre de la cartera compartida"),
            initial = form.sharedName,
            helper = tr(
                "배우자 폰에도 같은 이름으로 나와요. ${Purse.MAX_NAME_LENGTH}자까지 돼요.",
                "Your partner's phone shows the same name. Up to ${Purse.MAX_NAME_LENGTH} characters.",
                "El teléfono de tu pareja muestra el mismo nombre. Hasta ${Purse.MAX_NAME_LENGTH} caracteres.",
            ),
            filter = { it.take(Purse.MAX_NAME_LENGTH) },
            onSave = { save(form.copy(sharedName = it)) },
            onDismiss = onDismiss,
        )

        EditField.SHARED_BUDGET -> TextEditDialog(
            title = tr("공용 한 달 예산", "Shared monthly budget", "Presupuesto mensual compartido"),
            initial = form.sharedBudgetText,
            keyboardType = KeyboardType.Number,
            helper = tr(
                "배우자 폰에도 같은 예산이 적용돼요. ‘150만’처럼 적어도 돼요.",
                "The same budget applies on your partner's phone. You can also type ‘150만’ (= 1,500,000).",
                "El mismo presupuesto se aplica en el teléfono de tu pareja. También puedes escribir «150만» (= 1.500.000).",
            ),
            onSave = { save(form.copy(sharedBudgetText = it)) },
            onDismiss = onDismiss,
        )

        EditField.CATEGORIES -> TextEditDialog(
            title = tr("카테고리", "Categories", "Categorías"),
            initial = form.categoriesText,
            singleLine = false,
            helper = tr(
                "쉼표로 나눠 적어요. 기록할 때 적은 순서대로 나와요. 비우면 기본 목록으로 돌아가요.",
                "Separate with commas. They appear in this order when logging. Leave empty for the default list.",
                "Sepáralas con comas. Aparecen en este orden al anotar. Déjalo vacío para la lista predeterminada.",
            ),
            onSave = { save(form.copy(categoriesText = it)) },
            onDismiss = onDismiss,
        )
    }
}

@Composable
private fun TextEditDialog(
    title: String,
    initial: String,
    helper: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    filter: (String) -> String = { it },
    extraLink: Pair<String, () -> Unit>? = null,
) {
    // 커서를 끝에 둔다 — 금액을 고치러 열었는데 커서가 맨 앞이면 한 번 더 눌러야 한다.
    var value: TextFieldValue by remember { mutableStateOf(TextFieldValue(initial, TextRange(initial.length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        // 창이 뜨자마자 키보드를 올린다. 칸이 아직 붙지 않았으면 조용히 넘어간다.
        runCatching { focus.requestFocus() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HomePalette.Card,
        title = { Text(title, color = HomePalette.Ink, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = value,
                    onValueChange = { next -> value = next.copy(text = filter(next.text)) },
                    singleLine = singleLine,
                    maxLines = if (singleLine) 1 else 4,
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                    shape = RoundedCornerShape(14.dp),
                    colors = mintFieldColors(),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
                Spacer(Modifier.height(8.dp))
                Text(text = helper, color = HomePalette.Muted, fontSize = 12.sp, lineHeight = 17.sp)
                if (extraLink != null) {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = extraLink.second)
                            .padding(vertical = 8.dp),
                    ) {
                        Text(
                            text = extraLink.first,
                            color = HomePalette.Accent,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        Chevron(tint = HomePalette.Accent, size = 18.dp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(value.text) }) {
                Text(tr("저장", "Save", "Guardar"), color = HomePalette.Accent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(tr("취소", "Cancel", "Cancelar"), color = HomePalette.Ink2)
            }
        },
    )
}

/** 언어 고르기. 선택지는 그 언어 자신의 이름으로 적는다 — 모르는 언어로 잘못 바꿔도 돌아올 수 있게. */
@Composable
private fun LanguageDialog(current: Lang?, onPick: (Lang?) -> Unit, onDismiss: () -> Unit) {
    val options: List<Lang?> = listOf(null, Lang.KO, Lang.EN, Lang.ES)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HomePalette.Card,
        // 제목에도 «Language» 를 붙여 둔다 — 읽지 못하는 언어로 바꿔 버린 사람도 찾아 돌아오게.
        title = { Text(tr("언어 · Language", "Language", "Idioma · Language"), color = HomePalette.Ink, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                for (lang in options) {
                    val on: Boolean = lang == current
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onPick(lang) }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                    ) {
                        Text(
                            text = languageLabel(lang),
                            color = if (on) HomePalette.Accent else HomePalette.Ink,
                            fontSize = 15.sp,
                            fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.weight(1f),
                        )
                        if (on) {
                            Icon(
                                painter = painterResource(R.drawable.ic_check),
                                contentDescription = null,
                                tint = HomePalette.Accent,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(tr("닫기", "Close", "Cerrar"), color = HomePalette.Ink2)
            }
        },
    )
}

// ── 아래 안내 ─────────────────────────────────────────────────────────────────

/**
 * 결과 안내. 어디서 눌렀든 화면 아래에 뜨고, 길이에 맞춰 조금 머물다 사라진다.
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

/** 계정 한 줄. 첫 화면에서는 눌러서 계정 화면으로, 계정 화면에서는 머리 표시로만 쓴다. */
@Composable
private fun AccountCard(email: String?, sub: String, onClick: (() -> Unit)?) {
    val shown: String = email ?: tr("로그인 정보 없음", "Not signed in", "Sin sesión")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(HomePalette.Card)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(40.dp).clip(CircleShape).background(HomePalette.Soft),
        ) {
            Text(
                text = shown.take(1).uppercase(),
                color = HomePalette.Accent,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = shown, color = HomePalette.Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(text = sub, color = HomePalette.Muted, fontSize = 12.sp)
        }
        if (onClick != null) Chevron()
    }
}

/** 묶음 제목과 흰 카드 하나. */
@Composable
private fun Group(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(
        text = title,
        color = HomePalette.Ink2,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 20.dp, bottom = 8.dp),
    )
    CardBox(content)
}

/**
 * 이름과 지금 값 한 줄. 설명문은 두지 않는다 — 설명이 필요한 값은 눌러서 뜨는 창에서 한 줄로
 * 말한다. 이름이 길면(스페인어 등) 이름 쪽이 줄바꿈되고 값은 한 줄을 지킨다.
 */
@Composable
private fun ValueRow(
    title: String,
    value: String?,
    titleColor: Color = HomePalette.Ink,
    valueColor: Color = HomePalette.Ink2,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
    ) {
        Text(text = title, color = titleColor, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        if (value != null) {
            Spacer(Modifier.width(10.dp))
            Text(
                text = value,
                color = valueColor,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = Figures,
                modifier = Modifier.widthIn(max = 170.dp),
            )
        }
        Spacer(Modifier.width(4.dp))
        Chevron()
    }
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
    ) {
        Text(text = title, color = HomePalette.Ink, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
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

/** 지울 수 있는 한 줄. 숨긴 출처(«다시 보기»)와 이름 기억(«지우기»)이 같이 쓴다. */
@Composable
private fun ActionRow(label: String, action: String, onAction: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 10.dp)) {
        Text(text = label, color = HomePalette.Ink, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Text(
            text = action,
            color = HomePalette.Accent,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(HomePalette.Soft)
                .clickable(onClick = onAction)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun EmptyLine(text: String) {
    Text(text = text, color = HomePalette.Muted, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(vertical = 12.dp))
}

/** 카드 아래 작은 안내 한 줄. */
@Composable
private fun Note(text: String, color: Color = HomePalette.Muted) {
    Text(
        text = text,
        color = color,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp),
    )
}

@Composable
private fun RowDivider() {
    HorizontalDivider(color = HomePalette.Line)
}

@Composable
private fun PillButton(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = HomePalette.AccentBright,
            disabledContainerColor = HomePalette.Chip,
        ),
        modifier = Modifier.fillMaxWidth().height(50.dp),
    ) {
        Text(text = text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CardBox(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(HomePalette.Card)
            .padding(horizontal = 18.dp, vertical = 2.dp),
        content = content,
    )
}
