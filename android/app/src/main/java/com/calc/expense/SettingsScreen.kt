package com.calc.expense

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 설정 폼의 입력칸 값. 저장·검증 로직은 Activity 쪽(순수 상태가 아니라서)에 남는다. */
data class SettingsFormUi(
    val token: String = "",
    val nameProp: String = "",
    val priceProp: String = "",
    val dateProp: String = "",
    val purseProp: String = "",
    val categoryProp: String = "",
    val categoriesText: String = "",
    val payDayText: String = "",
    val personalName: String = "",
    val personalDatabaseId: String = "",
    val personalBudgetText: String = "",
    val sharedName: String = "",
    val sharedDatabaseId: String = "",
    val sharedBudgetText: String = "",
)

/** 설정 화면이 그리는 상태 한 벌. */
data class SettingsUi(
    val form: SettingsFormUi = SettingsFormUi(),
    val ledgerText: String = "",
    val statusMessage: String? = null,
    val statusIsError: Boolean = false,
    val saving: Boolean = false,
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
    val backfillBusy: Boolean = false,
    val backfillMessage: String? = null,
    val backfillMessageIsError: Boolean = false,
    val firestoreReadEnabled: Boolean = false,
)

/**
 * 설정 화면. 홈·통계·챌린지·내역과 같은 민트 카드 화면군으로 맞춘다.
 *
 * 폼은 한 데이터클래스([SettingsFormUi])로 오르내린다 — 입력칸이 열세 개라 필드마다
 * 콜백을 따로 두면 호출부가 장황해진다. 저장·검증·알림 토글 같은 부수효과는 전부
 * Activity 쪽 콜백으로 위임한다(네트워크·SharedPreferences 는 Compose 상태가 아니다).
 */
