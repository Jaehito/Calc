package com.calc.expense

import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * 상시 카드를 **한 시간에 한 번 다시 올릴** 시각 계산.
 *
 * 잠금화면 알림 목록은 다른 앱이 새 알림을 낼 때마다 밀린다. 카드가 맨 아래로 내려가면
 * 적으려고 스크롤부터 해야 하는데, 그건 «잠금화면에서 바로 적는다»는 이 앱의 전제를 깬다.
 * 중요도·대화 승격으로 «억지로 위에» 붙잡아 두는 대신, 한 시간마다 같은 알림을 조용히
 * 다시 올려 목록 위로 되돌린다 — 안드로이드는 갱신된 `when` 으로 순위를 다시 매긴다.
 *
 * **소리도 배너도 없다.** 채널이 무음이고 [NotificationHelper.show] 가 setOnlyAlertOnce 를
 * 걸어 두므로, 이미 떠 있는 카드를 다시 올려도 사용자를 방해하지 않는다.
 *
 * 밤([ReminderPolicy.QUIET_START]시~[ReminderPolicy.QUIET_END]시)에는 쉰다. 자는 동안 순위를
 * 지킬 이유가 없고, 결제 리마인더가 이미 쓰는 무음 구간과 규칙을 하나로 둔다.
 *
 * Android·WorkManager 에 의존하지 않아 단위 테스트로 고정한다.
 */
object CardRefreshTime {

    /** 다시 올리는 간격. */
    const val EVERY_HOURS = 1L

    /**
     * [from] 에서 다음으로 카드를 올릴 때까지 남은 시간.
     *
     * 한 시간 뒤가 무음 구간이면 그날 밤은 통째로 건너뛰고 **아침 [ReminderPolicy.QUIET_END]시**로
     * 민다. 밤새 한 시간마다 깨어나 아무것도 안 하고 다시 예약하는 낭비를 없앤다.
     */
    fun untilNextRefresh(from: LocalDateTime): Duration {
        val next: LocalDateTime = from.plusHours(EVERY_HOURS)
        if (!ReminderPolicy.isQuietHour(next.toLocalTime())) return Duration.between(from, next)
        return Duration.between(from, nextMorning(from))
    }

    /** 지금 카드를 올려도 되는 시각인가. 워커가 늦게 깨어났을 때 밤에 올리지 않도록 다시 본다. */
    fun canRefresh(at: LocalTime): Boolean = !ReminderPolicy.isQuietHour(at)

    /** [from] 이후 처음 오는 아침 [ReminderPolicy.QUIET_END]시. */
    private fun nextMorning(from: LocalDateTime): LocalDateTime {
        var morning: LocalDateTime = from
            .withHour(ReminderPolicy.QUIET_END)
            .withMinute(0)
            .withSecond(0)
            .withNano(0)
        if (!morning.isAfter(from)) morning = morning.plusDays(1)
        return morning
    }
}
