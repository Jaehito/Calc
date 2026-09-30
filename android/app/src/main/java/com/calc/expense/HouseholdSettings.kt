package com.calc.expense

/**
 * 가정 문서(`households/{id}`)에 두는, **두 폰이 같이 쓰는** 설정.
 *
 * 공용 곳간의 예산·이름·주기 경계(목표날 다음 날). 공용 주기는 두 폰이 같아야 한다 — 다르면
 * 같은 공용 지출을 두고 서로 다른 «이번 주기»를 보게 된다.
 *
 * 개인 곳간 예산과 개인 주기는 여기 없다. 그건 사람마다 다르다. (예전에는 한 날을 두 지갑이
 * 같이 썼다. 문서의 `payDay` 칸은 이름 그대로 두고 이제 공용 주기만 뜻한다.)
 */
data class SharedSettings(
    val payDay: Int,
    val sharedBudget: Long,
    val sharedName: String,
)

/**
 * 로컬 [Settings] 와 가정 문서 사이를 옮기는 규칙. Android·Firestore 에 의존하지 않아 단위
 * 테스트로 고정한다.
 *
 * **마지막에 저장한 값이 이긴다.** 두 사람이 거의 동시에 다른 값을 적는 일은 드물고, 그걸 병합할
 * 방법도 없다(예산 두 개 중 무엇이 «맞는» 값인가). 그래서 한쪽이 저장하면 가정 문서를 통째로
 * 덮고, 다른 폰은 앱을 열 때 그걸 받아 로컬을 덮는다.
 */
object HouseholdSettings {

    const val KEY_PAY_DAY = "payDay"
    const val KEY_SHARED_BUDGET = "sharedBudget"
    const val KEY_SHARED_NAME = "sharedName"

    fun of(settings: Settings): SharedSettings = SharedSettings(
        payDay = Payday.normalize(settings.sharedPayDay),
        sharedBudget = settings.shared.monthlyBudget.coerceAtLeast(0L),
        sharedName = settings.shared.name.trim(),
    )

    /** 가정 값을 로컬 설정에 덮는다. 개인 곳간(개인 주기 포함)과 그 밖의 값은 그대로 둔다. */
    fun apply(local: Settings, remote: SharedSettings): Settings = local.copy(
        sharedPayDay = Payday.normalize(remote.payDay),
        shared = local.shared.copy(
            monthlyBudget = remote.sharedBudget.coerceAtLeast(0L),
            name = remote.sharedName.trim().take(Purse.MAX_NAME_LENGTH),
        ),
    )

    fun differs(local: Settings, remote: SharedSettings): Boolean = of(local) != of(apply(local, remote))

    /**
     * 가정 문서 필드에서 읽는다. 월급날이 없으면 null — 이 기능 전에 만든 가정이라 아직 아무도
     * 올리지 않은 것이다. 그때는 로컬을 건드리지 않는다(빈 값으로 덮으면 예산이 0 이 된다).
     *
     * Firestore 는 정수를 Long 으로 돌려준다. 다른 숫자 타입이 와도 받는다.
     */
    fun fromFields(fields: Map<String, Any?>): SharedSettings? {
        val payDay: Number = fields[KEY_PAY_DAY] as? Number ?: return null
        return SharedSettings(
            payDay = Payday.normalize(payDay.toInt()),
            sharedBudget = (fields[KEY_SHARED_BUDGET] as? Number)?.toLong()?.coerceAtLeast(0L) ?: 0L,
            sharedName = (fields[KEY_SHARED_NAME] as? String).orEmpty(),
        )
    }

    fun toFields(shared: SharedSettings): Map<String, Any> = mapOf(
        KEY_PAY_DAY to shared.payDay,
        KEY_SHARED_BUDGET to shared.sharedBudget,
        KEY_SHARED_NAME to shared.sharedName,
    )

    /**
     * 설정을 저장한 뒤 가정에 올려야 하는가.
     *
     * 같이 쓰는 값이 바뀌었을 때만 올린다 — 안 바뀐 값을 올리면, 이 폰이 아직 받지 못한 상대의
     * 새 값을 옛 값으로 덮을 수 있다. 예외는 가정 문서를 읽어 봤더니 비어 있었을 때
     * ([remoteMissing])다. 그때는 누군가 처음 한 번 채워야 한다.
     */
    fun shouldPush(before: Settings, after: Settings, remoteMissing: Boolean): Boolean =
        remoteMissing || of(before) != of(after)
}
