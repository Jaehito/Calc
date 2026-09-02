package com.calc.expense

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.calc.expense.databinding.ActivityMainBinding
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var ui: ActivityMainBinding
    private val io = Executors.newSingleThreadExecutor()

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) enableNotification()
            else setStatus("알림 권한이 거부되었습니다. 설정에서 직접 허용해 주세요.", isError = true)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = ActivityMainBinding.inflate(layoutInflater)
        setContentView(ui.root)

        loadIntoForm()

        ui.buttonSaveTest.setOnClickListener { saveAndVerify() }
        ui.buttonEnable.setOnClickListener { requestNotificationThenEnable() }
        ui.buttonDisable.setOnClickListener {
            // 먼저 꺼야 DismissReceiver 가 되살리지 않는다.
            NotificationState.setOn(this, false)
            NotificationHelper.hide(this)
            WeeklyReviewScheduler.cancel(this)
            setStatus("알림을 껐습니다. 다시 켜기 전까지 잠금화면에 나오지 않습니다.")
        }
        ui.buttonNotificationSettings.setOnClickListener { openNotificationSettings() }
        ui.buttonOpenInput.setOnClickListener {
            startActivity(Intent(this, QuickInputActivity::class.java))
        }
        ui.buttonReminderToggle.setOnClickListener { toggleReminder() }
        ui.buttonReminderAccess.setOnClickListener { openNotificationAccessSettings() }
        ui.buttonExportSettings.setOnClickListener { exportSettings() }
        ui.buttonImportSettings.setOnClickListener { importSettings() }
    }

    /** 지금 설정을 코드로 만들어 클립보드에 올린다. 폼에 아직 저장 안 한 값까지 그대로 담는다. */
    private fun exportSettings() {
        val code: String = SettingsCodec.encode(currentForm())
        val clipboard = getSystemService(android.content.ClipboardManager::class.java)
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("expense-settings", code))
        setStatus("설정 코드를 클립보드에 복사했습니다. 메모 등에 붙여 보관하세요. 코드에는 토큰이 들어 있으니 남에게 주지 마세요.")
    }

    /** 클립보드의 코드를 읽어 설정을 복원한다. 코드가 아니면 그대로 두고 알린다. */
    private fun importSettings() {
        val clipboard = getSystemService(android.content.ClipboardManager::class.java)
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

    override fun onResume() {
        super.onResume()
        refreshStorageNotice()
        republishNotification()
        refreshLedger()
        refreshReminderButton()
        resyncInBackground()
    }

    /** «알림 접근» 권한이 이 앱에 허용돼 있는지. 리스너 서비스는 이게 있어야 동작한다. */
    private fun hasNotificationAccess(): Boolean =
        androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(this)
            .contains(packageName)

    private fun refreshReminderButton() {
        ui.buttonReminderToggle.text =
            if (ReminderState.isEnabled(this)) "결제 리마인더 끄기" else "결제 리마인더 켜기"
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

    override fun onDestroy() {
        io.shutdown()
        super.onDestroy()
    }

    private fun loadIntoForm() {
        val s = SettingsStore.load(this)
        ui.inputToken.setText(s.token)
        ui.inputNameProp.setText(s.nameProp)
        ui.inputPriceProp.setText(s.priceProp)
        ui.inputDateProp.setText(s.dateProp)
        ui.inputPurseProp.setText(s.purseProp)
        ui.inputCategoryProp.setText(s.categoryProp)
        ui.inputCategories.setText(Categories.format(CategoryStore.load(this)))
        ui.inputPayDay.setText(s.payDay.toString())

        ui.inputPersonalName.setText(s.personal.name)
        ui.inputPersonalDatabaseId.setText(s.personal.databaseId)
        ui.inputPersonalBudget.setText(budgetText(s.personal.monthlyBudget))
        ui.inputSharedName.setText(s.shared.name)
        ui.inputSharedDatabaseId.setText(s.shared.databaseId)
        ui.inputSharedBudget.setText(budgetText(s.shared.monthlyBudget))
    }

    private fun budgetText(amount: Long): String = if (amount > 0L) amount.toString() else ""

    private fun currentForm() = Settings(
        token = ui.inputToken.text?.toString().orEmpty().trim(),
        nameProp = ui.inputNameProp.text?.toString().orEmpty().trim(),
        priceProp = ui.inputPriceProp.text?.toString().orEmpty().trim(),
        dateProp = ui.inputDateProp.text?.toString().orEmpty().trim(),
        purseProp = ui.inputPurseProp.text?.toString().orEmpty().trim(),
        categoryProp = ui.inputCategoryProp.text?.toString().orEmpty().trim(),
        payDay = Payday.normalize(
            ui.inputPayDay.text?.toString()?.trim()?.toIntOrNull() ?: Payday.DEFAULT
        ),
        personal = PurseSettings(
            databaseId = ui.inputPersonalDatabaseId.text?.toString().orEmpty().trim(),
            monthlyBudget = readBudget(ui.inputPersonalBudget.text?.toString()),
            name = ui.inputPersonalName.text?.toString().orEmpty().trim(),
        ),
        shared = PurseSettings(
            databaseId = ui.inputSharedDatabaseId.text?.toString().orEmpty().trim(),
            monthlyBudget = readBudget(ui.inputSharedBudget.text?.toString()),
            name = ui.inputSharedName.text?.toString().orEmpty().trim(),
        ),
    )

    /** "930000" 도 "93만" 도 받는다. 잠금화면 입력과 같은 규칙이라 따로 배울 게 없다. */
    private fun readBudget(raw: String?): Long =
        ExpenseParser.parseAmount(raw.orEmpty().trim()) ?: 0L

    /** 곳간 현황을 다시 그린다. 홈 화면이 생기기 전까지 숫자를 눈으로 확인하는 창구다. */
    private fun refreshLedger() {
        val snapshots = Purse.entries.mapNotNull { Ledger.snapshot(this, it) }
        ui.textLedger.text = StatusText.overview(snapshots)
    }

    /**
     * 앱을 연 김에 Notion 을 기준으로 이번 달 캐시를 곳간마다 다시 맞춘다.
     * 잠금화면 기록은 로컬 사본만 보고 계산하므로, 다른 기기에서 고친 것은 여기서 들어온다.
     */
    private fun resyncInBackground() {
        val settings = SettingsStore.load(this)
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
        ui.textStorageNotice.visibility =
            if (SettingsStore.usingEncryption) View.GONE else View.VISIBLE
    }

    private fun saveAndVerify() {
        // 카테고리 칩 목록은 노션 연결과 무관하므로 완성도 검사 전에 먼저 저장한다.
        CategoryStore.save(this, Categories.parse(ui.inputCategories.text?.toString().orEmpty()))

        val form = currentForm()
        if (!form.isComplete) {
            setStatus(
                "토큰과 속성 이름을 채우고, 두 곳간 중 최소 한 곳에 DB를 연결하세요.",
                isError = true,
            )
            return
        }

        SettingsStore.save(this, form)
        loadIntoForm()   // 정규화된 DB ID를 화면에 반영
        refreshStorageNotice()

        val saved = SettingsStore.load(this)
        setStatus("확인 중…")
        ui.buttonSaveTest.isEnabled = false

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
                ui.buttonSaveTest.isEnabled = true

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
            val granted = ContextCompat.checkSelfPermission(
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
        if (NotificationHelper.isEnabled(this)) {
            val purses = SettingsStore.load(this).linkedPurses
            val howTo =
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

    private fun openNotificationSettings() {
        val intent = Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, packageName)
        try {
            startActivity(intent)
        } catch (_: Exception) {
            startActivity(
                Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(android.net.Uri.fromParts("package", packageName, null))
            )
        }
    }

    private fun setStatus(message: String, isError: Boolean = false) {
        ui.textStatus.text = message
        ui.textStatus.setTextColor(
            ContextCompat.getColor(
                this,
                if (isError) R.color.status_error else R.color.status_normal
            )
        )
    }
}
