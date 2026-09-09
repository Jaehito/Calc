package com.calc.expense

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.time.LocalDate

/**
 * 지출이 실제로 사는 곳. **Firestore 가 SSOT 다.**
 *
 * 노션을 쓰던 시절에는 이 파일이 «노션에 쓴 뒤 남기는 사본»이었다. 이제 반대다 — 여기 쓰기가
 * 성공해야 기록이고, 로컬 캐시([SpendingCache])가 그 파생이다.
 *
 * **네트워크를 기다리지 않는다.** Firestore SDK 는 쓰기를 디스크에 먼저 적어 두고 나중에
 * 서버로 올린다. `set()` 이 돌려주는 Task 는 서버가 받았을 때만 끝나므로, 그걸 기다리면
 * 비행기 모드에서 기록이 영영 끝나지 않는다. 그래서 **id 를 로컬에서 만들고 곧바로 성공으로
 * 본다** — 잠금화면 기록이 네트워크와 무관하게 즉시 끝나는 것이 이 앱의 전제다(노션을 쓸
 * 때는 왕복을 기다려야 해서 지하철에서 실패했다).
 *
 * 문서 id 는 [RecordExpense] 가 수정·삭제에 쓰는 주소다. 만들 때 정해서 돌려준다.
 */
object FirestoreExpenseStore {

    private const val TAG = "FirestoreExpenseStore"

    /** 쓰기 한 번의 결과. 실패는 «쓸 곳이 없다»뿐이다 — 네트워크 실패는 SDK 가 알아서 다시 올린다. */
    sealed interface Outcome {
        data class Ok(val id: String) : Outcome
        data class Err(val message: String) : Outcome
    }

    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    /** 지출 한 줄을 만든다. 성공하면 그 줄의 id 를 돌려준다. */
    fun add(context: Context, purse: Purse, expense: Expense, date: LocalDate): Outcome {
        val collection: CollectionReference =
            collectionFor(context, purse) ?: return Outcome.Err(missingReason(purse))

        val doc = collection.document()
        val payload: HashMap<String, Any> = hashMapOf(
            "name" to expense.name,
            "amount" to expense.amount,
            "category" to expense.category,
            "date" to date.toString(),
            "purse" to purse.key,
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        doc.set(payload).addOnFailureListener { Log.w(TAG, "add 서버 반영 실패(로컬엔 남음)", it) }
        return Outcome.Ok(doc.id)
    }

    /** 지출 한 줄을 지운다. [add] 와 같은 이유로 서버 응답을 기다리지 않는다. */
    fun archive(context: Context, purse: Purse, id: String): Outcome {
        if (id.isBlank()) return Outcome.Err("지울 줄을 찾을 수 없습니다")
        val collection: CollectionReference =
            collectionFor(context, purse) ?: return Outcome.Err(missingReason(purse))

        collection.document(id).delete()
            .addOnFailureListener { Log.w(TAG, "delete 서버 반영 실패(로컬엔 지워짐)", it) }
        return Outcome.Ok(id)
    }

    /**
     * 그 곳간의 컬렉션. 쓸 곳이 없으면 null 이고, 그것이 곧 «이 곳간에는 기록할 수 없다»는 뜻이다
     * ([PurseAccess] 가 이 판정을 그대로 쓴다).
     *
     * [FirestoreExpenseReader] 도 같은 경로 규칙을 쓴다 — 여기서만 정의한다.
     */
    internal fun collectionFor(context: Context, purse: Purse): CollectionReference? = when (purse) {
        Purse.PERSONAL -> {
            val uid: String = FirebaseAuth.getInstance().currentUser?.uid ?: return null
            db.collection("users").document(uid).collection("expenses")
        }
        Purse.SHARED -> {
            val householdId: String = HouseholdStore.householdId(context) ?: return null
            db.collection("households").document(householdId).collection("expenses")
        }
    }

    /** 쓸 곳이 없는 이유. 곳간마다 사용자가 할 일이 다르므로 문구도 다르다. */
    private fun missingReason(purse: Purse): String = when (purse) {
        Purse.PERSONAL -> "로그인이 풀렸습니다. 앱을 열어 다시 로그인해 주세요"
        Purse.SHARED -> "공용 곳간이 아직 배우자와 묶이지 않았습니다"
    }
}
