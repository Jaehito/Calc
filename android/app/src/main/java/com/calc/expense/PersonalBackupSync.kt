package com.calc.expense

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

/**
 * 개인 설정([PersonalBackup])을 `users/{uid}` 문서에 맡기고 되찾는다.
 *
 * - 올리기([schedulePush]): 설정·카테고리·고정비 저장소가 바뀔 때마다 그 저장소가 부른다.
 *   잇따른 저장을 한 번으로 모으려고 잠깐 기다렸다 올린다. 예산이 없으면 올리지 않는다.
 * - 되찾기([restoreIfEmpty]): 로그인 직후, 이 폰에 개인 예산이 없을 때만. 되찾으면 온보딩을 건너뛴다.
 *
 * 같은 문서의 `householdId` 는 건드리지 않는다(merge 로 `personal` 칸만 쓴다).
 * 콜백은 메인 스레드로 온다(Firestore 기본).
 */
object PersonalBackupSync {

    private const val TAG = "PersonalBackup"
    private const val PUSH_DELAY_MS = 1_500L
    private const val RESTORE_TIMEOUT_MS = 6_000L

    private val main = Handler(Looper.getMainLooper())
    private var pending: Runnable? = null

    /** 되찾은 값을 저장소에 쓰는 동안은 올리지 않는다 — 방금 받은 값을 그대로 되올릴 이유가 없다. */
    @Volatile
    private var restoring: Boolean = false

    /** 이 프로세스에서 한 번이라도 올렸는지. */
    @Volatile
    private var pushedOnce: Boolean = false

    /**
     * 앱을 열 때 한 번 올린다. 이 기능 전부터 쓰던 사람은 설정을 고치기 전까지 계정에 아무것도
     * 없다 — 그 사람이 재설치하면 되찾을 게 없다. 프로세스마다 한 번이라 가볍다.
     */
    fun ensureBackedUp(context: Context) {
        if (pushedOnce) return
        schedulePush(context)
    }

    fun schedulePush(context: Context) {
        if (restoring) return
        val app: Context = context.applicationContext
        main.post {
            pending?.let { main.removeCallbacks(it) }
            val run = Runnable { push(app) }
            pending = run
            main.postDelayed(run, PUSH_DELAY_MS)
        }
    }

    private fun push(app: Context) {
        pending = null
        val uid: String = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val backup: PersonalBackup = PersonalBackup.of(
            SettingsStore.load(app),
            CategoryStore.load(app),
            FixedCostStore.load(app),
            FixedCostStore.wasAsked(app),
        )
        if (!backup.worthKeeping) return
        pushedOnce = true
        FirebaseFirestore.getInstance().collection("users").document(uid)
            .set(mapOf(PersonalBackup.FIELD to backup.toMap()), SetOptions.merge())
            .addOnFailureListener { Log.w(TAG, "개인 설정 백업 실패(다음 저장 때 다시 올린다)", it) }
    }

    /**
     * 이 폰에 개인 예산이 없으면 계정에 맡겨 둔 값을 되찾는다. 되찾았으면 true.
     *
     * 오프라인이거나 느리면 [RESTORE_TIMEOUT_MS] 뒤 false 로 넘어간다 — 로그인 화면에서
     * 하염없이 기다리게 두지 않는다. 그 뒤 늦게 도착한 값은 쓰지 않는다(이미 온보딩으로 갔다).
     */
    fun restoreIfEmpty(context: Context, onDone: (Boolean) -> Unit) {
        val app: Context = context.applicationContext
        val uid: String = FirebaseAuth.getInstance().currentUser?.uid ?: return onDone(false)
        if (SettingsStore.load(app).personal.hasBudget) return onDone(false)

        var finished = false
        val finish: (Boolean) -> Unit = { restored ->
            if (!finished) {
                finished = true
                onDone(restored)
            }
        }
        main.postDelayed({ finish(false) }, RESTORE_TIMEOUT_MS)

        FirebaseFirestore.getInstance().collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                if (finished) return@addOnSuccessListener
                val backup: PersonalBackup? = PersonalBackup.fromMap(doc.get(PersonalBackup.FIELD) as? Map<*, *>)
                // 기다리는 사이 이 폰에서 예산을 정했으면 그쪽이 맞다.
                if (backup == null || SettingsStore.load(app).personal.hasBudget) return@addOnSuccessListener finish(false)
                apply(app, backup)
                finish(true)
            }
            .addOnFailureListener {
                Log.w(TAG, "개인 설정을 되찾지 못함", it)
                finish(false)
            }
    }

    private fun apply(app: Context, backup: PersonalBackup) {
        restoring = true
        try {
            SettingsStore.save(app, PersonalBackup.applyTo(SettingsStore.load(app), backup))
            if (backup.categories.isNotEmpty()) CategoryStore.save(app, backup.categories)
            val plan: FixedCostPlan = FixedCostCodec.decode(backup.fixedCosts)
            if (plan.monthlyIncome > 0L || plan.items.isNotEmpty()) FixedCostStore.save(app, plan)
            if (backup.asked) FixedCostStore.markAsked(app)
        } finally {
            restoring = false
        }
    }
}
