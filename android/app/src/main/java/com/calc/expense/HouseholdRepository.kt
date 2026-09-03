package com.calc.expense

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

/** 가정을 새로 만들었을 때 나오는 값 — 배우자에게 알려줄 코드. */
data class Household(val householdId: String, val code: String)

/**
 * 공용 곳간 지출을 배우자와 Firestore 에서 같이 보게 묶는 «가정».
 *
 * 챌린지의 6자리 코드([ChallengeCode])와 같은 체계를 그대로 쓴다 — 사람이 부르고 옮겨
 * 적기 쉬운 코드 형식을 새로 고안할 이유가 없다. 챌린지와 달리 방을 여러 개 만들거나
 * 옮겨 다닐 일이 없다 — 가정은 한 번 묶으면 끝이라 «참가 중인 방 목록» 같은 개념이 없고,
 * `users/{uid}.householdId` 필드 하나가 전부다.
 */
object HouseholdRepository {

    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    /** 내 uid 가 이미 묶인 가정이 있으면 그 id, 없으면 null. */
    fun currentHouseholdId(uid: String, onReady: (String?) -> Unit) {
        db.collection("users").document(uid).get()
            .addOnSuccessListener { onReady(it.getString("householdId")) }
            .addOnFailureListener { onReady(null) }
    }

    /** 새 가정을 만들고 코드를 돌려준다. 배우자가 이 코드로 [join] 하면 묶인다. */
    fun create(uid: String, onDone: (Result<Household>) -> Unit) {
        val code: String = ChallengeCode.generate()
        val room: HashMap<String, Any> = hashMapOf(
            "code" to code,
            "createdAt" to FieldValue.serverTimestamp(),
        )
        db.collection("households").add(room)
            .addOnSuccessListener { ref ->
                db.collection("users").document(uid)
                    .set(hashMapOf("householdId" to ref.id), SetOptions.merge())
                    .addOnSuccessListener { onDone(Result.success(Household(ref.id, code))) }
                    .addOnFailureListener { onDone(Result.failure(it)) }
            }
            .addOnFailureListener { onDone(Result.failure(it)) }
    }

    /** 배우자가 만든 코드로 그 가정에 들어간다. 성공하면 그 가정의 id 를 돌려준다. */
    fun join(uid: String, code: String, onDone: (Result<String>) -> Unit) {
        val normalized: String = ChallengeCode.normalize(code)
        if (!ChallengeCode.isValid(normalized)) {
            onDone(Result.failure(IllegalArgumentException("코드는 6자리예요. 다시 확인해 주세요.")))
            return
        }
        db.collection("households")
            .whereEqualTo("code", normalized)
            .limit(1)
            .get()
            .addOnSuccessListener { snap ->
                val doc = snap.documents.firstOrNull()
                if (doc == null) {
                    onDone(Result.failure(NoSuchElementException("그 코드의 가정을 찾을 수 없어요.")))
                    return@addOnSuccessListener
                }
                db.collection("users").document(uid)
                    .set(hashMapOf("householdId" to doc.id), SetOptions.merge())
                    .addOnSuccessListener { onDone(Result.success(doc.id)) }
                    .addOnFailureListener { onDone(Result.failure(it)) }
            }
            .addOnFailureListener { onDone(Result.failure(it)) }
    }

    /** 가정 연결을 끊는다(내 쪽만 — 배우자는 그대로 묶여 있다). 잘못 묶었을 때 되돌리는 용도. */
    fun leave(uid: String, onDone: (Result<Unit>) -> Unit) {
        db.collection("users").document(uid)
            .set(hashMapOf("householdId" to FieldValue.delete()), SetOptions.merge())
            .addOnSuccessListener { onDone(Result.success(Unit)) }
            .addOnFailureListener { onDone(Result.failure(it)) }
    }
}
