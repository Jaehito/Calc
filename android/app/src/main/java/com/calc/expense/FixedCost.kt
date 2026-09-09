package com.calc.expense

import org.json.JSONArray
import org.json.JSONObject

/**
 * 매달 손 안 대도 빠져나가는 돈 한 줄. 월세·대출이자·보험·통신·구독 같은 것.
 *
 * **지출로 기록되지 않는다.** 고정비는 «오늘 쓴 돈»이 아니라 «쓸 수 있는 돈을 정하는 재료»다.
 * 지출로 넣으면 월세 나가는 날 하루 등급이 F 가 되고 카테고리 도넛을 월세가 다 먹는다.
 */
data class FixedCostItem(
    val name: String,
    val amount: Long,
)

/**
 * 월급에서 고정비를 뺀 **이번 달 챌린지 금액**을 정하는 계산.
 *
 * 이 값이 그대로 [PurseSettings.monthlyBudget] 이 된다 — 숫자는 하나다. 챌린지 주간 목표는
 * [StatsRepository.dailyBudget] 이 그 예산에서 계산하므로, 여기가 정확해지면 챌린지도 따라
 * 정확해진다. 챌린지용 금액을 따로 두지 않는 이유가 이것이다(같은 숫자를 두 곳에서 계산하면
 * 어긋나는 날 어느 쪽이 맞는지 알 수 없다).
 *
 * Android 에 의존하지 않아 단위 테스트로 고정한다.
 */
data class FixedCostPlan(
    /** 세후 실수령액. 0 이면 아직 안 물어봤거나 건너뛴 것이다. */
    val monthlyIncome: Long = 0L,
    val items: List<FixedCostItem> = emptyList(),
) {
    /** 고정비 합계. 음수 항목은 0 으로 본다 — 빼기가 더하기가 되면 안 된다. */
    val fixedTotal: Long
        get() = items.sumOf { maxOf(0L, it.amount) }

    /**
     * 추천 챌린지 금액 = 월급 − 고정비.
     *
     * 고정비가 월급을 넘으면 0 이다. 음수를 예산으로 넣으면 하루치가 음수가 되어 화면의 모든
     * 숫자가 무너진다 — 그건 앱이 감당할 상황이 아니라 사람이 봐야 할 상황이고,
     * [isOverIncome] 로 화면에 알린다.
     */
    val recommended: Long
        get() = maxOf(0L, monthlyIncome - fixedTotal)

    /** 고정비가 월급 이상인가. 이러면 추천이 0 이라 화면이 그대로 쓰면 안 된다. */
    val isOverIncome: Boolean
        get() = monthlyIncome > 0L && fixedTotal >= monthlyIncome

    /** 추천을 만들 수 있나. 월급을 모르면 뺄 것이 없어 아무 말도 못 한다. */
    val canRecommend: Boolean
        get() = monthlyIncome > 0L && !isOverIncome

    /** 사용자가 이 파이프라인을 한 번이라도 채웠나. 건너뛴 사람과 가르는 기준이다. */
    val isEmpty: Boolean
        get() = monthlyIncome <= 0L && items.isEmpty()
}

/**
 * 고정비 목록 규칙. 저장소와 떼어 두어 단위 테스트로 고정한다.
 *
 * 같은 이름을 두 번 넣는 것은 막지 않는다 — 보험이 둘일 수 있고, 그걸 앱이 판단할 수 없다.
 * 대신 이름이 비었거나 금액이 0 이하인 줄은 버린다(화면에서 «+ 항목 추가»만 누르고 안 채운 줄).
 */
object FixedCosts {

    /** 한 사람이 들 만한 고정비 항목 수의 상한. 넘으면 화면이 스크롤 지옥이 된다. */
    const val MAX_ITEMS = 20

    /** 이름 길이 상한. 화면 한 줄에 이름과 금액이 같이 들어가야 한다. */
    const val MAX_NAME_LENGTH = 12

    /**
     * 첫 시작에서 미리 깔아 두는 항목 이름. **금액은 비워 둔다** — 사람마다 다르고,
     * 짐작한 금액이 예산에 들어가면 그 뒤 모든 숫자가 그만큼 틀린다.
     *
     * 나이·직업으로 금액을 추측하지 않는 이유가 이것이다. 사용자는 자기 월세를 이미 안다.
     */
    val SUGGESTED: List<String> = listOf("월세", "대출이자", "보험", "통신", "구독")

    /** 저장·계산에 쓸 수 있게 다듬는다. 빈 줄을 버리고, 이름을 자르고, 상한까지만 남긴다. */
    fun clean(items: List<FixedCostItem>): List<FixedCostItem> =
        items.asSequence()
            .map { FixedCostItem(it.name.trim().take(MAX_NAME_LENGTH), it.amount) }
            .filter { it.name.isNotEmpty() && it.amount > 0L }
            .take(MAX_ITEMS)
            .toList()

    /** 화면이 처음 보여줄 빈 줄들. 사용자가 «무엇을 적는 칸인지» 를 이름으로 알아본다. */
    fun suggestedRows(): List<FixedCostItem> =
        SUGGESTED.map { FixedCostItem(name = it, amount = 0L) }
}

/**
 * 고정비 계획을 JSON 한 덩어리로 옮긴다.
 *
 * [PendingPaymentCodec] 과 같은 판단 — 읽지 못한 항목은 통째로 사라지는 편이 낫지,
 * 예외로 앱을 죽이면 안 된다.
 */
object FixedCostCodec {

    fun encode(plan: FixedCostPlan): String {
        val array = JSONArray()
        for (item in plan.items) {
            array.put(JSONObject().put("name", item.name).put("amount", item.amount))
        }
        return JSONObject()
            .put("income", plan.monthlyIncome)
            .put("items", array)
            .toString()
    }

    fun decode(raw: String?): FixedCostPlan {
        if (raw.isNullOrBlank()) return FixedCostPlan()
        val root: JSONObject = try {
            JSONObject(raw)
        } catch (_: Exception) {
            return FixedCostPlan()
        }

        val array: JSONArray = root.optJSONArray("items") ?: JSONArray()
        val items = ArrayList<FixedCostItem>()
        for (i in 0 until array.length()) {
            val obj: JSONObject = array.optJSONObject(i) ?: continue
            val name: String = obj.optString("name", "")
            val amount: Long = obj.optLong("amount", 0L)
            if (name.isEmpty() || amount <= 0L) continue
            items.add(FixedCostItem(name = name, amount = amount))
        }

        return FixedCostPlan(
            monthlyIncome = maxOf(0L, root.optLong("income", 0L)),
            items = FixedCosts.clean(items),
        )
    }
}
