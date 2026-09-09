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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 고정비 한 줄의 입력 상태. 금액은 타이핑 중이라 문자열로 들고 있는다. */
data class FixedCostRow(val name: String, val amountText: String)

/** 첫 시작 화면이 그리는 상태 한 벌. */
data class OnboardingUi(
    val step: OnboardingStep = OnboardingStep.INCOME,
    val incomeText: String = "",
    val rows: List<FixedCostRow> = emptyList(),
    /** 건너뛰기·직접 고치기에서 쓰는 한 칸. */
    val budgetText: String = "",
)

/**
 * 첫 시작 — 월급에서 고정비를 빼 **이번 달 챌린지 금액**을 정한다.
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
                OnboardingStep.BUDGET -> BudgetStep(ui, onBudgetChange)
            }
        }

        Actions(ui, plan, onNext, onSkip, onEditManually, onBackToFixed, onFinish)
    }
}

// ── 단계별 본문 ────────────────────────────────────────────────────────────────

@Composable
private fun IncomeStep(ui: OnboardingUi, onIncomeChange: (String) -> Unit) {
    Question("한 달에 얼마 버세요?")
    Hint("세금 떼고 통장에 들어오는 금액이요. 대충이어도 나중에 고칠 수 있어요.")
    Spacer(Modifier.height(20.dp))
    AmountField(ui.incomeText, onIncomeChange, "월급 (예: 3000000 또는 300만)")
}

@Composable
private fun FixedStep(
    ui: OnboardingUi,
    plan: FixedCostPlan,
    onRowChange: (Int, FixedCostRow) -> Unit,
    onAddRow: () -> Unit,
    onRemoveRow: (Int) -> Unit,
) {
    Question("매달 그냥 나가는 돈")
    Hint("자동이체처럼 손 안 대도 빠져나가는 것만요. 모르는 칸은 비워 두면 빠집니다.")
    Spacer(Modifier.height(16.dp))

    for ((index, row) in ui.rows.withIndex()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = row.name,
                onValueChange = { onRowChange(index, row.copy(name = it.take(FixedCosts.MAX_NAME_LENGTH))) },
                label = { Text("이름") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = mintFieldColors(),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                value = row.amountText,
                onValueChange = { onRowChange(index, row.copy(amountText = it)) },
                label = { Text("금액") },
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
        text = "+ 항목 추가",
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
        Text(text = "고정비 합계", color = HomePalette.Ink2, fontSize = 14.sp)
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
        Question("고정비가 월급보다 많아요")
        Hint(
            "고정비 ${StatusText.won(plan.fixedTotal)}가 월급 ${StatusText.won(plan.monthlyIncome)}를 넘습니다. " +
                "금액을 잘못 적었거나, 이번 달은 앱이 계산해 줄 수 있는 상황이 아닙니다. " +
                "뒤로 가서 고치거나 금액을 직접 정해 주세요.",
        )
        return
    }

    Question("이만큼 쓸 수 있어요")
    Spacer(Modifier.height(18.dp))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(HomePalette.Card)
            .padding(20.dp),
    ) {
        Text(text = "이번 달 챌린지 금액", color = HomePalette.Ink2, fontSize = 13.sp)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = StatusText.figure(plan.recommended),
                color = HomePalette.Accent,
                fontSize = 38.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "원",
                color = HomePalette.Accent,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = "월급 ${StatusText.figure(plan.monthlyIncome)} − 고정비 ${StatusText.figure(plan.fixedTotal)}",
            color = HomePalette.Muted,
            fontSize = 12.sp,
        )
    }

    Spacer(Modifier.height(14.dp))
    Hint("이 금액이 곳간·등급·챌린지 목표의 기준이 됩니다. 나중에 설정에서 언제든 바꿀 수 있어요.")
}

@Composable
private fun BudgetStep(ui: OnboardingUi, onBudgetChange: (String) -> Unit) {
    Question("한 달에 얼마 쓸 거예요?")
    Hint("고정비를 빼고 남은, 실제로 쓸 수 있는 돈이요. 나중에 «고정비로 계산하기»로 다시 정할 수 있어요.")
    Spacer(Modifier.height(20.dp))
    AmountField(ui.budgetText, onBudgetChange, "한 달 예산 (예: 2000000 또는 200만)")
}

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
) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
        when (ui.step) {
            OnboardingStep.INCOME -> {
                PrimaryButton("다음", onNext, enabled = readAmount(ui.incomeText) > 0L)
                GhostButton("나중에 할래요", onSkip)
            }
            OnboardingStep.FIXED -> {
                // 고정비가 하나도 없어도 넘어갈 수 있다 — 그러면 월급이 그대로 예산이 된다.
                PrimaryButton("다음", onNext, enabled = true)
                GhostButton("나중에 할래요", onSkip)
            }
            OnboardingStep.RESULT -> {
                if (plan.isOverIncome) {
                    PrimaryButton("고정비 고치기", onBackToFixed, enabled = true)
                    GhostButton("금액 직접 정하기", onEditManually)
                } else {
                    PrimaryButton("이 금액으로 시작", onFinish, enabled = true)
                    GhostButton("직접 고치기", onEditManually)
                }
            }
            OnboardingStep.BUDGET -> {
                PrimaryButton("시작하기", onFinish, enabled = readAmount(ui.budgetText) > 0L)
                GhostButton("고정비로 계산하기", onBackToFixed)
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
