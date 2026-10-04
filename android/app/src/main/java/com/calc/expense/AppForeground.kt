package com.calc.expense

import android.app.Activity
import android.app.Application
import android.os.Bundle

/**
 * 하루치 화면이 앞에 떠 있는지. 떠 있는 동안에는 하단 결제 팝업을 띄우지 않는다 — 사람이 이미 앱 안에
 * 있고, 수집함·기록 창에서 적는 중이면 팝업이 키보드 자리를 가린다. 결제는 수집함에는 그대로 담긴다.
 *
 * 화면이 앞으로 나오면 떠 있던 팝업도 닫는다(팝업을 눌러 들어온 경우 포함).
 */
object AppForeground : Application.ActivityLifecycleCallbacks {

    @Volatile
    private var resumed: Int = 0

    val isVisible: Boolean get() = resumed > 0

    fun register(app: Application) {
        app.registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityResumed(activity: Activity) {
        resumed++
        PaymentOverlay.dismiss()
    }

    override fun onActivityPaused(activity: Activity) {
        resumed = maxOf(0, resumed - 1)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityStarted(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}
