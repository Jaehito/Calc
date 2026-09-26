package com.calc.expense

import java.time.LocalTime

/**
 * **밤에는 찌르지 않는다.** 자는 동안 알림을 올려 봐야 아침에 쌓인 것을 치우는 일만 남는다.
 *
 * 예전에는 «적었어?» 리마인더의 규칙(몇 분 기다릴지·하루 몇 번까지)도 함께 들고 있었는데,
 * 그 알림이 결제 배너로 바뀌면서 규칙 자체가 없어졌다. 배너는 결제를 본 그 순간 떠서
 * 금액과 가게 이름을 바로 말하므로 «기다렸다 물어본다»가 필요 없다.
 *
 * 남은 것은 무음 구간 하나뿐이고, 지금은 [CardRefreshTime] 이 쓴다.
 *
 * Android 에 의존하지 않아 단위 테스트로 고정한다.
 */
object ReminderPolicy {

    const val QUIET_START = 22
    const val QUIET_END = 8

    /** 이 시각이 무음 구간(밤 10시~아침 8시)인가. */
    fun isQuietHour(time: LocalTime): Boolean =
        time.hour >= QUIET_START || time.hour < QUIET_END
}
