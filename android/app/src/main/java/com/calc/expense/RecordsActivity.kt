package com.calc.expense

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import java.time.LocalDate

/**
 * 내 기록 화면. 통계 맨 아래 «내 기록» 칸에서 들어온다.
 *
 * 숫자는 도감과 같은 계산([DogamStore])을 그대로 읽는다 — 다시 세지 않는다. 다시 세는 일은
 * 도감 탭과 앱을 열 때 하루 한 번([HomeActivity])이 맡는다.
 */
class RecordsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings: Settings = SettingsStore.load(this)
        val ui = DogamUi(
            result = DogamStore.load(this),
            purseLabels = Purse.entries.associateWith { settings.labelOf(it) },
        )
        setContent {
            RecordsScreen(ui = ui, today = LocalDate.now(), onClose = { finish() })
        }
    }
}
