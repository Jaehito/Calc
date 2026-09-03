package com.calc.expense

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.time.LocalDate

/**
 * 노션 옆에 나란히 쓰는 Firestore 사본 — 2단계(이중 쓰기) 의 안전망.
 *
 * 노션이 아직 SSOT 다. 여기 실패는 사용자에게 보이지 않는다 — 기록 자체(노션 쓰기)가
 * 이미 끝난 뒤에만 불리고, 이 사본은 3단계 백필로도 채울 수 있어 재시도하지 않는다.
 * 문서 id 는 노션 페이지 id 를 그대로 쓴다 — [RecordExpense] 가 수정·삭제를 그 id 로
 * 주소 지정하므로, 같은 id 를 쓰면 별도 매핑 없이 그대로 지울 수 있다.
 *
 * 공용 곳간은 아직 가정으로 안 묶였으면([HouseholdStore] 가 비어 있으면) 조용히 건너뛴다 —
 * 강제로 개인 컬렉션에 넣으면 배우자가 못 보는 채로 3단계 백필이 더 꼬인다.
 */
object FirestoreExpenseStore {

    private const val TAG = "FirestoreExpenseStore"

    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    fun add(context: Context, purse: Purse, pageId: String, expense: Expense, date: LocalDate) {
        if (pageId.isBlank()) return
        val collection = collectionFor(context, purse) ?: return
        val doc: HashMap<String, Any> = hashMapOf(
            "name" to expense.name,
            "amount" to expense.amount,
            "category" to expense.category,
            "date" to date.toString(),
            "purse" to purse.key,
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        collection.document(pageId).set(doc)
            .addOnFailureListener { Log.w(TAG, "add 실패(노션엔 이미 저장됨, 무시)", it) }
    }

    fun archive(context: Context, purse: Purse, pageId: String) {
        if (pageId.isBlank()) return
        val collection = collectionFor(context, purse) ?: return
        collection.document(pageId).delete()
            .addOnFailureListener { Log.w(TAG, "archive 실패(노션엔 이미 지워짐, 무시)", it) }
    }

    private fun collectionFor(context: Context, purse: Purse): CollectionReference? = when (purse) {
        Purse.PERSONAL -> {
            val uid: String = FirebaseAuth.getInstance().currentUser?.uid ?: return null
            db.collection("users").document(uid).collection("expenses")
        }
        Purse.SHARED -> {
            val householdId: String = HouseholdStore.householdId(context) ?: return null
            db.collection("households").document(householdId).collection("expenses")
        }
    }
}
