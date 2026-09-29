package com.calc.expense

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 고정비 한 줄의 입력 상태. 금액은 타이핑 중이라 문자열로 들고 있는다. */
data class FixedCostRow(val name: String, val amountText: String)

/** 첫 시작 화면이 그리는 상태 한 벌. */
data class OnboardingUi(
    val step: OnboardingStep = OnboardingStep.BUDGET,
    val incomeText: String = "",
    val rows: List<FixedCostRow> = emptyList(),
    /** 금액 한 칸. 처음 화면이자 건너뛰기·직접 고치기가 도착하는 곳. */
    val budgetText: String = "",
    /** 금액 한 칸 아래에 «가정 코드가 있어요»를 보일지. 이미 묶였거나 계산하러 왔으면 숨긴다. */
    val canJoin: Boolean = false,
    val joinCode: String = "",
    val joinBusy: Boolean = false,
    /** 연결 결과 문구. 성공이면 무엇을 가져왔는지, 실패면 왜인지. */
    val joinMessage: String? = null,
    val joinFailed: Boolean = false,
    val joined: Boolean = false,
)

/**
 * 첫 시작 — 한 달에 쓸 금액을 정한다. 금액 한 칸이 기본이고, 월급에서 고정비를 빼 계산해 보는
 * 길과 배우자의 가정 코드로 묶는 길이 그 아래 링크로 있다. 끝에 잠금화면 알림을 한 번 묻는다.
 *
 * 나이·직업은 묻지 않는다. 사용자는 자기 월세를 이미 알고, 짐작한 숫자가 예산에 들어가면
 * 그 뒤 모든 계산이 그만큼 틀린다. 여기서 묻는 것은 **금액뿐**이고, 어느 단계에서든
 * 건너뛸 수 있다.
 *
 * 화면은 [OnboardingStep] 하나로 갈린다 — 단계마다 Activity 를 두면 뒤로가기가 꼬인다.
 */
@Composable
fun OnboardingScreen(
    ui: OnboardingUi,
    plan: FixedCostPlan,
    onIncomeChange: (String) -> Unit,
    onRowChange: (index: Int, row: FixedCostRow) -> Unit,
    onAddRow: () -> Unit,
    onRemoveRow: (Int) -> Unit,
    onBudgetChange: (String) -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    onEditManually: () -> Unit,
    onBackToFixed: () -> Unit,
    onFinish: () -> Unit,
    onOpenCalculator: () -> Unit,
    onOpenJoin: () -> Unit,
    onJoinCodeChange: (String) -> Unit,
    onJoin: () -> Unit,
    onLeaveJoin: () -> Unit,
    onEnableNotification: () -> Unit,
    onSkipNotification: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HomePalette.Ground)
            .padding(horizontal = 24.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(top = 40.dp),
        ) {
            when (ui.step) {
                OnboardingStep.INCOME -> IncomeStep(ui, onIncomeChange)
                OnboardingStep.FIXED -> FixedStep(ui, plan, onRowChange, onAddRow, onRemoveRow)
                OnboardingStep.RESULT -> ResultStep(plan)
                OnboardingStep.BUDGET -> BudgetStep(ui, onBudgetChange, onOpenCalculator, onOpenJoin)
                OnboardingStep.JOIN -> JoinStep(ui, onJoinCodeChange)
                OnboardingStep.NOTIFY -> NotifyStep()
            }
        }

        Actions(
            ui, plan, onNext, onSkip, onEditManually, onBackToFixed, onFinish,
            onJoin, onLeaveJoin, onEnableNotification, onSkipNotification,
        )
    }
}

// ── 단계별 본문 ────────────────────────────────────────────────────────────────

@Composable
private fun IncomeStep(ui: OnboardingUi, onIncomeChange: (String) -> Unit) {
    Question(tr("한 달에 얼마 버세요?", "How much do you earn a month?", "¿Cuánto ganas al mes?"))
    Hint(
        tr(
            "세금 떼고 통장에 들어오는 금액이요. 대충이어도 나중에 고칠 수 있어요.",
            "What reaches your account after tax. A rough figure is fine — you can change it later.",
            "Lo que llega a tu cuenta después de impuestos. Una cifra aproximada vale; puedes cambiarla luego.",
        ),
    )
    Spacer(Modifier.height(20.dp))
    AmountField(ui.incomeText, onIncomeChange, tr("월급 (예: 3000000 또는 300만)", "Income (e.g. 3000000)", "Sueldo (p. ej. 3000000)"))
}

