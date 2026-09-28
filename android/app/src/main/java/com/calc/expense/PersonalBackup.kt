package com.calc.expense

/**
 * 계정에 맡겨 두는 **개인 설정** 한 벌. 재설치하면 이걸 되찾아 온보딩을 다시 겪지 않는다.
 *
 * 공용 곳간 설정(공용 예산·이름·월급날)은 가정 문서([HouseholdSettings])가 따로 갖는다.
 * 여기는 이 사람만의 것 — 개인 예산·이름, 카테고리 칩, 고정비 계획, 월급날(가정이 없을 때 쓴다).
 *
 * 저장소는 `users/{uid}` 문서의 `personal` 칸이다. 이 파일은 그 칸과 오가는 모양만 정하고
 * Firebase 를 부르지 않는다 — 단위 테스트로 고정한다.
 */
data class PersonalBackup(
    val payDay: Int,
    val budget: Long,
    val name: String,
    val categories: List<String>,
    /** [FixedCostCodec] 로 만든 JSON 한 줄. 모양을 두 곳에서 따로 정하지 않으려고 그대로 싣는다. */
    val fixedCosts: String,
    /** 첫 시작 파이프라인을 이미 봤는지([FixedCostStore.wasAsked]). */
    val asked: Boolean,
) {
    /**
     * 올릴 만한 값인지. **개인 예산이 없으면 올리지 않는다** — 재설치 직후 빈 폰이 먼저
     * 올리면 계정에 맡겨 둔 좋은 값을 덮어 버린다. 예산을 정한 사람만 백업이 있다.
     */
    val worthKeeping: Boolean
        get() = budget > 0L

    fun toMap(): Map<String, Any> = mapOf(
        KEY_VERSION to VERSION,
        KEY_PAY_DAY to payDay.toLong(),
        KEY_BUDGET to budget,
        KEY_NAME to name,
        KEY_CATEGORIES to categories,
        KEY_FIXED to fixedCosts,
        KEY_ASKED to asked,
    )

    companion object {
        const val FIELD = "personal"

        private const val VERSION = 1L
        private const val KEY_VERSION = "v"
        private const val KEY_PAY_DAY = "payDay"
        private const val KEY_BUDGET = "budget"
        private const val KEY_NAME = "name"
        private const val KEY_CATEGORIES = "categories"
        private const val KEY_FIXED = "fixedCosts"
        private const val KEY_ASKED = "asked"

        /**
         * 저장소에서 읽은 칸을 되돌린다. 없거나 예산이 없으면 null — 되찾을 것이 없다.
         * Firestore 는 정수를 Long 으로, 목록을 List 로 돌려준다. 모르는 모양은 버린다.
         */
        fun fromMap(raw: Map<*, *>?): PersonalBackup? {
            if (raw == null) return null
            val budget: Long = (raw[KEY_BUDGET] as? Number)?.toLong() ?: return null
            if (budget <= 0L) return null
            // 설정 칸과 같은 다듬기(공백·중복·길이·개수)를 거친다. 칩 이름에는 쉼표가 들어갈 수 없다.
            val categories: List<String> =
                Categories.parse((raw[KEY_CATEGORIES] as? List<*>)?.mapNotNull { it as? String }.orEmpty().joinToString(","))
            return PersonalBackup(
                payDay = Payday.normalize((raw[KEY_PAY_DAY] as? Number)?.toInt() ?: Payday.DEFAULT),
                budget = budget,
                name = (raw[KEY_NAME] as? String).orEmpty().trim().take(Purse.MAX_NAME_LENGTH),
                categories = categories,
                fixedCosts = (raw[KEY_FIXED] as? String).orEmpty(),
                asked = raw[KEY_ASKED] as? Boolean ?: true,
            )
        }

        /** 이 폰의 값으로 한 벌을 만든다. */
        fun of(settings: Settings, categories: List<String>, fixed: FixedCostPlan, asked: Boolean): PersonalBackup =
            PersonalBackup(
                payDay = settings.payDay,
                budget = settings.personal.monthlyBudget,
                name = settings.personal.name,
                categories = categories,
                fixedCosts = FixedCostCodec.encode(fixed),
                asked = asked,
            )

        /**
         * 되찾은 값을 이 폰 설정에 얹는다. 공용 곳간 칸은 건드리지 않는다 — 그건 가정 문서가 준다.
         * 월급날은 되찾되, 가정에 묶여 있으면 뒤이어 가정 값이 덮는다([HouseholdSync.pull]).
         */
        fun applyTo(local: Settings, backup: PersonalBackup): Settings = local.copy(
            payDay = backup.payDay,
            personal = local.personal.copy(monthlyBudget = backup.budget, name = backup.name),
        )
    }
}
