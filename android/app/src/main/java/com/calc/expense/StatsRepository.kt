package com.calc.expense

import android.content.Context
import java.time.LocalDate
import java.time.YearMonth

/** 통계 화면이 쓰는 한 벌. 기간 비교는 로컬 캐시, 카테고리 막대는 노션에서 온다. */
data class StatsData(
    val recent7: Long,
    val prev7: Long,
    val thisMonth: Long,
    val lastMonth: Long,
    /** 카테고리 막대가 어느 달인지 (이번 달/지난 달). */
    val categoryMonthLabel: String,
    val categories: List<CategorySlice>,
    val categoryTotal: Long,
    /** 지난 14일 일별 합계(오래된→최신). 앞 7일·뒤 7일로 주간 추이 막대를 그린다. */
    val daily14: List<Long>,
    val loadingCategories: Boolean,
    val error: String?,
)

/**
 * 통계 데이터를 모은다.
 *
 * 기간 총액 비교(최근 7일·이번 달)는 **로컬 캐시**로 즉시 만든다 — 네트워크가 없어도 뜬다.
 * 카테고리 막대만 노션 «카테고리» 속성을 조회한다. 곳간(개인/공용)은 합쳐서 본다.
 */
object StatsRepository {

    /** 네트워크 없이 기간 비교만. 카테고리는 아직 비어 있고 [loadingCategories] = true. */
    fun localOnly(context: Context, today: LocalDate = LocalDate.now()): StatsData {
        val prev7End: LocalDate = today.minusDays(7)
        val thisMonth: YearMonth = YearMonth.from(today)
        val lastMonth: YearMonth = thisMonth.minusMonths(1)

        val daily: MutableList<Long> = ArrayList(14)
        var d: Int = 13
        while (d >= 0) {
            val day: LocalDate = today.minusDays(d.toLong())
            daily.add(spentBetween(context, day, day))
            d--
        }

        return StatsData(
            recent7 = spentBetween(context, today.minusDays(6), today),
            prev7 = spentBetween(context, prev7End.minusDays(6), prev7End),
            thisMonth = spentInMonth(context, thisMonth),
            lastMonth = spentInMonth(context, lastMonth),
            categoryMonthLabel = "이번 달",
            categories = emptyList(),
            categoryTotal = 0L,
            daily14 = daily,
            loadingCategories = true,
            error = null,
        )
    }

    /** 모든 연결된 곳간을 합쳐 [from]~[to](양끝 포함) 지출을 더한다. */
    fun spentBetween(context: Context, from: LocalDate, to: LocalDate): Long {
        val purses: List<Purse> = SettingsStore.load(context).linkedPurses
        var total: Long = 0L
        var day: LocalDate = from
        while (!day.isAfter(to)) {
            for (purse in purses) total += SpendingCache.spentOn(context, purse, day)
            day = day.plusDays(1)
        }
        return total
    }

    private fun spentInMonth(context: Context, month: YearMonth): Long =
        spentBetween(context, month.atDay(1), month.atEndOfMonth())

    /**
     * 그 달의 카테고리별 합계를 노션에서 가져온다. 네트워크를 타므로 백그라운드에서 부른다.
     *
     * 두 곳간이 다른 DB 를 쓰면 각 DB 를 한 번씩 조회해 합친다(같은 DB 면 한 번만). 성공하면
     * (카테고리 합계, null), 실패하면 (빈 맵, 오류 문구).
     */
    fun fetchCategories(context: Context, month: YearMonth): Pair<Map<String, Long>, String?> {
        val settings = SettingsStore.load(context)
        val merged = LinkedHashMap<String, Long>()
        val seenDatabases = HashSet<String>()

        for (purse in settings.linkedPurses) {
            val target: NotionTarget = settings.target(purse) ?: continue
            if (target.categoryProp.isBlank()) continue
            if (!seenDatabases.add(target.databaseId)) continue

            when (val r = NotionClient(target).queryMonthCategories(month)) {
                is NotionClient.CategoryOutcome.Err -> return emptyMap<String, Long>() to r.message
                is NotionClient.CategoryOutcome.Ok ->
                    for ((name, amount) in r.totals) merged[name] = (merged[name] ?: 0L) + amount
            }
        }
        return merged to null
    }
}