@Composable
private fun FixedStep(
    ui: OnboardingUi,
    plan: FixedCostPlan,
    onRowChange: (Int, FixedCostRow) -> Unit,
    onAddRow: () -> Unit,
    onRemoveRow: (Int) -> Unit,
) {
    Question(tr("매달 그냥 나가는 돈", "Money that goes out every month", "Dinero que sale cada mes"))
    Hint(
        tr(
            "자동이체처럼 손 안 대도 빠져나가는 것만요. 모르는 칸은 비워 두면 돼요.",
            "Only things that leave on their own, like automatic transfers. Leave unknown rows empty to skip them.",
            "Solo lo que sale solo, como domiciliaciones. Deja vacías las filas que no sepas y se omitirán.",
        ),
    )
    Spacer(Modifier.height(16.dp))

    for ((index, row) in ui.rows.withIndex()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = row.name,
                onValueChange = { onRowChange(index, row.copy(name = it.take(FixedCosts.MAX_NAME_LENGTH))) },
                label = { Text(tr("이름", "Name", "Nombre")) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = mintFieldColors(),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                value = row.amountText,
                onValueChange = { onRowChange(index, row.copy(amountText = it)) },
                label = { Text(tr("금액", "Amount", "Importe")) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(14.dp),
                colors = mintFieldColors(),
                modifier = Modifier.weight(1.2f),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "✕",
                color = HomePalette.Muted,
                fontSize = 15.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .clickable { onRemoveRow(index) }
                    .padding(6.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
    }

    Text(
        text = tr("+ 항목 추가", "+ Add item", "+ Añadir concepto"),
        color = HomePalette.Accent,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onAddRow)
            .padding(vertical = 12.dp),
    )

    Spacer(Modifier.height(10.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = tr("고정비 합계", "Fixed costs total", "Total de gastos fijos"), color = HomePalette.Ink2, fontSize = 14.sp)
        Text(
            text = StatusText.won(plan.fixedTotal),
            color = HomePalette.Ink,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ResultStep(plan: FixedCostPlan) {
    if (plan.isOverIncome) {
        Question(tr("고정비가 월급보다 많아요", "Fixed costs exceed your income", "Los gastos fijos superan tu sueldo"))
        Hint(
            tr(
                "고정비 ${StatusText.won(plan.fixedTotal)}가 월급 ${StatusText.won(plan.monthlyIncome)}보다 많아요. " +
                    "금액을 다시 확인해 보고, " +
                    "맞다면 한 달 예산을 직접 정해 주세요.",
                "Fixed costs of ${StatusText.won(plan.fixedTotal)} exceed your income of ${StatusText.won(plan.monthlyIncome)}. " +
                    "Either an amount is wrong, or this month isn't something the app can calculate. " +
                    "Go back to fix it, or set the amount yourself.",
                "Los gastos fijos de ${StatusText.won(plan.fixedTotal)} superan tu sueldo de ${StatusText.won(plan.monthlyIncome)}. " +
                    "O algún importe está mal, o este mes la app no puede calcularlo. " +
                    "Vuelve atrás para corregirlo o fija el importe tú mismo.",
            ),
        )
        return
    }

    Question(tr("이만큼 쓸 수 있어요", "Here's what you can spend", "Esto es lo que puedes gastar"))
    Spacer(Modifier.height(18.dp))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(HomePalette.Card)
            .padding(20.dp),
    ) {
        Text(text = tr("한 달에 쓸 수 있는 돈", "Available per month", "Disponible al mes"), color = HomePalette.Ink2, fontSize = 13.sp)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = L10n.wonPrefix + StatusText.figure(plan.recommended),
                color = HomePalette.Accent,
                fontSize = 38.sp,
                fontWeight = FontWeight.Bold,
            )
            if (L10n.wonSuffix.isNotEmpty()) {
                Text(
                    text = L10n.wonSuffix,
                    color = HomePalette.Accent,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = tr("월급", "Income", "Sueldo") + " ${StatusText.figure(plan.monthlyIncome)} − " +
                tr("고정비", "fixed costs", "gastos fijos") + " ${StatusText.figure(plan.fixedTotal)}",
            color = HomePalette.Muted,
            fontSize = 12.sp,
        )
    }

    Spacer(Modifier.height(14.dp))
    Hint(
        tr(
            "이 금액으로 하루치와 등급을 계산해요. 설정에서 언제든 바꿀 수 있어요.",
            "This amount drives your savings, grades and garden. You can change it anytime in Settings.",
            "Este importe es la base de tu ahorro, notas y jardín. Puedes cambiarlo cuando quieras en Ajustes.",
        ),
    )
}

@Composable
private fun BudgetStep(
    ui: OnboardingUi,
    onBudgetChange: (String) -> Unit,
    onOpenCalculator: () -> Unit,
    onOpenJoin: () -> Unit,
) {
    Question(tr("한 달에 얼마 쓸 거예요?", "How much will you spend a month?", "¿Cuánto vas a gastar al mes?"))
    Hint(
        tr(
            "고정비를 빼고 실제로 쓸 수 있는 돈이요. 나중에 설정에서 언제든 바꿀 수 있어요.",
            "The money you can actually spend after fixed costs. You can change it anytime in Settings.",
            "El dinero que realmente puedes gastar tras los gastos fijos. Puedes cambiarlo cuando quieras en Ajustes.",
        ),
    )
    Spacer(Modifier.height(20.dp))
    AmountField(ui.budgetText, onBudgetChange, tr("한 달 예산 (예: 930000 또는 93만)", "Monthly budget (e.g. 930000)", "Presupuesto mensual (p. ej. 930000)"))
    Spacer(Modifier.height(16.dp))
    LinkRow(tr("월급·고정비로 계산해 볼래요", "Calculate from income and fixed costs", "Calcular con sueldo y gastos fijos"), onOpenCalculator)
    if (ui.canJoin) {
        Spacer(Modifier.height(8.dp))
        LinkRow(tr("배우자에게 받은 가정 코드가 있어요", "I have a household code from my partner", "Tengo un código de hogar de mi pareja"), onOpenJoin)
    }
}

@Composable
private fun JoinStep(ui: OnboardingUi, onJoinCodeChange: (String) -> Unit) {
    Question(tr("가정 코드를 넣어 주세요", "Enter the household code", "Introduce el código de hogar"))
    Hint(
        tr(
            "배우자가 설정의 ‘공용 지갑’에서 만든 ${HouseholdCode.LENGTH}자리 코드예요. 연결되면 공용 예산과 월급날을 같이 써요.",
            "The ${HouseholdCode.LENGTH}-character code your partner made in Settings › Household. Once linked, you share the shared wallet's budget and payday.",
            "El código de ${HouseholdCode.LENGTH} caracteres que tu pareja creó en Ajustes › Hogar. Al vincularos, compartís el presupuesto y el día de cobro de la cartera compartida.",
        ),
    )
    Spacer(Modifier.height(20.dp))
    OutlinedTextField(
        value = ui.joinCode,
        onValueChange = onJoinCodeChange,
        label = { Text(tr("가정 코드", "Household code", "Código de hogar")) },
        singleLine = true,
        enabled = !ui.joined,
        textStyle = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = 6.sp),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Characters,
            autoCorrectEnabled = false,
        ),
        shape = RoundedCornerShape(16.dp),
        colors = mintFieldColors(),
        modifier = Modifier.fillMaxWidth(),
    )
    val message: String? = ui.joinMessage
    if (message != null) {
        Spacer(Modifier.height(12.dp))
        Text(
            text = message,
            color = if (ui.joinFailed) HomePalette.Over else HomePalette.Accent,
            fontSize = 13.sp,
            lineHeight = 20.sp,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(if (ui.joinFailed) HomePalette.Over.copy(alpha = 0.08f) else HomePalette.Soft)
                .padding(12.dp),
        )
    }
}

/**
 * 잠금화면 알림을 켜기 전에 무엇이 뜨는지 보여준다. 숫자는 예시다 — 아직 아무것도 적지 않은
 * 사람에게 실제 값을 그리면 «0원»뿐이라 무엇을 켜는지 알 수 없다.
 */
@Composable
private fun NotifyStep() {
    Question(tr("잠금화면에서 바로 적어요", "Log right from the lock screen", "Anota desde la pantalla de bloqueo"))
    Hint(
        tr(
            "알림을 켜 두면 잠금화면에서 오늘 쓸 수 있는 돈을 보고, 눌러서 ‘커피 4500’처럼 적을 수 있어요.",
            "With the notification on, the lock screen shows what you can spend today; tap it and type e.g. ‘coffee 4500’.",
            "Con la notificación activada, la pantalla de bloqueo muestra lo que puedes gastar hoy; tócala y escribe p. ej. «café 4500».",
        ),
    )
    Spacer(Modifier.height(20.dp))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(LockPreviewGround)
            .padding(16.dp),
    ) {
        Text(text = tr("예시", "Example", "Ejemplo"), color = LockPreviewMuted, fontSize = 11.sp)
        Spacer(Modifier.height(8.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.10f))
                .padding(14.dp),
        ) {
            Text(text = tr("하루치 · 지금", "Haruchi · now", "Haruchi · ahora"), color = LockPreviewMuted, fontSize = 11.sp)
            Spacer(Modifier.height(4.dp))
            Text(text = tr("오늘 31,000원 남았어요", "₩31,000 left today", "Quedan ₩31,000 hoy"), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(text = tr("이번 주기 612,400원 남음", "₩612,400 left this cycle", "Quedan ₩612,400 este ciclo"), color = LockPreviewText, fontSize = 12.sp)
            Spacer(Modifier.height(10.dp))
            Text(
                text = tr("커피 4500", "coffee 4500", "café 4500"),
                color = LockPreviewMuted,
                fontSize = 13.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.12f))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
    }
}

/** 잠금화면 미리보기의 색. 폰 잠금화면은 앱 테마와 상관없이 어둡다. */
private val LockPreviewGround = Color(0xFF15201C)
private val LockPreviewText = Color(0xFFC8D6D0)
private val LockPreviewMuted = Color(0xFF98ABA4)

// ── 아래 고정 버튼 ─────────────────────────────────────────────────────────────

@Composable
private fun Actions(
    ui: OnboardingUi,
    plan: FixedCostPlan,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    onEditManually: () -> Unit,
    onBackToFixed: () -> Unit,
    onFinish: () -> Unit,
    onJoin: () -> Unit,
    onLeaveJoin: () -> Unit,
    onEnableNotification: () -> Unit,
    onSkipNotification: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
        when (ui.step) {
            OnboardingStep.INCOME -> {
                PrimaryButton(tr("다음", "Next", "Siguiente"), onNext, enabled = readAmount(ui.incomeText) > 0L)
                GhostButton(tr("나중에 할래요", "Maybe later", "Más tarde"), onSkip)
            }
            OnboardingStep.FIXED -> {
                // 고정비가 하나도 없어도 넘어갈 수 있다 — 그러면 월급이 그대로 예산이 된다.
                PrimaryButton(tr("다음", "Next", "Siguiente"), onNext, enabled = true)
                GhostButton(tr("나중에 할래요", "Maybe later", "Más tarde"), onSkip)
            }
            OnboardingStep.RESULT -> {
                if (plan.isOverIncome) {
                    PrimaryButton(tr("고정비 고치기", "Fix fixed costs", "Corregir gastos fijos"), onBackToFixed, enabled = true)
                    GhostButton(tr("금액 직접 정하기", "Set the amount myself", "Fijar el importe yo"), onEditManually)
                } else {
                    PrimaryButton(tr("이 금액으로 시작", "Start with this amount", "Empezar con este importe"), onFinish, enabled = true)
                    GhostButton(tr("직접 고치기", "Edit it myself", "Editarlo yo"), onEditManually)
                }
            }
            OnboardingStep.BUDGET -> {
                // 계산 경로와 가정 코드는 본문 링크로 있다. 여기 한 번 더 두면 같은 길이 둘이다.
                PrimaryButton(tr("다음", "Next", "Siguiente"), onFinish, enabled = readAmount(ui.budgetText) > 0L)
            }
            OnboardingStep.JOIN -> {
                if (ui.joined) {
                    PrimaryButton(tr("개인 예산 정하러 가기", "Set my personal budget", "Fijar mi presupuesto personal"), onLeaveJoin, enabled = true)
                } else {
                    PrimaryButton(
                        if (ui.joinBusy) tr("연결하는 중…", "Joining…", "Uniéndote…") else tr("연결하기", "Join", "Unirse"),
                        onJoin,
                        enabled = !ui.joinBusy && HouseholdCode.isValid(ui.joinCode),
                    )
                    GhostButton(tr("취소", "Cancel", "Cancelar"), onLeaveJoin)
                }
            }
            OnboardingStep.NOTIFY -> {
                PrimaryButton(tr("알림 켜기", "Turn on notification", "Activar notificación"), onEnableNotification, enabled = true)
                GhostButton(tr("나중에 할래요", "Maybe later", "Más tarde"), onSkipNotification)
            }
        }
    }
}

