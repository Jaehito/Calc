package com.calc.expense

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

/**
 * [ChallengeRepository] 의 Firebase 구현.
 *
 * 신원은 익명 인증(로그인 화면 없이 기기마다 uid 하나), 데이터는 Firestore 에 둔다:
 *
 * ```
 * challenges/{id}            { name, code, createdAt }
 *   weeks/{weekKey}/entries/{uid}   { name, spent, budget, updatedAt }
 * ```
 *
 * 콜백은 Firestore SDK 가 메인 스레드로 준다. 화면 상태를 콜백에서 바로 갱신해도 된다.
 */
class FirestoreChallengeRepository(context: Context) : ChallengeRepository {

    private val app: Context = context.applicationContext
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()

    override val joinedChallengeId: String?
        get() = ChallengeStore.challengeId(app)

    override val currentUid: String?
        get() = auth.currentUser?.uid

    override fun ensureSignedIn(onReady: (String?) -> Unit) {
        val user = auth.currentUser
        if (user != null) {
            onReady(user.uid)
            return
        }
        auth.signInAnonymously()
            .addOnSuccessListener { onReady(it.user?.uid) }
            .addOnFailureListener { onReady(null) }
    }

    override fun createChallenge(
        name: String,
        myName: String,
        onDone: (Result<CreatedChallenge>) -> Unit,
    ) {
        ensureSignedIn { uid ->
            if (uid == null) {
                onDone(Result.failure(IllegalStateException("로그인에 실패했어요. 잠시 뒤 다시 시도해 주세요.")))
                return@ensureSignedIn
            }
            val code: String = ChallengeCode.generate()
            val room: HashMap<String, Any> = hashMapOf(
                "name" to name,
                "code" to code,
                "createdAt" to FieldValue.serverTimestamp(),
            )
            db.collection("challenges").add(room)
                .addOnSuccessListener { ref ->
                    ChallengeStore.setChallengeId(app, ref.id)
                    ChallengeStore.setMyName(app, myName)
                    onDone(Result.success(CreatedChallenge(ref.id, code)))
                }
                .addOnFailureListener { onDone(Result.failure(it)) }
        }
    }

    override fun joinChallenge(code: String, myName: String, onDone: (Result<String>) -> Unit) {
        val normalized: String = ChallengeCode.normalize(code)
        if (!ChallengeCode.isValid(normalized)) {
            onDone(Result.failure(IllegalArgumentException("코드는 6자리예요. 다시 확인해 주세요.")))
            return
        }
        ensureSignedIn { uid ->
            if (uid == null) {
                onDone(Result.failure(IllegalStateException("로그인에 실패했어요. 잠시 뒤 다시 시도해 주세요.")))
                return@ensureSignedIn
            }
            db.collection("challenges")
                .whereEqualTo("code", normalized)
                .limit(1)
                .get()
                .addOnSuccessListener { snap ->
                    val doc = snap.documents.firstOrNull()
                    if (doc == null) {
                        onDone(Result.failure(NoSuchElementException("그 코드의 방을 찾을 수 없어요.")))
                        return@addOnSuccessListener
                    }
                    ChallengeStore.setChallengeId(app, doc.id)
                    ChallengeStore.setMyName(app, myName)
                    onDone(Result.success(doc.id))
                }
                .addOnFailureListener { onDone(Result.failure(it)) }
        }
    }

    override fun pushMyWeek(
        challengeId: String,
        weekKey: String,
        myName: String,
        spent: Long,
        budget: Long,
    ) {
        val uid: String = auth.currentUser?.uid ?: return
        val entry: HashMap<String, Any> = hashMapOf(
            "name" to myName,
            "spent" to spent,
            "budget" to budget,
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        db.collection("challenges").document(challengeId)
            .collection("weeks").document(weekKey)
            .collection("entries").document(uid)
            .set(entry)
    }

    override fun observe(
        challengeId: String,
        weekKey: String,
        listener: (Result<RoomSnapshot>) -> Unit,
    ): Cancellable {
        val roomRef = db.collection("challenges").document(challengeId)
        val registration = arrayOfNulls<ListenerRegistration>(1)
        var cancelled: Boolean = false

        // 방 이름·코드는 한 번만 읽고, 그 주 엔트리만 실시간으로 구독한다.
        roomRef.get()
            .addOnSuccessListener { room ->
                if (cancelled) return@addOnSuccessListener
                if (!room.exists()) {
                    listener(Result.failure(NoSuchElementException("방이 사라졌어요.")))
                    return@addOnSuccessListener
                }
                val name: String = room.getString("name").orEmpty()
                val code: String = room.getString("code").orEmpty()

                registration[0] = roomRef
                    .collection("weeks").document(weekKey)
                    .collection("entries")
                    .addSnapshotListener { entries, error ->
                        if (error != null) {
                            listener(Result.failure(error))
                            return@addSnapshotListener
                        }
                        val members: List<MemberWeek> = entries?.documents.orEmpty().mapNotNull { d ->
                            val memberName: String = d.getString("name") ?: return@mapNotNull null
                            MemberWeek(
                                uid = d.id,
                                name = memberName,
                                spent = d.getLong("spent") ?: 0L,
                                budget = d.getLong("budget") ?: 0L,
                            )
                        }
                        listener(Result.success(RoomSnapshot(challengeId, name, code, members)))
                    }
            }
            .addOnFailureListener { listener(Result.failure(it)) }

        return Cancellable {
            cancelled = true
            registration[0]?.remove()
        }
    }

    override fun leave() {
        ChallengeStore.setChallengeId(app, null)
    }
}
