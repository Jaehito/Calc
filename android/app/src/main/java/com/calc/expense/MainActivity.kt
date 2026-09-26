package com.calc.expense

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.firebase.auth.FirebaseAuth
import java.util.concurrent.Executors

/**
 * 설정 화면. 홈·통계·도감·내역과 같은 Compose 화면군으로 맞춰(뱅크샐러드 톤) [SettingsScreen] 을 그린다.
 *
 * 폼 상태는 [SettingsFormUi] 한 덩어리로 들고, 네트워크·SharedPreferences 를 만지는 부수효과는
 * 전부 이 Activity 의 메서드로 남는다 — Compose 쪽은 순수하게 그리기만 한다.
 */
class MainActivity : ComponentActivity() {

    private val io = Executors.newSingleThreadExecutor()

    private var form: SettingsFormUi by mutableStateOf(SettingsFormUi())
    private var toast: String? by mutableStateOf(null)
    private var toastIsError: Boolean by mutableStateOf(false)
    private var toastId: Int by mutableStateOf(0)
    private var savedCount: Int by mutableStateOf(0)
    private var showStorageNotice: Boolean by mutableStateOf(false)
    private var notificationOn: Boolean by mutableStateOf(false)
    private var reminderOn: Boolean by mutableStateOf(false)
    private var householdPaired: Boolean by mutableStateOf(false)
    private var householdCode: String? by mutableStateOf(null)
    private var householdJoinInput: String by mutableStateOf("")
    private var householdBusy: Boolean by mutableStateOf(false)
    private var householdMessage: String? by mutableStateOf(null)
    private var householdMessageIsError: Boolean by mutableStateOf(false)
    private var blockedSenders: List<String> by mutableStateOf(emptyList())
    private var blockedPackages: List<String> by mutableStateOf(emptyList())
    private var nameMemories: List<Pair<String, String>> by mutableStateOf(emptyList())

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
                    toast = toast,
                    toastIsError = toastIsError,
                    toastId = toastId,
                    savedCount = savedCount,
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
                    fixedTotal = fixedTotal,
                    blockedSenders = blockedSenders,
                    blockedPackages = blockedPackages,
                    nameMemories = nameMemories,
                ),
                onBack = { finish() },
                onFormChange = { form = it },
                onFieldDone = { autosave() },
                onToggleNotification = { on -> if (on) requestNotificationThenEnable() else disableNotification() },
                onOpenNotificationSettings = { openNotificationSettings() },
                onOpenInput = {
                    startActivity(
                        Intent(this@MainActivity, QuickInputActivity::class.java),
                    )
                },
                onToggleReminder = { toggleReminder() },
                onExport = { exportSettings() },
                onImport = { importSettings() },
                onExportExpenses = { exportExpenses() },
                onSignOut = { signOut() },
                onHouseholdJoinInputChange = { householdJoinInput = it },
                onCreateHousehold = { createHousehold() },
                onJoinHousehold = { joinHousehold() },
                onLeaveHousehold = { leaveHousehold() },
                onShareHouseholdCode = { shareHouseholdCode() },
                onOpenFixedCosts = {
                    autosave()
                    startActivity(Intent(this@MainActivity, OnboardingActivity::class.java))
                },
                onUnblockSender = { sender ->
                    PaymentBlocklist.unblockSender(this, sender)
                    refreshBlocklist()
                },
                onUnblockPackage = { packageName ->
                    PaymentBlocklist.unblockPackage(this, packageName)
                    refreshBlocklist()
                },
                onForgetName = { key ->
                    NameMemoryStore.forget(this, key)
                    refreshBlocklist()
                    setStatus("지웠어요. 다음 알림부터 원래 이름으로 떠요.")
                },
                onToastShown = { toast = null },
            )
        }
    }

    /** 이번 달 고정비 합계. 설정 카드의 문구가 이 값으로 갈린다. */
    private var fixedTotal: Long by mutableStateOf(0L)

    private fun refreshBlocklist() {
        blockedSenders = PaymentBlocklist.blockedSenders(this).sorted()
        blockedPackages = PaymentBlocklist.blockedPackages(this).sorted()
        // 최근에 고친 것이 위로 온다(기억은 오래된 것부터 쌓인다).
        nameMemories = NameMemoryStore.load(this).toList().asReversed()
    }

    /**
     * 로그아웃하고 [LoginActivity] 로 돌아간다.
     *
     * 여기서는 아무것도 지우지 않는다 — 같은 계정으로 다시 들어올 수도 있기 때문이다.
     * 비우는 판단은 **다시 로그인할 때** [AccountScope] 가 uid 를 비교해서 한다.
     */
    private fun signOut() {
        autosave()
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
        refreshReminderButton()
        notificationOn = NotificationState.isOn(this)
        fixedTotal = FixedCostStore.load(this).fixedTotal
        refreshBlocklist()
        resyncInBackground()
        refreshHousehold()
    }

    /** 칸을 떠나지 않고 화면을 나가도(뒤로가기·홈 버튼) 적은 값이 남게 한다. */
    override fun onPause() {
        autosave()
        super.onPause()
    }

    /**
     * 가정 연결 상태를 읽는다. 로컬 캐시([HouseholdStore])가 있으면 그걸로 끝 — 매번
     * Firestore 를 왕복하지 않는다. 캐시가 비어 있을 때만(재설치 등) 서버에서 한 번 채운다.
     *
     * 묶여 있으면 코드(다시 보내 주려고)와 같이 쓰는 설정(배우자가 바꿨을 수 있어서)도 받아 온다.
     */
    private fun refreshHousehold() {
        val uid: String = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val cached: String? = HouseholdStore.householdId(this)
        if (cached != null) {
            onPaired(cached)
            return
        }
        HouseholdRepository.currentHouseholdId(uid) { id ->
            if (isFinishing || isDestroyed) return@currentHouseholdId
            if (id != null) {
                HouseholdStore.setHouseholdId(this, id)
                onPaired(id)
            }
        }
    }

    private fun onPaired(householdId: String) {
        householdPaired = true
        householdCode = HouseholdStore.code(this)
        if (householdCode == null) {
            HouseholdRepository.code(householdId) { code ->
                if (isFinishing || isDestroyed || code == null) return@code
                HouseholdStore.setCode(this, code)
                householdCode = code
            }
        }
        pullHouseholdSettings()
    }

    /** 배우자가 바꾼 공용 예산·월급날을 받아 칸에 다시 채운다. 이 폰이 먼저면 가정에 올린다. */
    private fun pullHouseholdSettings() {
        HouseholdSync.pull(this) { result ->
            if (isFinishing || isDestroyed) return@pull
            when (result) {
                HouseholdPull.CHANGED -> {
                    loadIntoForm()
                    if (NotificationHelper.isEnabled(this)) NotificationHelper.show(this)
                    resyncInBackground()
                }
                // 가정 문서가 비어 있다 — 이 기능 전에 묶은 가정이다. 이 폰 값으로 채운다.
                HouseholdPull.EMPTY -> HouseholdSync.push(this, SettingsStore.load(this))
                else -> Unit
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
                    HouseholdStore.setCode(this, household.code)
                    householdPaired = true
                    householdCode = household.code
                    // 배우자가 코드를 넣자마자 받아 갈 공용 예산·월급날을 올려 둔다.
                    autosave()
                    HouseholdSync.push(this, SettingsStore.load(this))
                    setHouseholdMessage("가정을 만들었어요. «코드 공유하기»로 배우자에게 보내 주세요.")
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
                    HouseholdStore.setCode(this, HouseholdCode.normalize(householdJoinInput))
                    householdPaired = true
                    householdCode = HouseholdStore.code(this)
                    householdJoinInput = ""
                    setHouseholdMessage("가정에 연결됐어요. 공용 예산·월급날은 가정 값을 따릅니다.")
                    pullHouseholdSettings()
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

    /** 가정 코드를 공유 창으로 보낸다. 받는 사람이 어디에 넣으면 되는지까지 적는다. */
    private fun shareHouseholdCode() {
        val code: String = householdCode ?: return
        val text: String =
            "곳간 가정 코드: $code\n" +
                "앱 첫 화면의 «배우자에게 받은 가정 코드가 있어요»나 설정 › 가정에 넣으면 같이 쓸 수 있어요."
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(send, "가정 코드 보내기"))
    }

    private fun setHouseholdMessage(message: String, isError: Boolean = false) {
        householdMessage = message
        householdMessageIsError = isError
    }

    override fun onDestroy() {
        io.shutdown()
        super.onDestroy()
    }

    /** 지금 설정을 코드로 만들어 클립보드에 올린다. 칸에 적는 중인 값도 먼저 저장해 담는다. */
    private fun exportSettings() {
        autosave()
        val code: String = SettingsCodec.encode(SettingsStore.load(this))
        val clipboard = getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("expense-settings", code))
        setStatus("설정 코드를 클립보드에 복사했어요. 메모에 붙여 보관하세요.")
    }

    /**
     * 지출 전부를 CSV 파일로 만들어 공유 시트로 넘긴다.
     *
     * 클립보드를 쓰지 않는 이유는 분량이다 — 몇 백 줄짜리 표를 클립보드로 옮기면 붙여 넣는
     * 쪽에서 잘린다. 파일은 앱 전용 캐시에 두고 [FileProvider] 로만 건네므로, 공유를 누르기
     * 전까지는 어떤 앱도 읽지 못한다.
     *
     * 저장소를 읽으므로 백그라운드에서 만든다. 지출이 많으면 몇 초 걸릴 수 있어 먼저 알린다.
     *
     * 안드로이드 10 이상이면 폰의 «다운로드» 폴더에 바로 들어간다. 그 아래 버전에서는
     * 저장 권한 없이 넣을 수 없어 공유 시트로 넘긴다([ExpenseExportRepository]).
     */
    private fun exportExpenses() {
        setStatus("지출을 모으는 중…")
        io.execute {
            val result: ExportResult = try {
                ExpenseExportRepository.write(this)
            } catch (e: Exception) {
                ExportResult.Err("오류: ${e.message ?: e.javaClass.simpleName}")
            }

            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                when (result) {
                    is ExportResult.Err -> setStatus(result.message, isError = true)
                    is ExportResult.Ok -> {
                        val savedTo: String? = result.savedTo
                        if (savedTo != null) {
                            setStatus("지출 ${result.count}건을 «$savedTo» 에 저장했습니다.")
                        } else {
                            // 옛 기기라 내려받지 못했다. 공유 시트가 남은 길이다.
                            setStatus("지출 ${result.count}건을 파일로 만들었습니다. 보낼 곳을 고르세요.")
                            share(result)
                        }
                    }
                }
            }
        }
    }

    /** 만든 파일을 공유 시트에 올린다. 받는 앱에만 읽기 권한을 잠깐 준다. */
    private fun share(result: ExportResult.Ok) {
        val uri: Uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", result.file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, result.file.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(send, "지출 내보내기"))
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
        val before: Settings = SettingsStore.load(this)
        SettingsStore.save(this, restored)
        HouseholdSync.pushIfNeeded(this, before, restored)
        loadIntoForm()
        savedCount++
        setStatus("설정을 불러와 저장했어요.")
    }

    /** «알림 접근» 권한이 이 앱에 허용돼 있는지. 리스너 서비스는 이게 있어야 동작한다. */
    private fun hasNotificationAccess(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)

    private fun refreshReminderButton() {
        reminderOn = ReminderState.isEnabled(this)
    }

    /**
     * 결제 알림 읽기를 켜고 끈다.
     *
     * 켤 때 «알림 접근» 권한이 없으면 먼저 그 설정으로 보낸다 — 권한 없이는 결제 알림을
     * 읽을 수 없다. 상시 알림 자체가 꺼져 있으면 그것부터 켜야 한다고 알린다.
     */
    private fun toggleReminder() {
        if (ReminderState.isEnabled(this)) {
            ReminderState.setEnabled(this, false)
            refreshReminderButton()
            setStatus("결제 알림 읽기를 껐어요.")
            return
        }

        if (!NotificationState.isOn(this)) {
            setStatus("먼저 «잠금화면 알림»을 켜 주세요. 결제 알림도 그 자리를 씁니다.", isError = true)
            return
        }
        if (!hasNotificationAccess()) {
            setStatus("«알림 접근»에서 «곳간»을 켠 뒤 돌아와 스위치를 다시 눌러 주세요.")
            openNotificationAccessSettings()
            return
        }

        ReminderState.setEnabled(this, true)
        refreshReminderButton()
        setStatus("결제 알림 읽기를 켰어요. 결제를 보면 금액과 가게를 잠깐 띄우고, 몇 초 뒤 스스로 사라져요.")
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
        if (!PurseAccess.isReady(this)) return
        NotificationHelper.show(this)
    }

    private fun loadIntoForm() {
        val s: Settings = SettingsStore.load(this)
        form = SettingsFormUi(
            categoriesText = Categories.format(CategoryStore.load(this)),
            payDayText = s.payDay.toString(),
            personalName = s.personal.name,
            personalBudgetText = budgetText(s.personal.monthlyBudget),
            sharedName = s.shared.name,
            sharedBudgetText = budgetText(s.shared.monthlyBudget),
        )
    }

    private fun budgetText(amount: Long): String = if (amount > 0L) amount.toString() else ""

    /**
     * 칸의 값으로 만든 설정. 월급날 칸을 비웠거나 숫자가 아니면 저장된 날을 그대로 둔다 — 칸을
     * 지우고 나가는 순간 주기가 1일로 바뀌면 곳간 숫자가 통째로 달라진다.
     */
    private fun currentForm(stored: Settings): Settings = Settings(
        payDay = form.payDayText.trim().toIntOrNull()?.let(Payday::normalize) ?: stored.payDay,
        personal = PurseSettings(
            monthlyBudget = readBudget(form.personalBudgetText),
            name = form.personalName.trim(),
        ),
        shared = PurseSettings(
            monthlyBudget = readBudget(form.sharedBudgetText),
            name = form.sharedName.trim(),
        ),
    )

    /** "930000" 도 "93만" 도 받는다. 잠금화면 입력과 같은 규칙이라 따로 배울 게 없다. */
    private fun readBudget(raw: String): Long = ExpenseParser.parseAmount(raw.trim()) ?: 0L

    /**
     * 앱을 연 김에 저장소를 기준으로 이번 달 캐시를 곳간마다 다시 맞춘다.
     * 잠금화면 기록은 로컬 사본만 보고 계산하므로, 다른 기기에서 적은 것은 여기서 들어온다.
     */
    private fun resyncInBackground() {
        val settings: Settings = SettingsStore.load(this)
        val purses: List<Purse> = PurseAccess.active(this, settings)
        if (purses.isEmpty()) return

        io.execute {
            val failures: List<String> = purses.mapNotNull { purse ->
                Ledger.resync(applicationContext, purse)
                    ?.let { "${settings.labelOf(purse)}: $it" }
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (failures.isNotEmpty()) {
                    setStatus(
                        "저장소 대조에 실패해 로컬 기록으로 표시 중입니다.\n\n" +
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

    /**
     * 칸의 값을 저장한다. 저장 버튼이 없다 — 칸을 떠날 때, 화면을 나갈 때 여기로 온다.
     *
     * 바뀐 게 없으면 아무것도 하지 않는다(«저장됨»도 띄우지 않는다). 바뀌었으면 칸을 저장된
     * 모양으로 다시 채운다 — «93만»이 «930000»으로 보이면 제대로 읽혔다는 뜻이다.
     *
     * 같이 쓰는 값(공용 예산·이름·월급날)이 바뀌었으면 가정에도 올린다([HouseholdSync]).
     */
    private fun autosave() {
        val before: Settings = SettingsStore.load(this)
        val after: Settings = currentForm(before)
        val categoriesBefore: List<String> = CategoryStore.load(this)
        val categoriesAfter: List<String> = Categories.parse(form.categoriesText)

        val settingsChanged: Boolean = after != before
        val categoriesChanged: Boolean = categoriesAfter != categoriesBefore
        HouseholdSync.pushIfNeeded(this, before, after)
        if (!settingsChanged && !categoriesChanged) return

        if (categoriesChanged) CategoryStore.save(this, categoriesAfter)
        if (settingsChanged) SettingsStore.save(this, after)
        loadIntoForm()
        savedCount++

        if (settingsChanged) {
            // 예산이 바뀌면 잠금화면 카드의 숫자도 바뀐다.
            if (NotificationHelper.isEnabled(this)) NotificationHelper.show(this)
            resyncInBackground()
        }
    }

    private fun requestNotificationThenEnable() {
        if (!PurseAccess.isReady(this)) {
            setStatus("먼저 로그인해 주세요. 계정이 있어야 기록을 저장할 수 있습니다.", isError = true)
            return
        }

        if (LockCard.needsPermission(this)) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        enableNotification()
    }

    private fun enableNotification() {
        LockCard.enable(this)
        notificationOn = true

        if (NotificationHelper.isEnabled(this)) {
            val pick: String = if (PurseAccess.linked(this).size > 1) " 곳간은 입력 화면 위에서 골라요." else ""
            setStatus("알림을 켰어요. 잠금화면 카드를 눌러 «커피 4500»처럼 적어 보세요.$pick")
        } else {
            setStatus("이 앱의 알림이 차단돼 있어요. «시스템 알림 설정 열기»에서 허용해 주세요.", isError = true)
        }
    }

    private fun disableNotification() {
        LockCard.disable(this)
        notificationOn = false
        setStatus("알림을 껐어요. 다시 켜기 전까지 잠금화면에 나오지 않아요.")
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

    /** 화면 아래 안내를 띄운다. 같은 문구를 다시 띄워도 다시 보이게 번호를 올린다. */
    private fun setStatus(message: String, isError: Boolean = false) {
        toast = message
        toastIsError = isError
        toastId++
    }
}