@Composable
fun SettingsScreen(
    ui: SettingsUi,
    onBack: () -> Unit,
    onFormChange: (SettingsFormUi) -> Unit,
    onSaveAndVerify: () -> Unit,
    onEnableNotification: () -> Unit,
    onDisableNotification: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenInput: () -> Unit,
    onToggleReminder: () -> Unit,
    onOpenReminderAccessSettings: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onSignOut: () -> Unit,
    onHouseholdJoinInputChange: (String) -> Unit,
    onCreateHousehold: () -> Unit,
    onJoinHousehold: () -> Unit,
    onLeaveHousehold: () -> Unit,
    onBackfill: () -> Unit,
    onToggleFirestoreRead: () -> Unit,
) {
    val form: SettingsFormUi = ui.form

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
            Text(text = "설정", color = HomePalette.Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(14.dp))

        if (ui.accountEmail != null) {
            CardBox {
                SectionTitle("계정")
                Spacer(Modifier.height(8.dp))
                Text(text = ui.accountEmail, color = HomePalette.Ink2, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                OutlinedPillButton("로그아웃", onSignOut, modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(12.dp))
        }

        CardBox {
            SectionTitle("내 곳간")
            Spacer(Modifier.height(8.dp))
            Text(text = ui.ledgerText, color = HomePalette.Ink2, fontSize = 13.sp, lineHeight = 19.sp)
        }
        Spacer(Modifier.height(12.dp))

        CardBox {
            SectionTitle("Notion 연결")
            HelperText("인테그레이션 시크릿 하나로 두 DB를 씁니다. 각 DB 페이지의 점 세 개 메뉴에서 연결(Connections)에 인테그레이션을 먼저 추가해야 합니다.")
            Spacer(Modifier.height(12.dp))

            var tokenVisible: Boolean by remember { mutableStateOf(false) }
            MintField(
                value = form.token,
                onValueChange = { onFormChange(form.copy(token = it)) },
                label = "인테그레이션 시크릿",
                visualTransformation = if (tokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingText = if (tokenVisible) "숨김" else "표시",
                onTrailingClick = { tokenVisible = !tokenVisible },
            )

            Spacer(Modifier.height(18.dp))
            SubTitle("DB 속성 이름")
            Spacer(Modifier.height(10.dp))
            MintField(form.nameProp, { onFormChange(form.copy(nameProp = it)) }, "이름 속성 (title 타입)")
            Spacer(Modifier.height(10.dp))
            MintField(form.priceProp, { onFormChange(form.copy(priceProp = it)) }, "금액 속성 (number 타입)")
            Spacer(Modifier.height(10.dp))
            MintField(form.dateProp, { onFormChange(form.copy(dateProp = it)) }, "날짜 속성 (date 타입)")
            Spacer(Modifier.height(10.dp))
            MintField(form.purseProp, { onFormChange(form.copy(purseProp = it)) }, "곳간 속성 (select 타입)")
            Spacer(Modifier.height(8.dp))
            HelperText("개인·공용을 한 DB에서 쓰려면 아래 두 곳간에 같은 DB를 넣고, Notion에 «개인»·«공용» 옵션을 가진 select 속성을 만드세요. DB를 따로 쓸 거면 이 칸은 비워도 됩니다.")
            Spacer(Modifier.height(10.dp))
            MintField(form.categoryProp, { onFormChange(form.copy(categoryProp = it)) }, "카테고리 속성 (통계용 select, 선택)")
            Spacer(Modifier.height(8.dp))
            HelperText("통계 탭의 카테고리별 막대가 이 select 속성을 읽습니다. 비워 두면 카테고리 통계는 표시되지 않습니다.")
        }
        Spacer(Modifier.height(12.dp))

        CardBox {
            SectionTitle("카테고리 칩 목록")
            Spacer(Modifier.height(10.dp))
            MintField(
                value = form.categoriesText,
                onValueChange = { onFormChange(form.copy(categoriesText = it)) },
                label = "쉼표로 구분",
                singleLine = false,
                maxLines = 2,
            )
            Spacer(Modifier.height(8.dp))
            HelperText("기록할 때 뜨는 카테고리 칩입니다. 쉼표로 구분해 순서대로 나옵니다. 비워 저장하면 기본 목록으로 돌아갑니다.")
        }
        Spacer(Modifier.height(12.dp))

        CardBox {
            SectionTitle("예산 주기")
            HelperText("월급날을 경계로 예산이 리셋됩니다. 25일이면 25일부터 다음 달 24일까지가 한 주기이고, 그 마지막 날이 «목표일»이 됩니다. 달력 1일 기준으로 쓰려면 1을 넣으세요.")
            Spacer(Modifier.height(10.dp))
            MintField(
                value = form.payDayText,
                onValueChange = { onFormChange(form.copy(payDayText = it)) },
                label = "월급날 (1~31)",
                keyboardType = KeyboardType.Number,
            )
        }
        Spacer(Modifier.height(12.dp))

        CardBox {
            SectionTitle("개인 곳간")
            HelperText("내 용돈. 이름은 자유롭게 정하세요 — 알림 버튼과 현황에 그대로 나옵니다. 예산을 주기 일수로 나눈 값이 하루치이고, 아낀 만큼 곳간에 쌓입니다. 처음에는 지난달 실제 지출보다 조금 넉넉하게 잡으세요.")
            Spacer(Modifier.height(10.dp))
            MintField(
                value = form.personalName,
                onValueChange = { onFormChange(form.copy(personalName = it.take(Purse.MAX_NAME_LENGTH))) },
                label = "이름 (비우면 «개인»)",
            )
            Spacer(Modifier.height(10.dp))
            MintField(form.personalDatabaseId, { onFormChange(form.copy(personalDatabaseId = it)) }, "DB ID 또는 DB URL 통째로")
            Spacer(Modifier.height(10.dp))
            MintField(form.personalBudgetText, { onFormChange(form.copy(personalBudgetText = it)) }, "월 예산 (예: 930000 또는 93만)")
        }
        Spacer(Modifier.height(12.dp))

        CardBox {
            SectionTitle("공용 곳간")
            HelperText("같이 쓰는 생활비. 아직 공용 계좌가 없으면 DB를 비워 두세요 — 비어 있으면 알림 버튼이 하나만 나옵니다. 나중에 여기만 채우면 됩니다.")
            Spacer(Modifier.height(10.dp))
            MintField(
                value = form.sharedName,
                onValueChange = { onFormChange(form.copy(sharedName = it.take(Purse.MAX_NAME_LENGTH))) },
                label = "이름 (비우면 «공용»)",
            )
            Spacer(Modifier.height(10.dp))
            MintField(form.sharedDatabaseId, { onFormChange(form.copy(sharedDatabaseId = it)) }, "DB ID 또는 DB URL 통째로")
            Spacer(Modifier.height(10.dp))
            MintField(form.sharedBudgetText, { onFormChange(form.copy(sharedBudgetText = it)) }, "월 예산 (예: 930000 또는 93만)")
        }
        Spacer(Modifier.height(12.dp))

        CardBox {
            SectionTitle("공용 곳간 동기화 (베타)")
            HelperText("공용 곳간 지출을 배우자와 함께 보려면 가정 코드로 한 번만 묶으세요. 노션 기록에는 영향이 없습니다.")
            Spacer(Modifier.height(10.dp))
            if (ui.householdPaired) {
                Text(
                    text = "가정으로 연결됐어요",
                    color = HomePalette.Accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedPillButton("연결 해제", onLeaveHousehold, modifier = Modifier.fillMaxWidth())
            } else {
                PillButton(text = "코드 만들기", onClick = onCreateHousehold, enabled = !ui.householdBusy)
                Spacer(Modifier.height(10.dp))
                MintField(
                    value = ui.householdJoinInput,
                    onValueChange = onHouseholdJoinInputChange,
                    label = "배우자가 준 코드 입력",
                )
                Spacer(Modifier.height(8.dp))
                PillButton(
                    text = "코드로 연결",
                    onClick = onJoinHousehold,
                    enabled = !ui.householdBusy && ui.householdJoinInput.isNotBlank(),
                )
            }
            if (ui.householdCode != null) {
                Spacer(Modifier.height(10.dp))
                WarningBanner("이 코드를 배우자에게 알려주세요: ${ui.householdCode}")
            }
            if (ui.householdMessage != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = ui.householdMessage,
                    color = if (ui.householdMessageIsError) HomePalette.Over else HomePalette.Accent,
                    fontSize = 12.sp,
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        CardBox {
            SectionTitle("노션 데이터 백필 (베타)")
            HelperText("지금까지 노션에 적힌 지출을 Firestore로 한 번 복사합니다. 여러 번 눌러도 안전합니다(같은 항목은 덮어쓸 뿐 중복되지 않음). 노션 기록은 그대로 남고 지워지지 않습니다.")
            Spacer(Modifier.height(10.dp))
            PillButton(
                text = if (ui.backfillBusy) "복사 중…" else "노션 → Firestore 백필 실행",
                onClick = onBackfill,
                enabled = !ui.backfillBusy,
            )
            if (ui.backfillMessage != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = ui.backfillMessage,
                    color = if (ui.backfillMessageIsError) HomePalette.Over else HomePalette.Accent,
                    fontSize = 12.sp,
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        CardBox {
            SectionTitle("읽기 전환 (실험적)")
            HelperText("켜면 홈 화면 숫자·통계·내역을 노션 대신 Firestore에서 읽습니다. 실패하면 그때그때 자동으로 노션으로 돌아가지만, Firestore에 데이터가 비어 있는데 읽기 자체는 성공하는 경우(백필 전, 또는 아직 규칙이 안 걸린 경우)는 걸러내지 못합니다 — 그러면 지출이 실제보다 적게(0에 가깝게) 보일 수 있습니다. 위 백필을 먼저 실행하고, Firestore 콘솔에서 데이터가 보이는 걸 확인한 뒤에 켜세요.")
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (ui.firestoreReadEnabled) HomePalette.AccentBright else HomePalette.Muted),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (ui.firestoreReadEnabled) "켜짐 — Firestore에서 읽는 중" else "꺼짐 — 노션에서 읽는 중(기본)",
                    color = if (ui.firestoreReadEnabled) HomePalette.Accent else HomePalette.Ink2,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(10.dp))
            if (ui.firestoreReadEnabled) {
                OutlinedPillButton("끄고 노션으로 돌아가기", onToggleFirestoreRead, modifier = Modifier.fillMaxWidth())
            } else {
                PillButton(text = "Firestore 읽기 켜기", onClick = onToggleFirestoreRead)
            }
        }

        Spacer(Modifier.height(16.dp))
        PillButton(
            text = if (ui.saving) "확인 중…" else "저장하고 연결 확인",
            onClick = onSaveAndVerify,
            enabled = !ui.saving,
        )

        Spacer(Modifier.height(8.dp))
        Row {
            OutlinedPillButton("설정 내보내기", onExport, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            OutlinedPillButton("설정 불러오기", onImport, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(6.dp))
        HelperText("«내보내기»는 지금 설정을 코드로 만들어 클립보드에 복사합니다. 그 코드를 메모에 붙여 두면, 새 기기·새 설치에서 «불러오기»로 한 번에 복원됩니다. 코드에는 토큰이 들어 있으니 남에게 주지 마세요.")

        if (ui.showStorageNotice) {
            Spacer(Modifier.height(10.dp))
            WarningBanner("주의: 이 기기에서 암호화 저장소를 열지 못해 토큰이 평문으로 저장됩니다. 앱 전용 영역이라 다른 앱은 접근할 수 없지만, 루팅된 기기에서는 노출될 수 있습니다.")
        }

        if (ui.statusMessage != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = ui.statusMessage,
                color = if (ui.statusIsError) HomePalette.Over else HomePalette.Accent,
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )
        }

        Spacer(Modifier.height(20.dp))
        CardBox {
            SectionTitle("잠금화면 알림")
            Spacer(Modifier.height(10.dp))
            PillButton(
                text = if (ui.notificationOn) "알림 끄기" else "알림 켜기",
                onClick = if (ui.notificationOn) onDisableNotification else onEnableNotification,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedPillButton("입력 화면 열어보기", onOpenInput, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            TextLink("시스템 알림 설정 열기", onOpenNotificationSettings)
        }
        Spacer(Modifier.height(12.dp))

        CardBox {
            SectionTitle("결제 리마인더")
            HelperText("결제 알림이 온 뒤 10분 안에 기록이 없으면 «적었어요?» 를 한 번 알려줍니다. 금액은 읽지 않고, 밤 10시~아침 8시는 무음, 하루 3번까지만. 이 기능은 «알림 접근» 권한이 필요합니다.")
            Spacer(Modifier.height(10.dp))
            PillButton(
                text = if (ui.reminderOn) "결제 리마인더 끄기" else "결제 리마인더 켜기",
                onClick = onToggleReminder,
            )
            Spacer(Modifier.height(8.dp))
            TextLink("알림 접근 권한 설정 열기", onOpenReminderAccessSettings)
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text = text, color = HomePalette.Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun SubTitle(text: String) {
    Text(text = text, color = HomePalette.Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun HelperText(text: String) {
    Spacer(Modifier.height(6.dp))
    Text(text = text, color = HomePalette.Muted, fontSize = 12.sp, lineHeight = 17.sp)
}

@Composable
private fun MintField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    singleLine: Boolean = true,
    maxLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingText: String? = null,
    onTrailingClick: (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = singleLine,
        maxLines = maxLines,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = visualTransformation,
        trailingIcon = if (trailingText != null && onTrailingClick != null) {
            {
                Text(
                    text = trailingText,
                    color = HomePalette.Accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clickable(onClick = onTrailingClick)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        } else {
            null
        },
        shape = RoundedCornerShape(14.dp),
        colors = mintFieldColors(),
        modifier = Modifier.fillMaxWidth(),
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
private fun OutlinedPillButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = HomePalette.Ink),
        modifier = modifier.height(48.dp),
    ) {
        Text(text = text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun TextLink(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        color = HomePalette.Accent,
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
            .padding(18.dp),
    ) {
        content()
    }
}
