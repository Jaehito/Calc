package com.calc.expense

import java.time.LocalDate

/**
 * 나무 탭의 벚꽃나무. 물을 준 만큼 자란다.
 *
 * - [water]: 아직 안 준 물(기록 창·나무 탭 오른쪽 위 💧 숫자). 기록하거나 좋은 하루를 마치면 는다([WaterRules]).
 * - [given]: 지금까지 나무에 준 물. 단계는 이것만 본다([TreeGrowth.stageOf]).
 * - [plantedOn]: 처음 물을 센 날. 이 날 전의 하루들은 물을 주지 않는다 — 기능이 생기기 전 기록으로
 *   한꺼번에 다 자라 버리면 키우는 재미가 없다.
 * - [settledThrough]: 하루 보너스(좋은 날·안 쓴 날)를 어느 날까지 줬나. 같은 날을 두 번 주지 않는다.
 *
 * Android 없이 테스트한다. 저장은 [TreeStore], 계정 백업은 [TreeBackupSync].
 */
data class TreeState(
    val water: Int = 0,
    val given: Int = 0,
    val plantedOn: LocalDate? = null,
    val settledThrough: LocalDate? = null,
) {
    val stage: Int get() = TreeGrowth.stageOf(given)

    fun toMap(): Map<String, Any> = buildMap {
        put(KEY_WATER, water.toLong())
        put(KEY_GIVEN, given.toLong())
        plantedOn?.let { put(KEY_PLANTED, it.toString()) }
        settledThrough?.let { put(KEY_SETTLED, it.toString()) }
    }

    companion object {
        const val FIELD = "tree"
        private const val KEY_WATER = "water"
        private const val KEY_GIVEN = "given"
        private const val KEY_PLANTED = "plantedOn"
        private const val KEY_SETTLED = "settledThrough"

        /** 계정에서 읽은 칸을 되돌린다. Firestore 는 정수를 Long 으로 준다. 모르는 모양은 버린다. */
        fun fromMap(raw: Map<*, *>?): TreeState? {
            if (raw == null) return null
            val given: Int = (raw[KEY_GIVEN] as? Number)?.toInt() ?: return null
            return TreeState(
                water = maxOf(0, (raw[KEY_WATER] as? Number)?.toInt() ?: 0),
                given = maxOf(0, given),
                plantedOn = parseDay(raw[KEY_PLANTED]),
                settledThrough = parseDay(raw[KEY_SETTLED]),
            )
        }

        /**
         * 재설치 뒤 계정에 맡겨 둔 나무와 이 폰의 나무를 합친다. 준 물은 큰 쪽(같은 나무를 두 번
         * 키운 게 아니다), 남은 물은 더한다(되찾기 전 이 폰에서 번 물도 잃지 않게).
         */
        fun merge(local: TreeState, remote: TreeState): TreeState = TreeState(
            water = local.water + remote.water,
            given = maxOf(local.given, remote.given),
            plantedOn = listOfNotNull(local.plantedOn, remote.plantedOn).minOrNull(),
            settledThrough = listOfNotNull(local.settledThrough, remote.settledThrough).maxOrNull(),
        )

        private fun parseDay(raw: Any?): LocalDate? = try {
            (raw as? String)?.let { LocalDate.parse(it) }
        } catch (_: Exception) {
            null
        }
    }
}

/** 단계: 씨앗 → 떡잎 → 본잎 → 묘목 → 어린 나무 → 나무 → 꽃봉오리 → 만개. */
object TreeGrowth {

    /** 각 단계가 시작되는 준 물. */
    val STEPS: List<Int> = listOf(0, 3, 10, 25, 50, 100, 180, 300)

    val LAST: Int = STEPS.size - 1

    /** 다 자란 뒤에는 이만큼 줄 때마다 꽃잎이 흩날린다. */
    const val PETAL_EVERY = 30

    fun stageOf(given: Int): Int = STEPS.indexOfLast { given >= it }.coerceAtLeast(0)

    /** 다음 단계까지 더 줘야 하는 물. 다 자랐으면 null. */
    fun toNext(given: Int): Int? {
        val stage: Int = stageOf(given)
        if (stage == LAST) return null
        return STEPS[stage + 1] - given
    }

