package com.calc.expense

/**
 * [SpendingGrade] 를 사람이 읽는 문구로 옮긴다. 화면·알림이 같은 문구를 쓰도록 여기 한 곳에 모은다.
 *
 * 등급이 채점이라는 건 숨기지 않는다 — 다만 잘잘못을 덧붙이는 말(«잘했어요» 류)은 쓰지 않고
 * 숫자와 등급만 말한다. [StatusText] 가 지켜 온 원칙과 같다.
 */
object GradeText {

    /** 저녁 9시 «오늘 등급» 알림. */
    fun daily(grade: SpendingGrade): StatusLines = when (grade) {
        is SpendingGrade.Graded -> {
            val head = tr("오늘 등급 ${grade.grade}", "Today's grade ${grade.grade}", "Nota de hoy ${grade.grade}")
            val spent: String = StatusText.won(grade.spent)
            val budget: String = StatusText.won(grade.budget)
            StatusLines(
                summary = "$head · $spent / " + tr("기준 $budget", "target $budget", "meta $budget"),
                detail = "$head\n\n" + tr(
                    "오늘 씀 $spent\n하루 기준 $budget",
                    "Spent today $spent\nDaily target $budget",
                    "Gastado hoy $spent\nMeta diaria $budget",
                ),
            )
        }
        SpendingGrade.NoRecord -> {
            val line = tr(
                "오늘은 기록이 없어요 — 등급을 매길 수 없습니다",
                "Nothing logged today — no grade to give",
                "Hoy no hay gastos anotados — no hay nota",
            )
            StatusLines(summary = line, detail = line)
        }
        SpendingGrade.NoBudget -> {
            val line = tr(
                "예산을 정하면 오늘 등급이 나옵니다",
                "Set a budget to get today's grade",
                "Define un presupuesto para ver la nota de hoy",
            )
            StatusLines(summary = line, detail = line)
        }
    }

    /** 지난 며칠(주간 돌아보기) 등급 한 줄. 기존 돌아보기 문구 뒤에 덧붙인다. */
    fun trailing(grade: SpendingGrade): String? = when (grade) {
        is SpendingGrade.Graded -> tr("지난 7일 등급 ${grade.grade}", "Last 7 days grade ${grade.grade}", "Nota de los últimos 7 días ${grade.grade}")
        SpendingGrade.NoRecord, SpendingGrade.NoBudget -> null
    }

    /** 막 끝난 주기 결산 한 줄. */
    fun cycle(grade: SpendingGrade): String? = when (grade) {
        is SpendingGrade.Graded ->
            tr("지난 주기 결산: ", "Last cycle: ", "Ciclo anterior: ") +
                "${grade.grade} · ${StatusText.won(grade.spent)} / ${StatusText.won(grade.budget)}"
        SpendingGrade.NoRecord, SpendingGrade.NoBudget -> null
    }
}
