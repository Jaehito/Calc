package com.calc.expense

import android.content.Context
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * 챌린지가 겨루는 «이번 주»의 경계와, 내 이번 주 성적을 뽑는 곳.
 *
 * 주는 월요일에 시작해 일요일에 끝난다(ISO). [key] 는 Firestore 문서 경로에, [label] 은 화면에 쓴다.
 */
object ChallengeWeek {

    private val LabelFormat: DateTimeFormatter =
        DateTimeFormatter.ofPattern("M월 d일", Locale.KOREA)

    /** 이번 주 월요일. */
    fun start(today: LocalDate): LocalDate =
        today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** 이번 주 일요일. */
    fun end(today: LocalDate): LocalDate = start(today).plusDays(6)

    /** Firestore 주간 문서 키. 정렬 가능한 월요일 날짜 문자열(예: 2026-08-24). */
    fun key(today: LocalDate): String = start(today).toString()

    /** 화면용 기간 문구(예: 8월 24일 – 8월 30일). */
    fun label(today: LocalDate): String =
        "${start(today).format(LabelFormat)} – ${end(today).format(LabelFormat)}"

    /** 이번 주가 며칠 남았나(오늘 제외, 일요일이면 0). */
    fun daysLeft(today: LocalDate): Int =
        (end(today).toEpochDay() - today.toEpochDay()).toInt().coerceAtLeast(0)

    /**
     * 내 이번 주 성적: (이번 주 지출 합계, 이번 주 예산).
     *
     * 지출은 연결된 모든 곳간을 합쳐 이번 주 월요일부터 오늘까지 더한다(로컬 캐시). 예산은
     * 곳간별 하루치([Budget.baseRate])의 7배 합이다 — 초과로 출렁이는 오늘 하루치가 아니라
     * 흔들리지 않는 기준치라야 주끼리 공정하게 견줄 수 있다.
     */
    fun myWeek(context: Context, today: LocalDate = LocalDate.now()): Pair<Long, Long> {
        val settings: Settings = SettingsStore.load(context)
        val spent: Long = StatsRepository.spentBetween(context, start(today), today)

        val cycle: BudgetCycle = Payday.cycleOf(today, settings.payDay)
        var budget: Long = 0L
        for (purse in settings.linkedPurses) {
            budget += Budget.baseRate(settings.of(purse).monthlyBudget, cycle) * 7L
        }
        return spent to budget
    }
}