/** "3000000" 도 "300만" 도 받는다. 잠금화면 입력과 같은 규칙이라 따로 배울 게 없다. */
fun readAmount(raw: String): Long = ExpenseParser.parseAmount(raw.trim()) ?: 0L

// ── 조각들 ─────────────────────────────────────────────────────────────────────

@Composable
private fun Question(text: String) {
    Text(
        text = text,
        color = HomePalette.Ink,
        fontSize = 26.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 34.sp,
    )
}

@Composable
private fun Hint(text: String) {
    Spacer(Modifier.height(10.dp))
    Text(text = text, color = HomePalette.Ink2, fontSize = 13.sp, lineHeight = 20.sp)
}

@Composable
private fun AmountField(value: String, onValueChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        shape = RoundedCornerShape(16.dp),
        colors = mintFieldColors(),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit, enabled: Boolean) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = HomePalette.AccentBright,
            disabledContainerColor = HomePalette.Line,
        ),
    ) {
        Text(
            text = text,
            color = if (enabled) Color.White else HomePalette.Muted,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** 본문 아래 다른 길로 가는 줄. 버튼보다 한 단계 약하게, 하지만 눌리는 것처럼 보이게. */
@Composable
private fun LinkRow(text: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(HomePalette.Card)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(
            text = text,
            color = HomePalette.Accent,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        Chevron()
    }
}

@Composable
private fun GhostButton(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        color = HomePalette.Ink2,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
    )
}
