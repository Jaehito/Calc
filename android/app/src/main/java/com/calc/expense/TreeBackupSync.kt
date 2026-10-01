package com.calc.expense

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

/**
 * 나무([TreeState])를 `users/{uid}` 문서의 `tree` 칸에 맡기고 되찾는다. 재설치해도 키우던 나무가 남는다.
 *
 * - 올리기([schedulePush]): 나무가 바뀔 때마다 [TreeStore] 가 부른다. 잇따른 물 주기를 한 번으로 모은다.
 *   계정과 한 번 맞춰 보기 전에는 올리지 않는다 — 재설치 직후 빈 나무가 맡겨 둔 나무를 덮지 않게.
 * - 되찾기([restoreOnce]): 앱을 열 때, 이 폰에서 아직 맞춰 본 적이 없으면 한 번.
 *
 * 개인 설정 백업([PersonalBackupSync])과 같은 문서지만 칸이 달라 서로 덮지 않는다(merge).
 */
object TreeBackupSync {

    private const val TAG = "TreeBackup"
    private const val PUSH_DELAY_MS = 2_000L

    private val main = Handler(Looper.getMainLooper())
    private var pending: Runnable? = null

    @Volatile
    private var restoring: Boolean = false

    fun schedulePush(context: Context) {
        val app: Context = context.applicationContext
        if (!TreeStore.restoreChecked(app)) return
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
        FirebaseFirestore.getInstance().collection("users").document(uid)
            .set(mapOf(TreeState.FIELD to TreeStore.load(app).toMap()), SetOptions.merge())
            .addOnFailureListener { Log.w(TAG, "나무 백업 실패(다음에 바뀔 때 다시 올린다)", it) }
    }

    /** 이 폰에서 아직 계정과 맞춰 본 적이 없으면 맡겨 둔 나무를 되찾아 합친다. 되찾았으면 [onRestored]. */
    fun restoreOnce(context: Context, onRestored: () -> Unit) {
        val app: Context = context.applicationContext
        if (restoring || TreeStore.restoreChecked(app)) return
        val uid: String = FirebaseAuth.getInstance().currentUser?.uid ?: return
        restoring = true
        FirebaseFirestore.getInstance().collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                restoring = false
                val remote: TreeState? = TreeState.fromMap(doc.get(TreeState.FIELD) as? Map<*, *>)
                TreeStore.applyRestored(app, remote)
                if (remote != null) onRestored()
            }
            .addOnFailureListener {
                // 다음에 앱을 열 때 다시 맞춰 본다. 그때까지는 올리지 않는다.
                restoring = false
                Log.w(TAG, "나무를 되찾지 못함", it)
            }
    }
}
