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
    /** «다른 앱 위에 표시» 권한. 있으면 결제 팝업이 시스템 팝업 줄을 서지 않고 바로 뜬다. */
    val overlayAllowed: Boolean = false,
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
    /** 알림 이름 → 사용자가 고친 이름. 열쇠는 띄어쓰기를 뺀 알림 이름이다([NameMemories]). */
    val nameMemories: List<Pair<String, String>> = emptyList(),
    /** 고른 언어. null 이면 폰 언어를 따른다. */
    val language: Lang? = null,
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
    onOpenOverlaySettings: () -> Unit,
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
    onForgetName: (String) -> Unit,
    onToastShown: () -> Unit,
    onLanguageChange: (Lang?) -> Unit,
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
                Text(text = tr("설정", "Settings", "Ajustes"), color = HomePalette.Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }

            LanguageGroup(ui.language, onLanguageChange)
            BudgetGroup(ui, onFormChange, onFieldDone, onOpenFixedCosts)
            NotificationGroup(ui, onToggleNotification, onToggleReminder, onOpenOverlaySettings, onOpenInput, onOpenNotificationSettings)
            HouseholdGroup(
                ui, onHouseholdJoinInputChange, onCreateHousehold, onJoinHousehold,
                onLeaveHousehold, onShareHouseholdCode,
            )
            RecordGroup(ui.form, onFormChange, onFieldDone)
            MoreGroup(
                ui, onExportExpenses, onExport, onImport, onSignOut, onUnblockSender, onUnblockPackage,
                onForgetName,
            )

            if (ui.showStorageNotice) {
                Spacer(Modifier.height(12.dp))
                WarningBanner(
                    tr(
                        "이 기기에서 암호화 저장소를 열지 못해 설정이 평문으로 저장됩니다. 앱 전용 영역이라 다른 앱은 읽지 못합니다.",
                        "Encrypted storage couldn't be opened on this device, so settings are saved unencrypted. They're in app-only storage that other apps can't read.",
                        "No se pudo abrir el almacenamiento cifrado en este dispositivo, así que los ajustes se guardan sin cifrar. Están en un espacio exclusivo de la app que otras apps no pueden leer.",
                    ),
                )
            }

            // 아래 안내가 마지막 카드를 가리지 않게 여백을 둔다.
            Spacer(Modifier.height(72.dp))
        }

        Toast(ui, onToastShown, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

// ── 언어 ──────────────────────────────────────────────────────────────────────

/**
 * 화면 언어. 선택지는 그 언어 자신의 이름으로 적는다 — 모르는 언어로 잘못 바꿔도 돌아올 수 있게.
 * 제목에도 «Language» 를 붙여 둔다.
 */
@Composable
private fun LanguageGroup(current: Lang?, onChange: (Lang?) -> Unit) {
    GroupTitle(tr("언어 · Language", "Language", "Idioma · Language"))
    CardBox {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            val options: List<Pair<Lang?, String>> = listOf(
                null to tr("폰 설정", "System", "Sistema"),
                Lang.KO to "한국어",
                Lang.EN to "English",
                Lang.ES to "Español",
            )
            for ((lang, label) in options) {
                val on: Boolean = current == lang
                Text(
                    text = label,
                    color = if (on) Color.White else HomePalette.Ink2,
                    fontSize = 13.sp,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (on) HomePalette.AccentBright else HomePalette.Chip)
                        .clickable { if (!on) onChange(lang) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
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
    GroupTitle(tr("예산", "Budget", "Presupuesto")) { SavedMark(ui.savedCount) }
    CardBox {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MintField(
                value = form.personalName,
                onValueChange = { onFormChange(form.copy(personalName = it.take(Purse.MAX_NAME_LENGTH))) },
                label = tr("개인 곳간 이름", "Personal wallet name", "Nombre de la cartera personal"),
                onDone = onFieldDone,
                modifier = Modifier.weight(1f),
            )
            MintField(
                value = form.personalBudgetText,
                onValueChange = { onFormChange(form.copy(personalBudgetText = it)) },
                label = tr("월 예산", "Monthly budget", "Presupuesto mensual"),
                keyboardType = KeyboardType.Number,
                onDone = onFieldDone,
                modifier = Modifier.weight(1.2f),
            )
        }
        HelperText(
            tr(
                "예산을 주기 일수로 나눈 값이 하루치이고, 아낀 만큼 곳간에 쌓입니다. «93만»처럼 적어도 됩니다.",
                "The budget divided by the days in the cycle is your daily amount; what you save builds up. You can also type «93만» (= 930,000).",
                "El presupuesto dividido entre los días del ciclo es tu cantidad diaria; lo que ahorras se acumula. También puedes escribir «93만» (= 930.000).",
            ),
        )
        Divider()
        LinkRow(
            title =
                if (ui.fixedTotal > 0L) tr("고정비 고치고 다시 계산", "Edit fixed costs and recalculate", "Editar gastos fijos y recalcular")
                else tr("고정비로 계산하기", "Calculate from fixed costs", "Calcular con gastos fijos"),
            sub = if (ui.fixedTotal > 0L) {
                tr("지금 적어 둔 고정비 ", "Current fixed costs ", "Gastos fijos actuales ") + StatusText.won(ui.fixedTotal)
            } else {
                tr(
                    "월급에서 월세·보험 같은 고정비를 빼 개인 예산을 정해요",
                    "Subtract fixed costs like rent and insurance from your income to set your budget",
                    "Resta a tu sueldo gastos fijos como alquiler o seguros para fijar tu presupuesto",
                )
            },
            onClick = onOpenFixedCosts,
        )

        if (ui.householdPaired) {
            Divider()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MintField(
                    value = form.sharedName,
                    onValueChange = { onFormChange(form.copy(sharedName = it.take(Purse.MAX_NAME_LENGTH))) },
                    label = tr("공용 곳간 이름", "Shared wallet name", "Nombre de la cartera compartida"),
                    onDone = onFieldDone,
                    modifier = Modifier.weight(1f),
                )
                MintField(
                    value = form.sharedBudgetText,
                    onValueChange = { onFormChange(form.copy(sharedBudgetText = it)) },
                    label = tr("공용 월 예산", "Shared monthly budget", "Presupuesto mensual compartido"),
                    keyboardType = KeyboardType.Number,
                    onDone = onFieldDone,
                    modifier = Modifier.weight(1.2f),
                )
            }
            HelperText(
                tr(
                    "공용 곳간 이름·예산과 월급날은 두 폰이 같이 씁니다. 여기서 바꾸면 배우자 폰도 앱을 열 때 따라 바뀝니다.",
                    "The shared wallet's name, budget and payday are shared by both phones. Changes here apply to your partner's phone when they open the app.",
                    "El nombre, el presupuesto y el día de cobro de la cartera compartida son comunes a ambos teléfonos. Los cambios se aplican en el de tu pareja al abrir la app.",
                ),
            )
        }

        Divider()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = tr("월급날", "Payday", "Día de cobro"), color = HomePalette.Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    text = tr(
                        "이날부터 다음 월급 전날까지가 한 주기예요. 달력대로 쓰려면 1",
                        "A cycle runs from this day to the day before the next payday. Use 1 for calendar months",
                        "Un ciclo va de este día al día anterior al siguiente cobro. Usa 1 para meses naturales",
                    ),
                    color = HomePalette.Muted,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                )
            }
            Spacer(Modifier.width(10.dp))
            MintField(
                value = form.payDayText,
                onValueChange = { onFormChange(form.copy(payDayText = it.filter(Char::isDigit).take(2))) },
                label = tr("일", "Day", "Día"),
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
        Text(text = tr("저장됨 ✓", "Saved ✓", "Guardado ✓"), color = HomePalette.Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

// ── 알림 ──────────────────────────────────────────────────────────────────────

@Composable
private fun NotificationGroup(
    ui: SettingsUi,
    onToggleNotification: (Boolean) -> Unit,
    onToggleReminder: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onOpenInput: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
) {
    GroupTitle(tr("알림", "Notifications", "Notificaciones"))
    CardBox {
        SwitchRow(
            title = tr("잠금화면 알림", "Lock screen card", "Tarjeta en pantalla de bloqueo"),
            sub = tr(
                "잠금화면에 오늘 쓸 수 있는 돈을 띄우고, 눌러서 바로 적어요",
                "Shows what you can spend today on the lock screen; tap to log right away",
                "Muestra en la pantalla de bloqueo lo que puedes gastar hoy; tócala para anotar al momento",
            ),
            checked = ui.notificationOn,
            onCheckedChange = onToggleNotification,
        )
        Divider()
        SwitchRow(
            title = tr("결제 알림 읽기", "Read payment alerts", "Leer avisos de pago"),
            sub = tr(
                "카드·은행 알림을 보면 금액과 가게를 잠깐 띄워요. «알림 접근» 권한이 필요해요",
                "Briefly shows the amount and store from card and bank alerts (Korean banks only). Needs «Notification access»",
                "Muestra un momento el importe y la tienda de los avisos de tarjeta y banco (solo bancos coreanos). Requiere «Acceso a notificaciones»",
            ),
            checked = ui.reminderOn,
            onCheckedChange = { onToggleReminder() },
        )
        // 결제 알림을 켤 때 권한을 한 번 물었는데 건너뛴 사람을 위한 두 번째 문.
        // 이미 켰으면 안 보인다 — 할 일이 없는 줄은 두지 않는다.
        if (ui.reminderOn && !ui.overlayAllowed) {
            LinkRow(
                title = tr("결제 팝업 바로 띄우기", "Show payment popup instantly", "Mostrar el aviso de pago al instante"),
                sub = tr(
                    "«다른 앱 위에 표시»를 켜면 카드사 알림이 들어갈 때까지 기다리지 않고 바로 떠요",
                    "Turn on «Display over other apps» so it appears right away instead of waiting for the card alert",
                    "Activa «Mostrar sobre otras apps» para que aparezca al momento sin esperar al aviso de la tarjeta",
                ),
                onClick = onOpenOverlaySettings,
            )
        }
        Divider()
        LinkRow(title = tr("입력 화면 열어보기", "Open the entry screen", "Abrir la pantalla de entrada"), sub = null, onClick = onOpenInput)
        LinkRow(
            title = tr("시스템 알림 설정 열기", "Open system notification settings", "Abrir ajustes de notificaciones"),
            sub = tr("잠금화면에 내용이 안 보일 때", "If the lock screen hides the content", "Si la pantalla de bloqueo oculta el contenido"),
            onClick = onOpenNotificationSettings,
        )
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
    GroupTitle(tr("가정", "Household", "Hogar"))
    CardBox {
        if (ui.householdPaired) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = tr("가정 코드", "Household code", "Código de hogar"), color = HomePalette.Muted, fontSize = 11.sp)
                    Text(
                        text = ui.householdCode ?: tr("불러오는 중…", "Loading…", "Cargando…"),
                        color = HomePalette.Ink,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 3.sp,
                    )
                }
                Text(
                    text = tr("연결됨", "Linked", "Vinculado"),
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
            PillButton(text = tr("코드 공유하기", "Share code", "Compartir código"), onClick = onShare, enabled = ui.householdCode != null)
            Spacer(Modifier.height(6.dp))
            TextLink(tr("연결 해제", "Leave household", "Salir del hogar"), onLeave, color = HomePalette.Muted)
        } else {
            Text(
                text = tr(
                    "배우자와 가정 코드로 묶으면 공용 곳간이 생겨요",
                    "Link with your partner via a household code to get a shared wallet",
                    "Vincúlate con tu pareja mediante un código de hogar para tener una cartera compartida",
                ),
                color = HomePalette.Ink,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            HelperText(
                tr(
                    "한 사람이 코드를 만들어 보내고, 다른 사람이 그 코드를 넣으면 됩니다. 개인 곳간에는 영향이 없어요.",
                    "One person creates a code and sends it; the other enters it. Your personal wallet isn't affected.",
                    "Una persona crea un código y lo envía; la otra lo introduce. Tu cartera personal no se ve afectada.",
                ),
            )
            Spacer(Modifier.height(10.dp))
            PillButton(text = tr("코드 만들기", "Create code", "Crear código"), onClick = onCreate, enabled = !ui.householdBusy)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                MintField(
                    value = ui.householdJoinInput,
                    onValueChange = onJoinInputChange,
                    label = tr("받은 코드", "Received code", "Código recibido"),
                    onDone = {},
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                OutlinedPillButton(
                    text = tr("연결", "Join", "Unirse"),
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
    GroupTitle(tr("기록", "Logging", "Registro"))
    CardBox {
        LinkRow(
            title = tr("카테고리 칩", "Category chips", "Categorías"),
            sub = form.categoriesText.ifBlank { tr("기본 목록", "Default list", "Lista predeterminada") },
            trailing = if (open) tr("접기", "Close", "Cerrar") else tr("고치기", "Edit", "Editar"),
            onClick = { open = !open },
        )
        if (open) {
            Spacer(Modifier.height(4.dp))
            MintField(
                value = form.categoriesText,
                onValueChange = { onFormChange(form.copy(categoriesText = it)) },
                label = tr("쉼표로 구분", "Comma-separated", "Separadas por comas"),
                singleLine = false,
                onDone = onFieldDone,
            )
            HelperText(
                tr(
                    "기록할 때 뜨는 칩입니다. 적은 순서대로 나옵니다. 비우면 기본 목록으로 돌아갑니다.",
                    "Chips shown when logging, in the order written. Leave empty to restore the default list.",
                    "Opciones que aparecen al anotar, en el orden escrito. Déjalo vacío para volver a la lista predeterminada.",
                ),
            )
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
    onForgetName: (String) -> Unit,
) {
    var open: Boolean by rememberSaveable { mutableStateOf(false) }
    var blockedOpen: Boolean by rememberSaveable { mutableStateOf(false) }
    var namesOpen: Boolean by rememberSaveable { mutableStateOf(false) }
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
            text = tr("더보기", "More", "Más"),
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
            title = tr("수집함에서 안 보는 출처", "Hidden sources", "Fuentes ocultas"),
            sub = tr("결제 알림을 모을 때 건너뛰는 발신자·앱", "Senders and apps skipped when collecting payment alerts", "Remitentes y apps que se omiten al reunir avisos de pago"),
            trailing = if (blockedCount > 0) "$blockedCount" else tr("없음", "None", "Ninguna"),
            onClick = { blockedOpen = !blockedOpen },
        )
        if (blockedOpen) {
            if (blockedCount == 0) {
                HelperText(
                    tr(
                        "수집함 팝업에서 «이 발신자 안 보기»·«이 앱 안 보기»를 누르면 여기에 생깁니다.",
                        "Appears here when you tap «Hide this sender» or «Hide this app» in the inbox.",
                        "Aparece aquí al tocar «Ocultar remitente» u «Ocultar esta app» en la bandeja.",
                    ),
                )
            }
            for (sender in ui.blockedSenders) BlockedRow(label = sender) { onUnblockSender(sender) }
            for (packageName in ui.blockedPackages) BlockedRow(label = packageName) { onUnblockPackage(packageName) }
            Spacer(Modifier.height(6.dp))
        }
        Divider()
        LinkRow(
            title = tr("결제 알림 이름 바꿔 띄우기", "Renamed payment alerts", "Avisos de pago renombrados"),
            sub = tr(
                "수집함에서 고쳐 적은 이름을 다음 알림부터 대신 띄워요",
                "Names you corrected in the inbox are used for future alerts",
                "Los nombres que corregiste en la bandeja se usan en los próximos avisos",
            ),
            trailing = if (ui.nameMemories.isNotEmpty()) "${ui.nameMemories.size}" else tr("없음", "None", "Ninguno"),
            onClick = { namesOpen = !namesOpen },
        )
        if (namesOpen) {
            if (ui.nameMemories.isEmpty()) {
                HelperText(
                    tr(
                        "수집함에서 알림 이름을 고쳐 기록하면 여기에 생깁니다. 쿠팡 같은 쇼핑몰과 카드사 이름만 읽힌 결제는 기억하지 않아요.",
                        "Appears here when you correct an alert's name in the inbox. Payments that only show a store like Coupang or a card company aren't remembered.",
                        "Aparece aquí al corregir el nombre de un aviso en la bandeja. No se recuerdan los pagos que solo muestran una tienda como Coupang o la emisora de la tarjeta.",
                    ),
                )
            }
            for ((from, to) in ui.nameMemories) {
                BlockedRow(label = "$from → $to", action = tr("지우기", "Remove", "Borrar")) { onForgetName(from) }
            }
            Spacer(Modifier.height(6.dp))
        }
        Divider()
        LinkRow(
            title = tr("지출 내보내기 (CSV)", "Export spending (CSV)", "Exportar gastos (CSV)"),
            sub = tr(
                "적은 지출 전부를 «다운로드» 폴더에 표 파일로 저장해요",
                "Saves all your spending as a spreadsheet file in «Downloads»",
                "Guarda todos tus gastos como hoja de cálculo en «Descargas»",
            ),
            onClick = onExportExpenses,
        )
        Divider()
        LinkRow(
            title = tr("설정 백업 코드", "Settings backup code", "Código de copia de ajustes"),
            sub = tr("예산·이름·월급날을 코드로 옮겨요", "Moves budget, names and payday via a code", "Traslada presupuesto, nombres y día de cobro con un código"),
            trailing = if (backupOpen) tr("접기", "Close", "Cerrar") else tr("열기", "Open", "Abrir"),
            onClick = { backupOpen = !backupOpen },
        )
        if (backupOpen) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedPillButton(tr("내보내기", "Export", "Exportar"), onExport, modifier = Modifier.weight(1f))
                OutlinedPillButton(tr("불러오기", "Import", "Importar"), onImport, modifier = Modifier.weight(1f))
            }
            HelperText(
                tr(
                    "«내보내기»는 지금 설정을 코드로 만들어 클립보드에 복사합니다. 메모에 붙여 두면 새 기기에서 «불러오기»로 한 번에 되돌립니다.",
                    "«Export» turns your settings into a code and copies it. Keep it in a note and use «Import» on a new device to restore them.",
                    "«Exportar» convierte tus ajustes en un código y lo copia. Guárdalo en una nota y usa «Importar» en un dispositivo nuevo para recuperarlos.",
                ),
            )
            Spacer(Modifier.height(6.dp))
        }
        Divider()
        LinkRow(
            title = ui.accountEmail ?: tr("로그인 정보 없음", "Not signed in", "Sin sesión"),
            sub = tr("구글 계정", "Google account", "Cuenta de Google"),
            trailing = tr("로그아웃", "Sign out", "Cerrar sesión"),
            onClick = onSignOut,
        )
    }
}

/** 지울 수 있는 한 줄. 막아 둔 출처(«해제»)와 이름 기억(«지우기»)이 같이 쓴다. */
@Composable
private fun BlockedRow(label: String, action: String = tr("해제", "Unhide", "Mostrar"), onUnblock: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Text(text = label, color = HomePalette.Ink2, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(
            text = action,
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
