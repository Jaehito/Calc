package com.calc.expense

import java.time.LocalDate

/**
 * 통계 맨 아래 «내 기록» 칸에 올릴 기록 하나.
 *
 * 칸이 작아서 하나만 고른다. 고르는 순서:
 * 1. 며칠 안에 새로 세운 기록([Dogam.isFresh]) — 둘 이상이면 가장 최근에 세운 것
 * 2. 없으면 기록 최장 연속 — 적기만 하면 늘어나는, 누구나 가진 기록이라서
 * 3. 그것도 없으면 아무 기록이나 있는 것 중 첫째
 *
 * 하나도 없으면 null — 칸은 «아직 없어요»를 보인다.
 */
enum class RecordKind { SAVED_DAY, KEEP_RUN, NO_SPEND_RUN, CHEAPEST_WEEK, RECORD_RUN }

data class RecordHighlight(val kind: RecordKind, val best: Best) {

    /** 칸에 크게 쓰는 숫자. 연속은 «30일», 금액은 «38,400원». */
    val value: String
        get() = when (kind) {
            RecordKind.SAVED_DAY, RecordKind.CHEAPEST_WEEK -> StatusText.won(best.value)
            else -> "${best.value}" + tr("일", " days", " días")
        }

    /** 숫자 아래 작게 쓰는 기록 이름. [RecordsCard] 의 줄 이름과 같다. */
    val title: String
        get() = when (kind) {
            RecordKind.SAVED_DAY -> tr("가장 많이 아낀 날", "Biggest saving day", "Día de mayor ahorro")
            RecordKind.KEEP_RUN -> tr("하루치 최장 연속", "Longest on-budget streak", "Racha más larga en presupuesto")
            RecordKind.NO_SPEND_RUN -> tr("무지출 최장 연속", "Longest no-spend streak", "Racha más larga sin gastos")
            RecordKind.CHEAPEST_WEEK -> tr("가장 적게 쓴 주", "Cheapest week", "Semana más barata")
            RecordKind.RECORD_RUN -> tr("기록 최장 연속", "Longest logging streak", "Racha más larga anotando")
        }

    companion object {
        fun of(bests: PersonalBests, today: LocalDate): RecordHighlight? {
            val all: List<RecordHighlight> = listOfNotNull(
                bests.recordRun?.let { RecordHighlight(RecordKind.RECORD_RUN, it) },
                bests.keepRun?.let { RecordHighlight(RecordKind.KEEP_RUN, it) },
                bests.savedDay?.let { RecordHighlight(RecordKind.SAVED_DAY, it) },
                bests.noSpendRun?.let { RecordHighlight(RecordKind.NO_SPEND_RUN, it) },
                bests.cheapestWeek?.let { RecordHighlight(RecordKind.CHEAPEST_WEEK, it) },
            )
            val fresh: RecordHighlight? = all
                .filter { Dogam.isFresh(it.best, today) }
                .maxByOrNull { it.best.to }
            return fresh ?: all.firstOrNull()
        }
    }
}
