package com.calc.expense

import android.content.Context
import java.time.LocalDate
import java.time.YearMonth

/** 통계 화면이 쓰는 한 벌. 기간 비교는 로컬 캐시, 카테고리 막대는 저장소에서 온다. */
data class StatsData(
    val recent7: Long,
    val prev7: Long,
    /** 최근 7일 목표 (그 곳간의 하루치 × 7). 예산 미설정이면 0. */
    val week7Budget: Long,
    /** 카테고리 막대가 어느 주기인지 (이번 주기/지난 주기). */
    val categoryCycleLabel: String,
    /** 그 주기가 언제부터 언제까지인지 — «9월 15일 ~ 10월 14일». */
    val categoryCycleRange: String,
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
 * 카테고리 막대만 저장소를 조회한다.
 *
 * 곳간(개인/공용)은 **나눠서** 본다 — 홈의 토글과 같은 곳간이다. 합쳐 보면 공용 목표날과
 * 개인 목표날이 다를 때 «이번 주기»가 어느 주기인지 말할 수 없고, 목표 금액도 섞인다.
 */
object StatsRepository {

    /** 네트워크 없이 기간 비교만. 카테고리는 아직 비어 있고 [loadingCategories] = true. */
    fun localOnly(context: Context, purse: Purse, today: LocalDate = LocalDate.now()): StatsData {
        val prev7End: LocalDate = today.minusDays(7)
        val cycle: BudgetCycle = cycleOf(context, purse, today)

        val daily: MutableList<Long> = ArrayList(14)
        var d: Int = 13
        while (d >= 0) {
            val day: LocalDate = today.minusDays(d.toLong())
            daily.add(spentBetween(context, purse, day, day))
            d--
        }

        return StatsData(
            recent7 = spentBetween(context, purse, today.minusDays(6), today),
            prev7 = spentBetween(context, purse, prev7End.minusDays(6), prev7End),
            week7Budget = dailyBudget(context, purse, today) * 7L,
            categoryCycleLabel = tr("이번 주기", "This cycle", "Este ciclo"),
            categoryCycleRange = StatusText.cycleRange(cycle),
            categories = emptyList(),
            categoryTotal = 0L,
            daily14 = daily,
            loadingCategories = true,
            error = null,
        )
    }

    /** 그 곳간의 목표날로 센 [today] 가 든 주기. 공용은 공용 목표날을 따른다. */
    fun cycleOf(context: Context, purse: Purse, today: LocalDate): BudgetCycle =
        Payday.cycleOf(today, SettingsStore.load(context).payDayOf(purse))

    /**
     * 그 곳간의 **하루치**. 주간 목표는 여기에 7을 곱한다.
     *
     * 기준은 그 주기의 하루치 기본값([Budget.baseRate])이다 — 초과로 줄어든 오늘의 실제
     * dailyRate 가 아니다. 흔들리지 않는 기준이라야 주끼리 견줄 수 있다.
     * [GradeRepository] 도 같은 기준을 쓴다.
     */
    fun dailyBudget(context: Context, purse: Purse, today: LocalDate): Long {
        val settings: Settings = SettingsStore.load(context)
        val cycle: BudgetCycle = Payday.cycleOf(today, settings.payDayOf(purse))
        return Budget.baseRate(settings.of(purse).monthlyBudget, cycle)
    }

    /** [purse] 곳간의 [from]~[to](양끝 포함) 지출을 더한다. */
    fun spentBetween(context: Context, purse: Purse, from: LocalDate, to: LocalDate): Long {
        var total: Long = 0L
        var day: LocalDate = from
        while (!day.isAfter(to)) {
            total += SpendingCache.spentOn(context, purse, day)
            day = day.plusDays(1)
        }
        return total
    }

    /**
     * 그 **주기**의 카테고리별 합계. 저장소를 읽으므로 백그라운드에서 부른다.
     *
     * 달력 달이 아니라 주기로 센다. 월급날이 15일인 사람에게 「이번 달」은 아무 의미가 없다 —
     * 곳간도 챌린지도 등급도 전부 월급날부터 다음 월급날 전날까지를 한 덩어리로 보는데,
     * 카테고리만 1일부터 세면 같은 화면의 숫자들이 서로 다른 기간을 말하게 된다.
     *
     * [purse] 곳간 하나만 읽는다. 성공하면 (카테고리 합계, null), 실패하면 (빈 맵, 오류 문구).
     *
     * **카테고리가 없는 줄도 담는다.** 예전에는 빈 카테고리를 건너뛰었는데, 그러면 그 돈이
     * 도넛에서 통째로 사라지고 합계도 실제보다 작아진다. 사용자 눈에는 모든 지출이 저절로
     * 분류된 것처럼 보이지만 실제로는 분류 안 된 돈이 화면에서 없어진 것이다.
     * 빈 이름은 [CategoryBreakdown] 이 «미분류»로 묶는다 — 주기 리포트도 같은 규칙을 쓴다.
     */
    fun fetchCategories(context: Context, purse: Purse, cycle: BudgetCycle): Pair<Map<String, Long>, String?> {
        val rows: List<ExpenseRow> =
            FirestoreExpenseReader.rowsBetween(context, purse, cycle.start, cycle.lastDay)
                ?: return emptyMap<String, Long>() to tr("카테고리를 불러오지 못했어요", "Couldn't load categories", "No se pudieron cargar las categorías")
        val merged = LinkedHashMap<String, Long>()
        for (row in rows) {
            merged[row.category] = (merged[row.category] ?: 0L) + row.amount
        }
        return merged to null
    }

    /**
     * 통계 달력 한 달치. [totals] 는 그 달 날짜별 합계 — 캐시([SpendingCache])든 저장소에서 막 읽은 것이든.
     * 넘김은 그날이 든 주기의 하루치([dailyBudget] 과 같은 기준)로 가린다.
     */
    fun calendar(context: Context, purse: Purse, month: YearMonth, totals: Map<LocalDate, Long>, today: LocalDate = LocalDate.now()): StatsCalendarUi {
        val settings: Settings = SettingsStore.load(context)
        val monthly: Long = settings.of(purse).monthlyBudget
        val payDay: Int = settings.payDayOf(purse)
        return StatsCalendarUi(
            month = month,
            cells = MonthCalendar.cells(
                month = month,
                totals = totals,
                dailyTarget = { day -> Budget.baseRate(monthly, Payday.cycleOf(day, payDay)) },
                today = today,
                firstDay = L10n.firstDayOfWeek,
            ),
            total = totals.filterKeys { YearMonth.from(it) == month }.values.sum(),
            canNext = month.isBefore(YearMonth.from(today)),
        )
    }
}
