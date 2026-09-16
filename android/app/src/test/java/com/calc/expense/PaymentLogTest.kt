package com.calc.expense

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * 결제 기록 목록. 수집함과 달리 무시해도 남는 목록이라, 여기서 새는 것이 곧 «못 찾는 고정비»다.
 */
class PaymentLogTest {

    private val now: Long = 1_757_000_000_000L
    private val minute: Long = 60L * 1000L
    private val day: Long = 24L * 60L * 60L * 1000L

    private fun entry(name: String, amount: Long = 10_000L, at: Long = now) =
        PaymentLogEntry(name = name, amount = amount, at = at)

    @Test
    fun `한 결제가 두 알림으로 와도 한 건이다`() {
        // 카드 문자와 은행 앱 푸시가 같은 결제를 각각 올린다. 두 건으로 세면
        // «한 주기에 두 번»이 되어 고정비 판정이 흔들린다.
        val first: List<PaymentLogEntry> =
            PaymentLogs.add(emptyList(), entry("신한카드", 35_656L, now), now)
        val both: List<PaymentLogEntry> =
            PaymentLogs.add(first, entry("신한은행 HUAMAN CAR", 35_656L, now + minute), now)

        assertEquals(1, both.size)
    }

    @Test
    fun `이름을 더 잘 읽은 쪽이 남는다`() {
        // 이름이 반복 판정의 열쇠다. 지는 쪽을 남기면 같은 고정비가 두 이름으로 갈라진다.
        val first: List<PaymentLogEntry> =
            PaymentLogs.add(emptyList(), entry("신한카드", 35_656L, now), now)
        val both: List<PaymentLogEntry> =
            PaymentLogs.add(first, entry("신한은행 HUAMAN CAR", 35_656L, now + minute), now)

        assertEquals("신한은행 HUAMAN CAR", both[0].name)
    }

    @Test
    fun `합칠 때 이른 시각을 남긴다`() {
        // 문자는 결제보다 늦게 온다. 이른 쪽이 실제 결제 시각에 가깝다.
        val first: List<PaymentLogEntry> =
            PaymentLogs.add(emptyList(), entry("우리카드", 9_000L, now), now)
        val both: List<PaymentLogEntry> =
            PaymentLogs.add(first, entry("우리카드 우아한형제들", 9_000L, now + 2 * minute), now)

        assertEquals(now, both[0].at)
    }

    @Test
    fun `금액이 다르면 따로 센다`() {
        val first: List<PaymentLogEntry> = PaymentLogs.add(emptyList(), entry("이마트", 30_000L), now)
        val both: List<PaymentLogEntry> = PaymentLogs.add(first, entry("이마트", 31_000L), now)

        assertEquals(2, both.size)
    }

    @Test
    fun `같은 금액이라도 시간이 멀면 다른 결제다`() {
        // 달마다 나가는 같은 금액의 월세가 한 건으로 뭉쳐 버리면 고정비를 영영 못 찾는다.
        val first: List<PaymentLogEntry> = PaymentLogs.add(emptyList(), entry("월세", 550_000L, now), now)
        val both: List<PaymentLogEntry> =
            PaymentLogs.add(first, entry("월세", 550_000L, now + 30 * day), now + 30 * day)

        assertEquals(2, both.size)
    }

    @Test
    fun `보관 기간이 지난 것은 버린다`() {
        val old: Long = now - (PaymentLogs.KEEP_DAYS + 1) * day
        val items: List<PaymentLogEntry> = PaymentLogs.prune(listOf(entry("옛날", at = old), entry("어제")), now)

        assertEquals(1, items.size)
        assertEquals("어제", items[0].name)
    }

    @Test
    fun `네 주기를 담을 만큼 오래 들고 있다`() {
        // 주기 셋을 판정에 쓰는데 보관이 93일이면 달의 길이에 따라 맨 앞이 잘린다.
        assertTrue(PaymentLogs.KEEP_DAYS >= 31L * RecurringCosts.LOOK_BACK_CYCLES + 20L)
    }

    @Test
    fun `상한을 넘으면 최신부터 남긴다`() {
        val many: List<PaymentLogEntry> =
            (0 until PaymentLogs.MAX_ITEMS + 10).map { entry("가게$it", at = now - it * minute) }
        val kept: List<PaymentLogEntry> = PaymentLogs.prune(many, now)

        assertEquals(PaymentLogs.MAX_ITEMS, kept.size)
        assertEquals("가게0", kept[0].name)
    }

    @Test
    fun `금액이 없는 건은 담지 않는다`() {
        assertTrue(PaymentLogs.add(emptyList(), entry("이상한 알림", amount = 0L), now).isEmpty())
    }

    @Test
    fun `저장했다 읽으면 그대로다`() {
        val items: List<PaymentLogEntry> = listOf(entry("우리카드 우아한형제들", 16_900L), entry("관리비", 120_000L))
        assertEquals(items, PaymentLogCodec.decode(PaymentLogCodec.encode(items)))
    }

    @Test
    fun `읽지 못한 글자는 빈 목록이 된다`() {
        // 예외로 앱을 죽이느니 목록을 잃는 편이 낫다.
        assertTrue(PaymentLogCodec.decode("이건 JSON 이 아니다").isEmpty())
        assertTrue(PaymentLogCodec.decode(null).isEmpty())
    }

    @Test
    fun `시각을 그 기기의 날짜로 바꾼다`() {
        val seoul: ZoneId = ZoneId.of("Asia/Seoul")
        val at: Long = LocalDate.of(2026, 8, 5).atStartOfDay(seoul).toInstant().toEpochMilli()

        val events: List<MoneyEvent> = PaymentLogs.events(listOf(entry("월세", 550_000L, at)), seoul)

        assertEquals(LocalDate.of(2026, 8, 5), events[0].date)
        assertEquals(false, events[0].fromRecord)
    }

    @Test
    fun `지출 기록도 같은 모양이 된다`() {
        val row = ExpenseRow(id = "a", name = "KT 통신비", amount = 55_000L, date = LocalDate.of(2026, 8, 25), category = "통신")

        val event: MoneyEvent = row.toMoneyEvent()

        assertEquals("KT 통신비", event.name)
        assertEquals(55_000L, event.amount)
        assertTrue(event.fromRecord)
    }
}
