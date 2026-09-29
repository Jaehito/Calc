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

    /**
     * 어림한 금액 — 문장 속에서 읽는 숫자. «17만 원», «8.7만 원», «9,800원».
     *
     * 통계의 목표(606,662원)처럼 원 단위까지 딱 떨어지는 숫자는 사람이 정한 목표로 읽히지 않는다.
     * 10만 원부터는 만 단위 정수, 1만~10만은 소수 한 자리, 그 아래는 그대로 적는다. 부호는 떼고 쓴다.
     */
    fun approxWon(amount: Long): String {
        val a: Long = kotlin.math.abs(amount)
        return when (L10n.lang) {
            Lang.KO -> if (a < 10_000L) won(a) else man(a, decimals = if (a < 100_000L) 1 else 0) + "만 원"
            Lang.EN -> if (a < 1_000L) won(a) else "₩" + kilo(a) + "K"
            Lang.ES -> if (a < 1_000L) won(a) else "₩" + kilo(a) + " mil"
        }
    }

    /** 막대 위처럼 좁은 자리의 짧은 금액. «12만», «11.9만», «8.7만». 만 원 아래는 쉼표 숫자. */
    fun approxShort(amount: Long): String {
        val a: Long = kotlin.math.abs(amount)
        return when (L10n.lang) {
            Lang.KO -> if (a < 10_000L) figure(a) else man(a, decimals = if (a < 1_000_000L) 1 else 0) + "만"
            Lang.EN -> if (a < 1_000L) figure(a) else kilo(a) + "K"
            Lang.ES -> if (a < 1_000L) figure(a) else kilo(a) + " mil"
        }
    }

    /** 만 단위로 반올림한 숫자. 소수 자리가 0 이면 떼어 «12.0» 대신 «12». */
    private fun man(amount: Long, decimals: Int): String = roundTo(amount / 10_000.0, decimals)

    /** 천 단위(K). 100K 부터는 정수, 그 아래는 소수 한 자리. */
    private fun kilo(amount: Long): String = roundTo(amount / 1_000.0, if (amount < 100_000L) 1 else 0)

    private fun roundTo(value: Double, decimals: Int): String {
        if (decimals <= 0) return L10n.figure(Math.round(value))
        val scaled: Long = Math.round(value * 10)
        val whole: Long = scaled / 10
        val tenth: Long = scaled % 10
        return if (tenth == 0L) L10n.figure(whole) else L10n.figure(whole) + "." + tenth
    }

    /** 원을 붙이지 않은 쉼표 숫자. 히어로 숫자처럼 «원» 단위를 따로 붙이는 곳에서 쓴다. */
    fun figure(amount: Long): String = format(amount)

    /**
     * 홈 카드와 기록 창 아래의 짧은 기간 줄 왼쪽. «10월 14일까지 67,170원» / «… 초과».
     * 남은 날은 [daysLeft] 로 따로 그린다 — 오른쪽 끝에 초록으로 붙기 때문이다.
     */
    fun periodLeft(snapshot: LedgerSnapshot): String {
        val target: String = snapshot.targetDay.format(L10n.monthDay())
        val left: Long = snapshot.untilTarget
        return if (left >= 0L) {
            tr("${target}까지 ${won(left)}", "${won(left)} until $target", "${won(left)} hasta el $target")
        } else {
            tr("${target}까지 ${won(-left)} 초과", "${won(-left)} over until $target", "${won(-left)} de más hasta el $target")
        }
    }

    /** 기간 줄 오른쪽. «15일 남음». */
    fun daysLeft(snapshot: LedgerSnapshot): String =
        tr("${snapshot.daysLeft}일 남음", "${L10n.days(snapshot.daysLeft)} left", "quedan ${L10n.days(snapshot.daysLeft)}")

    /**
     * 기록 창에서 금액을 적는 동안 보이는 줄. 이걸 적으면 오늘 얼마가 남는지(또는 넘는지).
     * [available] 은 지금 오늘 쓸 수 있는 돈이다(이미 넘었으면 음수).
     */
    fun afterRecord(available: Long, amount: Long): String {
        val left: Long = available - amount
        return if (left >= 0L) {
            tr("기록하면 오늘 ${won(left)} 남아요", "After this, ${won(left)} left today", "Después de esto, quedan ${won(left)} hoy")
        } else {
            tr("기록하면 오늘 ${won(-left)} 넘어요", "After this, ${won(-left)} over today", "Después de esto, ${won(-left)} de más hoy")
        }
    }

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
