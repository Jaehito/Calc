package com.calc.expense

import java.time.format.DateTimeFormatter

/** 알림 한 줄과 펼쳤을 때의 본문. */
data class StatusLines(val summary: String, val detail: String)

/**
 * 기록 결과를 사람이 읽는 문구로 옮긴다.
 *
 * 문구가 이 앱의 보상이다. 입력한 순간 돌아오는 답이 없으면 기록은 순수 비용이 되고,
 * 순수 비용은 반드시 중단된다. 그래서 조립을 화면에서 떼어내 테스트로 고정한다.
 *
 * 나를 채점하는 표현은 쓰지 않는다 — 느낌표도, 잘했다는 말도, 몇 건 밀렸다는 말도 없다.
 * 돈에 관한 숫자만 말한다.
 */
object StatusText {

    private val DAY_FORMAT: DateTimeFormatter get() = L10n.monthDay()

    private fun format(amount: Long): String = L10n.figure(amount)

    fun won(amount: Long): String = L10n.won(amount)

    /**
     * 주기를 사람이 읽는 한 줄로 — «9월 15일 ~ 10월 14일».
     *
     * 월급날이 달 중간이면 「이번 주기」가 달력 달과 어긋난다. 어느 날짜부터 어느 날짜까지인지
     * 화면이 말해 주지 않으면, 사용자는 그 숫자가 무엇을 센 것인지 알 수 없다.
     */
    fun cycleRange(cycle: BudgetCycle): String =
        cycle.start.format(DAY_FORMAT) + " ~ " + cycle.lastDay.format(DAY_FORMAT)

    /** 원을 붙이지 않은 쉼표 숫자. 히어로 숫자처럼 «원» 단위를 따로 붙이는 곳에서 쓴다. */
    fun figure(amount: Long): String = format(amount)

    /**
     * 목표일(다음 월급날 전날)까지의 여유. 오늘 하루가 아니라 주기 전체를 보는 줄이다.
     * 오늘 쓸 수 있는 돈이 "지금 괜찮나"라면 이건 "이 페이스로 가도 되나"에 답한다.
     */
    fun untilTarget(snapshot: LedgerSnapshot): String {
        val target: String = snapshot.targetDay.format(DAY_FORMAT)
        val left: Long = snapshot.untilTarget

        if (left < 0L) {
            return tr(
                "${target}까지 ${won(-left)} 초과 · 남은 ${snapshot.daysLeft}일",
                "${won(-left)} over until $target · ${L10n.days(snapshot.daysLeft)} left",
                "${won(-left)} de más hasta el $target · quedan ${L10n.days(snapshot.daysLeft)}",
            )
        }
        return tr(
            "${target}까지 ${won(left)} · 남은 ${snapshot.daysLeft}일 (하루 ${format(snapshot.perDayLeft)})",
            "${won(left)} until $target · ${L10n.days(snapshot.daysLeft)} left (${format(snapshot.perDayLeft)}/day)",
            "${won(left)} hasta el $target · quedan ${L10n.days(snapshot.daysLeft)} (${format(snapshot.perDayLeft)}/día)",
        )
    }

    /**
     * 기록이 성공했을 때.
     *
     * @param snapshot null 이면 그 곳간에 예산이 아직 없다
     * @param showPurse 곳간이 둘 다 연결됐을 때만 true — 하나뿐이면 이름을 붙이지 않는다
     */
    fun recorded(
        name: String,
        amount: Long,
        snapshot: LedgerSnapshot?,
        time: String,
        showPurse: Boolean = false,
    ): StatusLines {
        val tag: String = if (showPurse && snapshot != null) "${snapshot.label} " else ""
        val head = tr(
            "✓ $name ${won(amount)} 기록됨 · $time",
            "✓ $name ${won(amount)} logged · $time",
            "✓ $name ${won(amount)} anotado · $time",
        )

        if (snapshot == null) {
            return StatusLines(
                summary = head,
                detail = head + "\n\n" + tr(
                    "앱에서 예산을 정하면 오늘 쓸 수 있는 돈도 같이 보여요.",
                    "Set a budget in the app to see what you can spend today.",
                    "Define un presupuesto en la app para ver lo que puedes gastar hoy.",
                ),
            )
        }

        val available: Long = snapshot.available
        if (available < 0L) {
            return StatusLines(
                summary = "✓ $name ${format(amount)} · " + tr(
                    "${tag}오늘 ${format(-available)} 초과",
                    "${tag}today ${format(-available)} over",
                    "${tag}hoy ${format(-available)} de más",
                ),
                detail = head + "\n\n" + tr(
                    "${tag}오늘 ${won(-available)} 초과" +
                        "\n곳간을 다 쓰고 넘친 만큼은 남은 날에 나눠서 빼요.",
                    "${tag}today ${won(-available)} over" +
                        "\nThe savings are used up; the extra is spread over the remaining days.",
                    "${tag}hoy ${won(-available)} de más" +
                        "\nEl ahorro se agotó; el exceso se reparte entre los días que quedan.",
                ) + "\n\n" + untilTarget(snapshot),
            )
        }

        return StatusLines(
            summary = "✓ $name ${format(amount)} · " +
                if (tag.isEmpty()) tr(
                    "오늘 쓸 수 있는 돈 ${format(available)}",
                    "left today ${format(available)}",
                    "disponible hoy ${format(available)}",
                )
                else tr("${tag}오늘 ${format(available)}", "${tag}today ${format(available)}", "${tag}hoy ${format(available)}"),
            detail = head + "\n\n" + leftToday(tag, snapshot) + "\n" + untilTarget(snapshot),
        )
    }

