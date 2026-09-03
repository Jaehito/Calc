package com.calc.expense

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.firebase.auth.FirebaseAuth
import java.util.concurrent.Executors

/**
 * 설정 화면. 홈·통계·챌린지·내역과 같은 Compose 화면군으로 맞춰(뱅크샐러드 톤) [SettingsScreen] 을 그린다.
 *
 * 폼 상태는 [SettingsFormUi] 한 덩어리로 들고, 네트워크·SharedPreferences 를 만지는 부수효과는
 * 전부 이 Activity 의 메서드로 남는다 — Compose 쪽은 순수하게 그리기만 한다.
 */
class MainActivity : ComponentActivity() {

    private val io = Executors.newSingleThreadExecutor()

    private var form: SettingsFormUi by mutableStateOf(SettingsFormUi())
    private var ledgerText: String by mutableStateOf("")
    private var statusMessage: String? by mutableStateOf(null)
    private var statusIsError: Boolean by mutableStateOf(false)
    private var saving: Boolean by mutableStateOf(false)
    private var showStorageNotice: Boolean by mutableStateOf(false)
    private var notificationOn: Boolean by mutableStateOf(false)
    private var reminderOn: Boolean by mutableStateOf(false)
    private var householdPaired: Boolean by mutableStateOf(false)
    private var householdCode: String? by mutableStateOf(null)
    private var householdJoinInput: String by mutableStateOf("")
    private var householdBusy: Boolean by mutableStateOf(false)
    private var householdMessage: String? by mutableStateOf(null)
    private var householdMessageIsError: Boolean by mutableStateOf(false)
    private var backfillBusy: Boolean by mutableStateOf(false)
    private var backfillMessage: String? by mutableStateOf(null)
    private var backfillMessageIsError: Boolean by mutableStateOf(false)

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) enableNotification()
            else setStatus("알림 권한이 거부되었습니다. 설정에서 직접 허용해 주세요.", isError = true)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadIntoForm()

        setContent {
            SettingsScreen(
                ui = SettingsUi(
                    form = form,
                    ledgerText = ledgerText,
                    statusMessage = statusMessage,
                    statusIsError = statusIsError,
                    saving = saving,
                    showStorageNotice = showStorageNotice,
                    notificationOn = notificationOn,
                    reminderOn = reminderOn,
                    accountEmail = FirebaseAuth.getInstance().currentUser?.email,
                    householdPaired = householdPaired,
                    householdCode = householdCode,
                    householdJoinInput = householdJoinInput,
                    householdBusy = householdBusy,
                    householdMessage = householdMessage,
                    householdMessageIsError = householdMessageIsError,
                    backfillBusy = backfillBusy,
                    backfillMessage = backfillMessage,
                    backfillMessageIsError = backfillMessageIsError,
                ),
                onBack = { finish() },
                onFormChange = { form = it },
                onSaveAndVerify = { saveAndVerify() },
                onEnableNotification = { requestNotificationThenEnable() },
                onDisableNotification = { disableNotification() },
                onOpenNotificationSettings = { openNotificationSettings() },
                onOpenInput = {
                    startActivity(
                        Intent(this@MainActivity, QuickInputActivity::class.java)
                            .putExtra(QuickInputActivity.EXTRA_FORCE_INPUT, true),
                    )
                },
                onToggleReminder = { toggleReminder() },
                onOpenReminderAccessSettings = { openNotificationAccessSettings() },
                onExport = { exportSettings() },
                onImport = { importSettings() },
                onSignOut = { signOut() },
                onHouseholdJoinInputChange = { householdJoinInput = it },
                onCreateHousehold = { createHousehold() },
                onJoinHousehold = { joinHousehold() },
                onLeaveHousehold = { leaveHousehold() },
                onBackfill = { runBackfill() },
            )
        }
    }

    /** 노션 지출 전체를 Firestore로 1회 복사한다(3단계). 네트워크를 타므로 백그라운드에서. */
    private fun runBackfill() {
        backfillBusy = true
        backfillMessage = null
        val appContext: Context = applicationContext
        io.execute {
            val result: FirestoreBackfill.Result = FirestoreBackfill.run(appContext)
            runOnUiThread {
                backfillBusy = false
                if (result.ok) {
                    backfillMessage = "${result.attempted}건 복사를 시도했어요. 잠시 뒤 Firestore 콘솔에서 개수를 확인해 보세요."
                    backfillMessageIsError = false
                } else {
                    backfillMessage = result.message.ifBlank { "백필에 실패했어요" }
                    backfillMessageIsError = true
                }
            }
        }
    }

    /** 로그아웃하고 [LoginActivity] 로 돌아간다. 로컬 곳간·설정은 지우지 않는다 — 계정만 바뀐다. */
    private fun signOut() {
        FirebaseAuth.getInstance().signOut()
        startActivity(
            Intent(this, LoginActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
        finish()
    }

    override fun onResume() {
        super.onResume()
        refreshStorageNotice()
        republishNotification()
        refreshLedger()
        refreshReminderButton()
        notificationOn = NotificationState.isOn(this)
        resyncInBackground()
        refreshHousehold()
    }

    /**
     * 가정 연결 상태를 읽는다. 로컬 캐시([HouseholdStore])가 있으면 그걸로 끝 — 매번
     * Firestore 를 왕복하지 않는다. 캐시가 비어 있을 때만(재설치 등) 서버에서 한 번 채운다.
     */
    private fun refreshHousehold() {
        val uid: String = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val cached: String? = HouseholdStore.householdId(this)
        if (cached != null) {
            householdPaired = true
            return
        }
        HouseholdRepository.currentHouseholdId(uid) { id ->
            if (isFinishing || isDestroyed) return@currentHouseholdId
            if (id != null) {
                HouseholdStore.setHouseholdId(this, id)
                householdPaired = true
            }
        }
    }

    /** 새 가정을 만들고 배우자에게 알려줄 코드를 화면에 띄운다. */
    private fun createHousehold() {
        val uid: String = FirebaseAuth.getInstance().currentUser?.uid
            ?: return setHouseholdMessage("로그인 정보를 확인할 수 없습니다", isError = true)
        householdBusy = true
        HouseholdRepository.create(uid) { result ->
            householdBusy = false
            result
                .onSuccess { household ->
                    HouseholdStore.setHouseholdId(this, household.householdId)
                    householdPaired = true
                    householdCode = household.code
                    setHouseholdMessage("가정을 만들었어요. 아래 코드를 배우자에게 알려주세요.")
                }
                .onFailure { setHouseholdMessage("가정을 만들지 못했어요: ${it.message}", isError = true) }
        }
    }

    /** 배우자가 만든 코드로 가정에 들어간다. */
    private fun joinHousehold() {
        val uid: String = FirebaseAuth.getInstance().currentUser?.uid
            ?: return setHouseholdMessage("로그인 정보를 확인할 수 없습니다", isError = true)
        householdBusy = true
        HouseholdRepository.join(uid, householdJoinInput) { result ->
            householdBusy = false
            result
                .onSuccess { householdId ->
                    HouseholdStore.setHouseholdId(this, householdId)
                    householdPaired = true
                    householdJoinInput = ""
                    setHouseholdMessage("가정에 연결됐어요.")
                }
                .onFailure { setHouseholdMessage(it.message ?: "연결에 실패했어요", isError = true) }
        }
    }

    /** 가정 연결을 해제한다. 잘못 묶었을 때 되돌리는 용도 — 배우자 쪽 연결은 그대로 남는다. */
    private fun leaveHousehold() {
        val uid: String = FirebaseAuth.getInstance().currentUser?.uid ?: return
        HouseholdRepository.leave(uid) { result ->
            result
                .onSuccess {
                    HouseholdStore.setHouseholdId(this, null)
                    householdPaired = false
                    householdCode = null
                    setHouseholdMessage("연결을 해제했어요.")
                }
                .onFailure { setHouseholdMessage("해제하지 못했어요: ${it.message}", isError = true) }
        }
    }

    private fun setHouseholdMessage(message: String, isError: Boolean = false) {
        householdMessage = message
        householdMessageIsError = isError
    }

    override fun onDestroy() {
        io.shutdown()
        super.onDestroy()
    }

    /** 지금 설정을 코드로 만들어 클립보드에 올린다. 폼에 아직 저장 안 한 값까지 그대로 담는다. */
    private fun exportSettings() {
        val code: String = SettingsCodec.encode(currentForm())
        val clipboard = getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("expense-settings", code))
        setStatus("설정 코드를 클립보드에 복사했습니다. 메모 등에 붙여 보관하세요. 코드에는 토큰이 들어 있으니 남에게 주지 마세요.")
    }

    /** 클립보드의 코드를 읽어 설정을 복원한다. 코드가 아니면 그대로 두고 알린다. */
    private fun importSettings() {
        val clipboard = getSystemService(ClipboardManager::class.java)
        val clip: CharSequence? = clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text
        if (clip.isNullOrBlank()) {
            setStatus("클립보드가 비어 있습니다. 먼저 설정 코드를 복사해 주세요.", isError = true)
            return
        }
        val restored: Settings? = SettingsCodec.decode(clip.toString())
        if (restored == null) {
            setStatus("클립보드 내용이 설정 코드가 아닙니다. «내보내기»로 만든 코드를 복사해 주세요.", isError = true)
            return
        }
        SettingsStore.save(this, restored)
        loadIntoForm()
        setStatus("설정을 불러왔습니다. 위 값을 확인하고 «저장하고 연결 확인»을 눌러 주세요.")
    }

    /** «알림 접근» 권한이 이 앱에 허용돼 있는지. 리스너 서비스는 이게 있어야 동작한다. */
    private fun hasNotificationAccess(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)

    private fun refreshReminderButton() {
        reminderOn = ReminderState.isEnabled(this)
    }

    /**
     * 결제 리마인더를 켜고 끈다.
     *
     * 켤 때 «알림 접근» 권한이 없으면 먼저 그 설정으로 보낸다 — 권한 없이는 결제 알림을
     * 읽을 수 없다. 상시 알림 자체가 꺼져 있으면 그것부터 켜야 한다고 알린다.
     */
    private fun toggleReminder() {
        if (ReminderState.isEnabled(this)) {
            ReminderState.setEnabled(this, false)
            ReminderScheduler.cancel(this)
            refreshReminderButton()
            setStatus("결제 리마인더를 껐습니다.")
            return
        }

        if (!NotificationState.isOn(this)) {
            setStatus("먼저 위에서 «알림 켜기» 를 눌러 주세요. 리마인더도 그 알림을 씁니다.", isError = true)
            return
        }
        if (!hasNotificationAccess()) {
            setStatus(
                "결제 알림을 읽으려면 «알림 접근» 권한이 필요합니다. 아래 버튼으로 설정을 열어 " +
                    "«지출 기록 리마인더» 를 켠 뒤, 다시 «결제 리마인더 켜기» 를 눌러 주세요."
            )
            openNotificationAccessSettings()
            return
        }

        ReminderState.setEnabled(this, true)
        refreshReminderButton()
        setStatus(
            "결제 리마인더를 켰습니다.\n\n결제 알림이 온 뒤 10분 안에 기록이 없으면 한 번 알려줍니다. " +
                "금액은 읽지 않고, 밤 10시~아침 8시는 무음, 하루 3번까지만."
        )
    }

    private fun openNotificationAccessSettings() {
        val intent = Intent(AndroidSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        try {
            startActivity(intent)
        } catch (_: Exception) {
            setStatus("이 기기에서 알림 접근 설정을 열 수 없습니다.", isError = true)
        }
    }

    /**
     * 켜 둔 상태면 알림을 다시 띄운다.
     *
     * 앱을 업데이트해도 셰이드에 남아 있던 옛 알림은 그대로다. 그 안의 PendingIntent 는
     * 옛 버전을 가리켜, 눌러도 아무 일이 일어나지 않는다. 앱을 열 때마다 다시 띄워
     * 항상 지금 버전의 알림이 걸려 있게 한다.
     */
    private fun republishNotification() {
        if (!NotificationState.isOn(this)) return
        if (!SettingsStore.load(this).isComplete) return
        NotificationHelper.show(this)
    }

    private fun loadIntoForm() {
        val s: Settings = SettingsStore.load(this)
        form = SettingsFormUi(
            token = s.token,
            nameProp = s.nameProp,
            priceProp = s.priceProp,
            dateProp = s.dateProp,
            purseProp = s.purseProp,
            categoryProp = s.categoryProp,
            categoriesText = Categories.format(CategoryStore.load(this)),
            payDayText = s.payDay.toString(),
            personalName = s.personal.name,
            personalDatabaseId = s.personal.databaseId,
            personalBudgetText = budgetText(s.personal.monthlyBudget),
            sharedName = s.shared.name,
            sharedDatabaseId = s.shared.databaseId,
            sharedBudgetText = budgetText(s.shared.monthlyBudget),
        )
    }

    private fun budgetText(amount: Long): String = if (amount > 0L) amount.toString() else ""

    private fun currentForm(): Settings = Settings(
        token = form.token.trim(),
        nameProp = form.nameProp.trim(),
        priceProp = form.priceProp.trim(),
        dateProp = form.dateProp.trim(),
        purseProp = form.purseProp.trim(),
        categoryProp = form.categoryProp.trim(),
        payDay = Payday.normalize(form.payDayText.trim().toIntOrNull() ?: Payday.DEFAULT),
        personal = PurseSettings(
            databaseId = form.personalDatabaseId.trim(),
            monthlyBudget = readBudget(form.personalBudgetText),
            name = form.personalName.trim(),
        ),
        shared = PurseSettings(
            databaseId = form.sharedDatabaseId.trim(),
            monthlyBudget = readBudget(form.sharedBudgetText),
            name = form.sharedName.trim(),
        ),
    )

    /** "930000" 도 "93만" 도 받는다. 잠금화면 입력과 같은 규칙이라 따로 배울 게 없다. */
    private fun readBudget(raw: String): Long = ExpenseParser.parseAmount(raw.trim()) ?: 0L

    /** 곳간 현황을 다시 그린다. */
    private fun refreshLedger() {
        val snapshots: List<LedgerSnapshot> = Purse.entries.mapNotNull { Ledger.snapshot(this, it) }
        ledgerText = StatusText.overview(snapshots)
    }

    /**
     * 앱을 연 김에 Notion 을 기준으로 이번 달 캐시를 곳간마다 다시 맞춘다.
     * 잠금화면 기록은 로컬 사본만 보고 계산하므로, 다른 기기에서 고친 것은 여기서 들어온다.
     */
    private fun resyncInBackground() {
        val settings: Settings = SettingsStore.load(this)
        val purses: List<Purse> = Purse.entries.filter { settings.of(it).isActive }
        if (settings.token.isBlank() || purses.isEmpty()) return

        io.execute {
            val failures: List<String> = purses.mapNotNull { purse ->
                Ledger.resync(applicationContext, purse)
                    ?.let { "${settings.labelOf(purse)}: $it" }
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                refreshLedger()
                if (failures.isNotEmpty()) {
                    setStatus(
                        "Notion 대조에 실패해 로컬 기록으로 표시 중입니다.\n\n" +
                            failures.joinToString("\n"),
                        isError = true,
                    )
                }
            }
        }
    }

    private fun refreshStorageNotice() {
        showStorageNotice = !SettingsStore.usingEncryption
    }

    private fun saveAndVerify() {
        // 카테고리 칩 목록은 노션 연결과 무관하므로 완성도 검사 전에 먼저 저장한다.
        CategoryStore.save(this, Categories.parse(form.categoriesText))

        val formSettings: Settings = currentForm()
        if (!formSettings.isComplete) {
            setStatus(
                "토큰과 속성 이름을 채우고, 두 곳간 중 최소 한 곳에 DB를 연결하세요.",
                isError = true,
            )
            return
        }

        SettingsStore.save(this, formSettings)
        loadIntoForm() // 정규화된 DB ID를 화면에 반영
        refreshStorageNotice()

        val saved: Settings = SettingsStore.load(this)
        setStatus("확인 중…")
        saving = true

        io.execute {
            val results: List<Pair<Boolean, String>> = saved.linkedPurses.map { purse ->
                val target: NotionTarget = saved.target(purse)
                    ?: return@map false to "${saved.labelOf(purse)}: DB를 읽지 못했습니다"
                when (val outcome = NotionClient(target).verify()) {
                    is NotionClient.Outcome.Ok -> true to "${saved.labelOf(purse)}: ${outcome.detail}"
                    is NotionClient.Outcome.Err ->
                        false to "${saved.labelOf(purse)}\n${outcome.message}"
                }
            }

            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                saving = false

                val allOk: Boolean = results.all { it.first }
                val report: String = results.joinToString("\n\n") { it.second }
                setStatus(
                    if (allOk) "저장 완료. DB 연결 확인됨.\n\n$report"
                    else "저장은 됐지만 연결에 문제가 있습니다.\n\n$report",
                    isError = !allOk,
                )

                // 알림 액션이 연결된 곳간 수에 따라 달라지므로 다시 그린다
                if (NotificationHelper.isEnabled(this@MainActivity)) {
                    NotificationHelper.show(this@MainActivity)
                }
                refreshLedger()
                resyncInBackground()
            }
        }
    }

    private fun requestNotificationThenEnable() {
        if (!SettingsStore.load(this).isComplete) {
            setStatus("먼저 설정을 저장하고 연결을 확인하세요.", isError = true)
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted: Boolean = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        enableNotification()
    }

    private fun enableNotification() {
        NotificationState.setOn(this, true)
        NotificationHelper.show(this)
        WeeklyReviewScheduler.schedule(this)
        notificationOn = true

        if (NotificationHelper.isEnabled(this)) {
            val purses: List<Purse> = SettingsStore.load(this).linkedPurses
            val howTo: String =
                "알림 카드를 누르면 입력 화면이 바로 뜹니다. «커피 4500» 처럼 적으세요.\n" +
                    "엔터를 칠 때마다 한 건씩 들어가고 위의 숫자가 줄어듭니다.\n" +
                    if (purses.size > 1) "곳간은 입력 화면 위에서 고릅니다." else ""
            setStatus(
                "알림을 켰습니다.\n\n" + howTo + "\n\n" +
                    "실수로 지워도 다시 올라옵니다. 끄려면 아래 «알림 끄기»를 누르세요.\n" +
                    "잠금화면에 내용이 안 보이면 «잠금화면에 알림 내용 표시»를 켜주세요."
            )
        } else {
            setStatus("이 앱의 알림이 차단되어 있습니다. 아래 버튼으로 설정에서 허용해 주세요.", isError = true)
        }
    }

    private fun disableNotification() {
        // 먼저 꺼야 DismissReceiver 가 되살리지 않는다.
        NotificationState.setOn(this, false)
        NotificationHelper.hide(this)
        WeeklyReviewScheduler.cancel(this)
        notificationOn = false
        setStatus("알림을 껐습니다. 다시 켜기 전까지 잠금화면에 나오지 않습니다.")
    }

    private fun openNotificationSettings() {
        val intent = Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, packageName)
        try {
            startActivity(intent)
        } catch (_: Exception) {
            startActivity(
                Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.fromParts("package", packageName, null))
            )
        }
    }

    private fun setStatus(message: String, isError: Boolean = false) {
        statusMessage = message
        statusIsError = isError
    }
}
