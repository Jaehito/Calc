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
            setStatus("알림을 껐습니다. 다시 켜기 전까지 잠금화면에 나오지 않습니다.")
        }
        ui.buttonNotificationSettings.setOnClickListener { openNotificationSettings() }
        ui.buttonOpenInput.setOnClickListener {
            startActivity(Intent(this, QuickInputActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStorageNotice()
        republishNotification()
        refreshLedger()
        resyncInBackground()
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
        if (NotificationHelper.isEnabled(this)) {
            val settings = SettingsStore.load(this)
            val purses = settings.linkedPurses
            val howTo =
                "알림 카드를 누르면 입력 화면이 바로 뜹니다. «커피 4500» 처럼 적으세요.\n" +
                    if (purses.size > 1) {
                        "곳간은 입력 화면에서 고릅니다. 알림의 «${settings.labelOf(purses[0])}» · " +
                            "«${settings.labelOf(purses[1])}» 버튼으로 바로 적어도 됩니다."
                    } else {
                        "알림의 «기록» 버튼으로 바로 적어도 됩니다."
                    }
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
