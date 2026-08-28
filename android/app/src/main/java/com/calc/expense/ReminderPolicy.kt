package com.calc.expense

import java.time.LocalTime

/**
 * «적었어?» 리마인더를 언제 보낼지 정하는 순수 규칙. 사용자가 못 박은 방침을 코드로 옮긴다.
 *
 * - 결제 알림 뒤 [WAIT_MINUTES] 분 기다렸다 확인한다.
 * - 그 사이 기록이 있으면 보내지 않는다 (누락이 아니므로).
 * - 재알림 없음 — 한 결제에 한 번만.
 * - 하루 [DAILY_CAP] 회 상한.
 * - [QUIET_START]시~[QUIET_END]시는 무음 — 자는 동안 찌르지 않는다.
 *
 * Android 에 의존하지 않아 단위 테스트로 고정한다.
 */
object ReminderPolicy {

    const val WAIT_MINUTES = 10L
    const val DAILY_CAP = 3
    const val QUIET_START = 22
    const val QUIET_END = 8

    /** 이 시각이 무음 구간(밤 10시~아침 8시)인가. */
    fun isQuietHour(time: LocalTime): Boolean =
        time.hour >= QUIET_START || time.hour < QUIET_END

    /**
     * 지금 리마인더를 보내도 되는가.
     *
     * @param recordedAfterPayment 결제 감지 뒤 기록이 있었으면 true (그러면 보내지 않는다)
     * @param quiet 지금이 무음 구간이면 true
     * @param todayCount 오늘 이미 보낸 리마인더 수
     */
    fun shouldRemind(recordedAfterPayment: Boolean, quiet: Boolean, todayCount: Int): Boolean =
        !recordedAfterPayment && !quiet && todayCount < DAILY_CAP
}
