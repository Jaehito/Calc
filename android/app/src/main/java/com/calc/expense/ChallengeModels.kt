package com.calc.expense

/** 챌린지 한 사람의 이번 주 성적: 얼마 썼고, 그 주의 예산은 얼마였나. */
data class MemberWeek(
    val uid: String,
    val name: String,
    val spent: Long,
    val budget: Long,
)

/**
 * 순위 한 줄.
 *
 * [percent] 는 예산 대비 사용률(반올림 정수). 예산을 정하지 않았으면 [hasBudget] = false 이고
 * percent 는 0 이다 — 표시 쪽이 "예산 없음"으로 처리한다.
 */
data class Standing(
    val rank: Int,
    val uid: String,
    val name: String,
    val spent: Long,
    val budget: Long,
    val percent: Int,
    val hasBudget: Boolean,
    val isLeader: Boolean,
)

/**
 * 챌린지 순위. **예산 대비 덜 쓴 사람이 앞선다** — 소득·예산이 달라도 공평하게 겨루기 위해서다.
 *
 * 단순 총액이면 예산 큰 사람이 늘 지므로, "얼마나 무리했나(사용률)"로 세운다. Android 에
 * 의존하지 않아 단위 테스트로 고정한다 — 화면·Firestore 는 이 결과를 그대로 그린다.
 */
object ChallengeStandings {

    /**
     * [entries] 를 예산 대비 사용률 오름차순으로 세운다. 동률은 같은 등수(경쟁식: 1, 2, 2, 4).
     *
     * 사용률이 같으면 덜 쓴 금액이 앞서고, 그것도 같으면 진짜 동률이다. 예산을 정하지 않은
     * 사람([budget] ≤ 0)은 견줄 기준이 없어 맨 뒤로 보낸다.
     */
    fun rank(entries: List<MemberWeek>): List<Standing> =
        entries
            .map { me ->
                val rank: Int = 1 + entries.count { other -> isAhead(other, me) }
                Standing(
                    rank = rank,
                    uid = me.uid,
                    name = me.name,
                    spent = me.spent,
                    budget = me.budget,
                    percent = percentOf(me),
                    hasBudget = me.budget > 0L,
                    isLeader = rank == 1,
                )
            }
            .sortedWith(compareBy({ it.rank }, { it.name }))

    /** 예산 대비 사용률(반올림). 예산이 없으면 0 — 정렬은 [isAhead] 가 따로 맨 뒤로 보낸다. */
    fun percentOf(week: MemberWeek): Int =
        if (week.budget > 0L) Math.round(week.spent * 100.0 / week.budget).toInt() else 0

    /**
     * [a] 가 [b] 보다 앞선(성적이 좋은)가.
     *
     * 사용률은 반올림 정수 대신 원분수로 견준다(a/ab < b/bb ⇔ a·bb < b·ab, 예산 양수).
     * 반올림해서 같아 보이는 두 값도 실제 차이로 갈라, 등수 동률이 억지로 생기지 않게 한다.
     */
    private fun isAhead(a: MemberWeek, b: MemberWeek): Boolean {
        val aHasBudget: Boolean = a.budget > 0L
        val bHasBudget: Boolean = b.budget > 0L
        if (aHasBudget != bHasBudget) return aHasBudget // 예산 있는 쪽이 앞
        if (!aHasBudget) return false // 둘 다 예산 없음 → 동률(앞서지 않음)

        val left: Long = a.spent * b.budget
        val right: Long = b.spent * a.budget
        if (left != right) return left < right
        return a.spent < b.spent
    }
}