    /** 이번 단계 안에서 얼마나 왔나(0~1). 다 자랐으면 1. */
    fun progress(given: Int): Float {
        val stage: Int = stageOf(given)
        if (stage == LAST) return 1f
        val from: Int = STEPS[stage]
        return (given - from).toFloat() / (STEPS[stage + 1] - from)
    }

    /** 이 한 방울로 꽃잎이 흩날리나. 다 자란 뒤 [PETAL_EVERY] 방울마다. */
    fun petalsAt(givenAfter: Int): Boolean =
        givenAfter > STEPS[LAST] && (givenAfter - STEPS[LAST]) % PETAL_EVERY == 0
}

/** 하루 보너스로 받은 물. 아침 «물을 받았어요» 팝업이 보여 준다. */
data class WaterGift(val goodDays: Int = 0, val quietDays: Int = 0) {
    val total: Int get() = goodDays * WaterRules.GOOD_DAY + quietDays * WaterRules.QUIET_DAY
    val isEmpty: Boolean get() = total == 0
}

/**
 * 물을 버는 규칙.
 *
 * - 기록 한 건 [PER_RECORD] (하루 제한 없음). **내가 적은 건이면 공용도 센다** — 배우자가 적은 건은
 *   세지 않는다. 그 기록을 지우면 도로 뺀다(0 밑으로는 안 내려간다).
 * - 아래 하루 보너스는 개인 지갑만 본다(등급·도감과 같다) — 공용은 배우자 지출이 섞인다.
 * - A 등급 이상으로 마친 날 [GOOD_DAY]
 * - 한 푼도 안 쓴 날 [QUIET_DAY]. 기록이 없는 날이 곧 안 쓴 날인데, 깜빡하고 안 적은 날과 가를 수 없다.
 *   그래서 **기록한 날 뒤로 이틀까지만**([MAX_QUIET_RUN]) 친다 — 도감 무지출과 같은 틈 기준
 *   ([Dogam.MAX_GAP_DAYS])이다. 그보다 긴 틈은 앱을 안 쓴 것으로 본다.
 *
 * 하루 보너스는 하루가 끝나야 알 수 있어 다음 날 앱을 열 때 준다. 며칠 만에 열면 밀린 날을 합쳐 주되
 * [MAX_BACK_DAYS] 일까지만 거슬러 본다.
 */
object WaterRules {

    const val PER_RECORD = 1
    const val GOOD_DAY = 2
    const val QUIET_DAY = 3
    const val MAX_QUIET_RUN = 2
    const val MAX_BACK_DAYS = 7L

    /** 이 등급 이상이면 좋은 날이다. */
    val GOOD: Grade = Grade.A

    /**
     * 아직 안 센 날들(어제까지)을 세어 보너스를 낸다.
     *
     * @param recorded 그 날 개인 지갑에 기록이 있나
     * @param grade 그 날 등급. 예산이 없거나 기록이 없으면 null
     * @return 보너스와, 어디까지 셌는지(다음에 이어서 셀 날의 전날)
     */
    fun settle(
        state: TreeState,
        today: LocalDate,
        recorded: (LocalDate) -> Boolean,
        grade: (LocalDate) -> Grade?,
    ): Pair<WaterGift, LocalDate> {
        val yesterday: LocalDate = today.minusDays(1)
        val planted: LocalDate = state.plantedOn ?: return WaterGift() to yesterday
        val earliest: LocalDate = listOfNotNull(
            planted,
            state.settledThrough?.plusDays(1),
            today.minusDays(MAX_BACK_DAYS),
        ).max()

        var good = 0
        var quiet = 0
        var day: LocalDate = earliest
        while (!day.isAfter(yesterday)) {
            if (recorded(day)) {
                val g: Grade? = grade(day)
                if (g != null && g.ordinal <= GOOD.ordinal) good++
            } else if ((1..MAX_QUIET_RUN).any { recorded(day.minusDays(it.toLong())) }) {
                quiet++
            }
            day = day.plusDays(1)
        }
        return WaterGift(good, quiet) to yesterday
    }
}
