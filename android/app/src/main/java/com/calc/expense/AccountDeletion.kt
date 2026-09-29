package com.calc.expense

import android.app.Activity
import androidx.credentials.exceptions.NoCredentialException
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.tasks.await

/**
 * 계정과 기록을 지운다. 설정 › 계정의 «계정과 기록 삭제»가 부른다.
 *
 * 순서가 중요하다.
 * 1. **구글로 다시 확인받는다.** Firebase 는 로그인한 지 오래된 계정을 지우지 못하게 막는다.
 *    데이터를 먼저 지우고 계정 삭제에서 막히면, 기록만 사라지고 계정은 남는 반쪽이 된다.
 * 2. **내 지출(users/{uid}/expenses)과 내 문서(users/{uid})를 지운다.** 규칙상 로그인한 본인만
 *    지울 수 있어서 계정보다 먼저 지운다. 내 문서에는 가정 연결과 맡겨 둔 개인 설정이 있다.
 * 3. **Firebase 계정을 지운다.**
 * 4. **이 폰에 남은 것을 비운다.** 잠금화면 카드·결제 알림 읽기도 끈다.
 *
 * 공용 지갑의 기록(households/…)은 지우지 않는다 — 배우자의 기록이기도 하다. 가정 연결만
 * 끊기므로 배우자 폰에서는 그대로 보인다.
 */
object AccountDeletion {

    /** 한 번에 지우는 문서 수. Firestore 일괄 쓰기 상한(500) 아래로 둔다. */
    private const val BATCH = 400L

    suspend fun delete(activity: Activity): Result<Unit> {
        val auth: FirebaseAuth = FirebaseAuth.getInstance()
        val user: FirebaseUser = auth.currentUser
            ?: return Result.failure(IllegalStateException(tr("로그인 정보를 확인하지 못했어요", "Couldn't verify your sign-in", "No se pudo verificar tu sesión")))

        return try {
            // 이 앱에 쓴 계정만 먼저 보여 준다. 폰이 그 기록을 잊었으면(재설치 등) 전체 목록으로.
            val credential: AuthCredential = try {
                GoogleCredentials.fetch(activity, onlyAuthorized = true)
            } catch (_: NoCredentialException) {
                GoogleCredentials.fetch(activity, onlyAuthorized = false)
            }
            user.reauthenticate(credential).await()

            val db: FirebaseFirestore = FirebaseFirestore.getInstance()
            val mine = db.collection("users").document(user.uid)
            while (true) {
                val page: QuerySnapshot = mine.collection("expenses").limit(BATCH).get().await()
                if (page.isEmpty) break
                val batch = db.batch()
                for (doc in page.documents) batch.delete(doc.reference)
                batch.commit().await()
            }
            mine.delete().await()

            user.delete().await()

            LockCard.disable(activity)
            ReminderState.setEnabled(activity, false)
            AccountScope.forget(activity)
            auth.signOut()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
