package com.calc.expense

import android.content.Context
import java.time.LocalDate

/** 도감을 새로 채운 결과. 실패해도 [result] 에는 저장해 둔 도감이 들어 있다. */
data class DogamLoad(val result: DogamResult, val error: String? = null)

/**
 * 저장소에서 처음부터 오늘까지의 기록을 읽어 도감을 다시 계산하고 폰에 덧댄다.
 *
 * **개인 곳간만 센다** — 등급과 같은 기준이다([GradeRepository.GRADED]). 공용에는 배우자의 지출과
 * 기록이 섞여서, 내가 아낀 날이 배우자 지출로 깨지거나 내가 안 적은 날이 적은 날이 된다.
 *
 * 로컬 캐시(달별 합계)가 아니라 저장소의 줄을 읽는 이유는 두 가지다 — 캐시는 이번·지난 달만
 * 들고 있어 지난 기록으로 채울 수 없고, 미분류(정리 선반)는 줄마다의 카테고리가 있어야 셀 수 있다.
 *
 * 반드시 백그라운드 스레드에서 부른다.
 */
object DogamRepository {

    fun refresh(context: Context, today: LocalDate = LocalDate.now()): DogamLoad {
        val purses: List<Purse> = PurseAccess.linked(context).filter { it == GradeRepository.GRADED }
        if (purses.isEmpty()) return DogamLoad(DogamStore.load(context))

        val rows = ArrayList<PursedRow>()
        for (purse in purses) {
            val list: List<ExpenseRow> =
                FirestoreExpenseReader.rowsBetween(context, purse, ExpenseExport.EARLIEST, today)
                    ?: return DogamLoad(DogamStore.load(context), tr("기록을 불러오지 못했어요. 네트워크를 확인해 주세요.", "Couldn't load your entries. Check your connection.", "No se pudieron cargar tus gastos. Revisa la conexión."))
            list.mapTo(rows) { PursedRow(purse, it) }
        }

        val settings: Settings = SettingsStore.load(context)
        val input = DogamInput(
            today = today,
            rows = rows,
            purses = purses,
            monthlyBudgets = purses.associateWith { settings.of(it).monthlyBudget },
            payDay = settings.payDay,
            hasFixedCosts = FixedCostStore.load(context).items.isNotEmpty(),
        )
        return DogamLoad(DogamStore.update(context, Dogam.evaluate(input), today))
    }
}
