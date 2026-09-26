package com.calc.expense

import android.content.Context
import android.content.SharedPreferences
import java.time.LocalDate

/**
 * 도감을 폰에 둔다. 핀 꽃과 핀 날, 최고 기록, 쌓인 횟수 — 전부 [Dogam.merge] 로만 쓴다.
 *
 * 저장해 두는 이유는 두 가지다. 탭을 열자마자 네트워크 없이 그리려고, 그리고 **줄지 않게**
 * 하려고. 매번 새로 계산만 하면 예산을 낮추거나 지난 기록을 지웠을 때 꽃이 지고 기록이 줄어든다.
 *
 * 따로 두 목록을 기억한다.
 * - 알린 꽃: 「어제 등급」 팝업에 한 번 붙였거나 도감 탭에서 이미 본 꽃
 * - 본 꽃: 도감 탭에서 NEW 표시를 떼도 되는 꽃
 */
object DogamStore {

    private const val FILE = "dogam"

    /**
     * 세는 기준이 바뀌면 올린다. 저장된 버전이 다르면 도감을 비우고 처음부터 다시 센다 —
     * 줄지 않게 덧대는 규칙([Dogam.merge]) 때문에, 옛 기준으로 핀 꽃과 기록이 새 기준 위에 남는다.
     *
     * 2: 개인+공용 합산에서 개인 곳간만으로 바꿈.
     */
    private const val VERSION = 2
    private const val KEY_VERSION = "version"
    private const val BLOOM_PREFIX = "bloom."
    private const val KEY_ANNOUNCED = "announced"
    private const val KEY_SEEN = "seen"
    private const val KEY_EVALUATED = "evaluatedOn"
    private const val KEY_SAVED_DAY = "best.savedDay"
    private const val KEY_KEEP_RUN = "best.keepRun"
    private const val KEY_NO_SPEND_RUN = "best.noSpendRun"
    private const val KEY_CHEAPEST_WEEK = "best.cheapestWeek"
    private const val KEY_RECORD_RUN = "best.recordRun"
    private const val KEY_S_DAYS = "count.sDays"
    private const val KEY_NO_SPEND_DAYS = "count.noSpendDays"
    private const val KEY_KEPT_DAYS = "count.keptDays"
    private const val KEY_KEPT_WEEKS = "count.keptWeeks"

    /** 버전이 다르면 비우고 연다. 어디서 읽든 옛 기준의 꽃이 한 번도 보이지 않게. */
    private fun prefs(context: Context): SharedPreferences {
        val p: SharedPreferences = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        if (p.getInt(KEY_VERSION, 0) != VERSION) p.edit().clear().putInt(KEY_VERSION, VERSION).commit()
        return p
    }

    fun load(context: Context): DogamResult {
        val p: SharedPreferences = prefs(context)
        val blooms = LinkedHashMap<Plant, LocalDate>()
        for (plant in Plant.entries) {
            val day: LocalDate = parseDay(p.getString(BLOOM_PREFIX + plant.key, null)) ?: continue
            blooms[plant] = day
        }
        return DogamResult(
            blooms = blooms,
            bests = PersonalBests(
                savedDay = BestCodec.decode(p.getString(KEY_SAVED_DAY, null)),
                keepRun = BestCodec.decode(p.getString(KEY_KEEP_RUN, null)),
                noSpendRun = BestCodec.decode(p.getString(KEY_NO_SPEND_RUN, null)),
                cheapestWeek = BestCodec.decode(p.getString(KEY_CHEAPEST_WEEK, null)),
                recordRun = BestCodec.decode(p.getString(KEY_RECORD_RUN, null)),
            ),
            tallies = Tallies(
                sDays = p.getInt(KEY_S_DAYS, 0),
                noSpendDays = p.getInt(KEY_NO_SPEND_DAYS, 0),
                keptDays = p.getInt(KEY_KEPT_DAYS, 0),
                keptWeeks = p.getInt(KEY_KEPT_WEEKS, 0),
            ),
        )
    }

