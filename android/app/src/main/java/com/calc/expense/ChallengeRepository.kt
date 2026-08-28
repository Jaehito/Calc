package com.calc.expense

/** 방을 만들면 나오는 값 — 방 id 와 공유할 코드. */
data class CreatedChallenge(val challengeId: String, val code: String)

/** 방 한 벌의 실시간 스냅샷. [members] 는 이번 주 참가자별 성적(정렬 전). */
data class RoomSnapshot(
    val challengeId: String,
    val name: String,
    val code: String,
    val members: List<MemberWeek>,
)

/** 실시간 구독을 끊는 손잡이. */
fun interface Cancellable {
    fun cancel()
}

/**
 * 챌린지 백엔드 계약.
 *
 * **이 인터페이스 뒤에 Firestore 를 숨긴다** — 나중에 다른 DB(Supabase 등)로 옮길 때
 * 구현만 갈아끼우고 화면·순위 로직([ChallengeStandings])은 그대로 두기 위해서다.
 * 순위 계산은 이 계층이 하지 않는다. 화면이 [RoomSnapshot.members] 를 [ChallengeStandings] 로
 * 세운다.
 */
interface ChallengeRepository {

    /** 로컬에 기억된, 지금 참가 중인 방 id. 없으면 null. */
    val joinedChallengeId: String?

    /** 이미 로그인돼 있으면 그 uid, 아니면 null. */
    val currentUid: String?

    /** 익명 로그인을 보장하고 내 uid 를 돌려준다(실패 시 null). */
    fun ensureSignedIn(onReady: (uid: String?) -> Unit)

    /** 새 방을 만든다. 성공하면 방 id 와 코드. */
    fun createChallenge(name: String, myName: String, onDone: (Result<CreatedChallenge>) -> Unit)

    /** 코드로 방에 참가한다. 성공하면 방 id. */
    fun joinChallenge(code: String, myName: String, onDone: (Result<String>) -> Unit)

    /** 내 이번 주 성적을 방의 그 주 문서에 올린다(덮어쓰기). */
    fun pushMyWeek(challengeId: String, weekKey: String, myName: String, spent: Long, budget: Long)

    /** 방 정보 + 그 주 참가자 성적을 실시간으로 받는다. */
    fun observe(
        challengeId: String,
        weekKey: String,
        listener: (Result<RoomSnapshot>) -> Unit,
    ): Cancellable

    /** 방을 나간다(로컬 기억만 지운다). */
    fun leave()
}