    /**
     * 펼친 알림의 «오늘 쓸 수 있는 돈» 한 줄.
     *
     * 예전에는 «하루치 + 곳간 − 오늘» 계산식 줄을 붙였다. 알림에서는 식이 설명이 아니라
     * 소음이었다 — 근거는 홈 카드의 세 칸(하루치·곳간·오늘 씀)이 이미 보여 준다.
     */
    private fun leftToday(tag: String, snapshot: LedgerSnapshot): String = tr(
        "${tag}오늘 쓸 수 있는 돈 ${won(snapshot.available)}",
        "${tag}Left to spend today ${won(snapshot.available)}",
        "${tag}Disponible hoy ${won(snapshot.available)}",
    )

    /**
     * 빠른 입력 화면의 결과 줄. 한 줄 고정이라 카드 높이가 출렁이지 않는다.
     *
     * 한 건만 적었으면 개수를 붙이지 않는다 — 셀 것이 없을 때 세지 않는다.
     */
    fun entered(name: String, amount: Long, count: Int): String {
        val head = "✓ $name ${format(amount)}"
        return if (count <= 1) head else head + tr(" · ${count}건째", " · #$count", " · n.º $count")
    }

    /**
     * 지난 주기 이맘때와 견준 한 줄. 과거의 나와 겨루는 재미가 이 앱이 노리는 동기다.
     *
     * [LedgerSnapshot.vsLastCycle] 이 null(비교할 기록 없음)이면 줄을 만들지 않는다.
     * 채점하지 않는다 — 덜 썼다·더 썼다는 사실만 말하고 잘잘못을 붙이지 않는다.
     */
    fun comparison(snapshot: LedgerSnapshot): String? {
        val diff: Long = snapshot.vsLastCycle ?: return null
        return when {
            diff < 0L -> tr(
                "지난 주기 이맘때보다 ${won(-diff)} 덜 썼어요",
                "${won(-diff)} less than this point last cycle",
                "${won(-diff)} menos que a estas alturas del ciclo anterior",
            )
            diff > 0L -> tr(
                "지난 주기 이맘때보다 ${won(diff)} 더 썼어요",
                "${won(diff)} more than this point last cycle",
                "${won(diff)} más que a estas alturas del ciclo anterior",
            )
            else -> tr(
                "지난 주기 이맘때와 똑같이 쓰고 있어요",
                "Exactly the same as this point last cycle",
                "Igual que a estas alturas del ciclo anterior",
            )
        }
    }

    /** 파싱이나 기록이 실패했을 때. 실패는 그 자리에서 무엇이 잘못됐는지 말한다. */
    fun failed(message: String, time: String): StatusLines {
        val line = "✗ $message · $time"
        return StatusLines(summary = line, detail = line)
    }

    /** 곳간 하나를 읽지 못했을 때. */
    fun loadFailed(label: String): String = tr(
        "$label 지갑을 불러오지 못했어요",
        "Couldn't load the $label wallet",
        "No se pudo cargar la cartera $label",
    )

    /** 로그인이 풀려 기록할 곳이 없을 때. */
    fun signedOut(): String = tr(
        "로그인이 풀렸어요. 앱을 열어 다시 로그인해 주세요",
        "You've been signed out. Open the app and sign in again",
        "Se cerró tu sesión. Abre la app y vuelve a iniciar sesión",
    )

    /**
     * 예상하지 못한 실패를 사람 말로. 예외 원문(대개 영어 스택 메시지)은 사용자에게 아무 뜻이
     * 없어서 보이지 않는다 — 할 수 있는 일(잠시 뒤 다시)만 말한다.
     */
    @Suppress("UNUSED_PARAMETER")
    fun error(e: Throwable): String = tr(
        "잠깐 문제가 생겼어요. 조금 뒤 다시 해 주세요",
        "Something went wrong. Please try again in a moment",
        "Algo salió mal. Vuelve a intentarlo en un momento",
    )

    /** 지난 며칠간 한 곳간이 쓴 합계. 주간 돌아보기에 쓴다. */
    data class WeeklySpend(val label: String, val total: Long)

    /**
     * 주 1회 «돌아보기» 알림 문구. 지난 [days] 일 동안 곳간별로 얼마 썼는지 담는다.
     *
     * 채점하지 않는다 — 얼마 썼다는 사실과 하루 평균만 말한다. 예산 대비 잘잘못은 붙이지 않는다.
     * 접힌 줄(summary)에는 곳간별 합계를, 펼친 본문(detail)에는 하루 평균까지 둔다.
     */
    fun weekly(spends: List<WeeklySpend>, days: Int = 7): StatusLines {
        if (spends.isEmpty()) {
            val line = tr("지난 ${days}일 기록이 없어요", "Nothing logged in the last $days days", "Nada anotado en los últimos $days días")
            return StatusLines(summary = line, detail = line)
        }

        val lastDays: String = tr("지난 ${days}일", "Last $days days", "Últimos $days días")
        val summary: String =
            if (spends.size > 1) {
                "$lastDays " + spends.joinToString(" · ") { "${it.label} ${format(it.total)}" }
            } else {
                "$lastDays ${won(spends[0].total)}"
            }

        val body: String = spends.joinToString("\n") { s ->
            "${s.label} ${won(s.total)} · " +
                tr("하루 평균", "daily avg.", "media diaria") + " ${format(s.total / days)}"
        }
        val title: String = tr("지난 ${days}일 돌아보기", "Your last $days days", "Tus últimos $days días")
        return StatusLines(summary = summary, detail = "$title\n\n$body")
    }
}
