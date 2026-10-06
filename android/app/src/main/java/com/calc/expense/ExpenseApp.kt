package com.calc.expense

import android.app.Application
import android.content.res.Configuration

/**
 * 앱 프로세스의 시작점. 어떤 화면·알림·작업보다 먼저 언어를 정해 둔다 —
 * 재부팅 뒤 [BootReceiver] 가 카드를 다시 그릴 때도 고른 언어여야 한다.
 */
class ExpenseApp : Application() {

    override fun onCreate() {
        super.onCreate()
        LanguageStore.apply(this)
    }

    /** «폰 언어 따라가기» 일 때 폰 언어를 바꾸면 따라간다. */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        LanguageStore.apply(this)
    }
}
