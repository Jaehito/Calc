package com.calc.expense

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

/** 가정을 새로 만들었을 때 나오는 값 — 배우자에게 알려줄 코드. */
data class Household(val householdId: String, val code: String)

/**
 * 공용 곳간 지출을 배우자와 Firestore 에서 같이 보게 묶는 «가정».
 *
 * 6자리 코드([HouseholdCode])로 묶는다 — 사람이 부르고 옮겨 적기 쉬운 형식이다.
 * 가정은 한 번 묶으면 끝이라 방을 여러 개 만들거나 옮겨 다니는 개념이 없고,
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
        val code: String = HouseholdCode.generate()
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
        val normalized: String = HouseholdCode.normalize(code)
        if (!HouseholdCode.isValid(normalized)) {
            onDone(Result.failure(IllegalArgumentException(tr("코드는 6자리예요. 다시 확인해 주세요.", "The code has 6 characters. Please check it.", "El código tiene 6 caracteres. Revísalo."))))
            return
        }
        db.collection("households")
            .whereEqualTo("code", normalized)
            .limit(1)
            .get()
            .addOnSuccessListener { snap ->
                val doc = snap.documents.firstOrNull()
                if (doc == null) {
                    onDone(Result.failure(NoSuchElementException(tr("그 코드의 가정을 찾을 수 없어요.", "No household found with that code.", "No hay ningún hogar con ese código."))))
                    return@addOnSuccessListener
                }
                db.collection("users").document(uid)
                    .set(hashMapOf("householdId" to doc.id), SetOptions.merge())
                    .addOnSuccessListener { onDone(Result.success(doc.id)) }
                    .addOnFailureListener { onDone(Result.failure(it)) }
            }
            .addOnFailureListener { onDone(Result.failure(it)) }
    }

    /** 가정 코드. 묶인 뒤에도 다시 보내 줄 수 있게 필요할 때마다 읽는다. */
    fun code(householdId: String, onReady: (String?) -> Unit) {
        db.collection("households").document(householdId).get()
            .addOnSuccessListener { onReady(it.getString("code")) }
            .addOnFailureListener { onReady(null) }
    }

    /**
     * 가정 문서에 올려 둔 같이 쓰는 설정. 아직 아무도 올리지 않았으면 null 이 성공으로 온다
     * ([HouseholdSettings.fromFields]). 읽기 자체가 실패하면 실패로 온다 — 둘을 섞으면
     * «비어 있다»로 오해해 누군가 옛 값으로 덮어쓴다.
     */
    fun readSettings(householdId: String, onDone: (Result<SharedSettings?>) -> Unit) {
        db.collection("households").document(householdId).get()
            .addOnSuccessListener { onDone(Result.success(HouseholdSettings.fromFields(it.data.orEmpty()))) }
            .addOnFailureListener { onDone(Result.failure(it)) }
    }

    /** 같이 쓰는 설정을 가정 문서에 덮는다. 코드는 건드리지 않는다(merge). */
    fun writeSettings(householdId: String, shared: SharedSettings, onDone: (Result<Unit>) -> Unit = {}) {
        val fields: HashMap<String, Any> = HashMap(HouseholdSettings.toFields(shared))
        fields["settingsUpdatedAt"] = FieldValue.serverTimestamp()
        db.collection("households").document(householdId)
            .set(fields, SetOptions.merge())
            .addOnSuccessListener { onDone(Result.success(Unit)) }
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