    /**
     * 새로 계산한 도감을 덧대 저장하고, 덧댄 결과를 돌려준다.
     *
     * **처음 채울 때**(한 번도 계산한 적 없을 때) 핀 꽃은 전부 알린 것·본 것으로 둔다. 지난 기록으로
     * 한꺼번에 채운 여덟 송이를 「어제 핀 꽃」처럼 팝업에 붙이거나 NEW 를 여덟 개 다는 건 소음이다.
     */
    fun update(context: Context, fresh: DogamResult, today: LocalDate): DogamResult {
        val firstTime: Boolean = evaluatedOn(context) == null
        val merged: DogamResult = Dogam.merge(load(context), fresh)

        val edit: SharedPreferences.Editor = prefs(context).edit()
        for ((plant, day) in merged.blooms) edit.putString(BLOOM_PREFIX + plant.key, day.toString())
        putBest(edit, KEY_SAVED_DAY, merged.bests.savedDay)
        putBest(edit, KEY_KEEP_RUN, merged.bests.keepRun)
        putBest(edit, KEY_NO_SPEND_RUN, merged.bests.noSpendRun)
        putBest(edit, KEY_CHEAPEST_WEEK, merged.bests.cheapestWeek)
        putBest(edit, KEY_RECORD_RUN, merged.bests.recordRun)
        edit.putInt(KEY_S_DAYS, merged.tallies.sDays)
        edit.putInt(KEY_NO_SPEND_DAYS, merged.tallies.noSpendDays)
        edit.putInt(KEY_KEPT_DAYS, merged.tallies.keptDays)
        edit.putInt(KEY_KEPT_WEEKS, merged.tallies.keptWeeks)
        edit.putString(KEY_EVALUATED, today.toString())
        if (firstTime) {
            val keys: Set<String> = merged.blooms.keys.map { it.key }.toSet()
            edit.putStringSet(KEY_ANNOUNCED, keys)
            edit.putStringSet(KEY_SEEN, keys)
        }
        edit.apply()
        return merged
    }

    /** 마지막으로 계산한 날. null 이면 한 번도 채운 적 없다. */
    fun evaluatedOn(context: Context): LocalDate? =
        parseDay(prefs(context).getString(KEY_EVALUATED, null))

    /** 피었는데 아직 알리지 않은 꽃. 핀 순서대로. */
    fun unannounced(context: Context): List<Plant> {
        val announced: Set<String> = keys(context, KEY_ANNOUNCED)
        return load(context).blooms.entries
            .filter { it.key.key !in announced }
            .sortedBy { it.value }
            .map { it.key }
    }

    fun markAnnounced(context: Context, plants: Collection<Plant>) = addKeys(context, KEY_ANNOUNCED, plants)

    /** 피었는데 도감에서 아직 못 본 꽃. */
    fun unseen(context: Context): Set<Plant> {
        val seen: Set<String> = keys(context, KEY_SEEN)
        return load(context).blooms.keys.filter { it.key !in seen }.toSet()
    }

    fun markSeen(context: Context, plants: Collection<Plant>) = addKeys(context, KEY_SEEN, plants)

    /** 계정이 바뀌면 앞사람의 꽃이다. */
    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }

    private fun keys(context: Context, key: String): Set<String> =
        prefs(context).getStringSet(key, null)?.toSet() ?: emptySet()

    private fun addKeys(context: Context, key: String, plants: Collection<Plant>) {
        if (plants.isEmpty()) return
        val next: Set<String> = keys(context, key) + plants.map { it.key }
        prefs(context).edit().putStringSet(key, next).apply()
    }

    private fun putBest(edit: SharedPreferences.Editor, key: String, best: Best?) {
        val raw: String? = BestCodec.encode(best)
        if (raw == null) edit.remove(key) else edit.putString(key, raw)
    }

    private fun parseDay(raw: String?): LocalDate? =
        raw?.let {
            try {
                LocalDate.parse(it)
            } catch (_: Exception) {
                null
            }
        }
}
