package com.calc.expense

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingPaymentTest {

    private val now: Long = 1_757_000_000_000L
    private val day: Long = 24L * 60L * 60L * 1000L

    private fun item(
        id: String,
        amount: Long = 1_000L,
        postedAt: Long = now,
        packageName: String = "com.kakao.talk",
        sender: String = "1588-1234",
    ) = PendingPayment(
        id = id,
        amount = amount,
        merchant = "스타벅스",
        category = "카페",
        packageName = packageName,
        sender = sender,
        postedAt = postedAt,
    )

    @Test
    fun `같은 알림이 갱신돼도 한 건만 남는다`() {
        val first: List<PendingPayment> = PendingPayments.add(emptyList(), item("key1", amount = 1_000L), now)
        val second: List<PendingPayment> = PendingPayments.add(first, item("key1", amount = 2_000L), now)

        assertEquals(1, second.size)
        assertEquals(2_000L, second[0].amount)
    }

    @Test
    fun `최신순으로 세운다`() {
        var items: List<PendingPayment> = emptyList()
        items = PendingPayments.add(items, item("old", postedAt = now - 2 * day), now)
        items = PendingPayments.add(items, item("new", postedAt = now), now)

        assertEquals(listOf("new", "old"), items.map { it.id })
    }

    @Test
    fun `보관 기간이 지난 것은 버린다`() {
        val stale: PendingPayment = item("stale", postedAt = now - (PendingPayments.KEEP_DAYS + 1) * day)
        val fresh: PendingPayment = item("fresh", postedAt = now)

        val kept: List<PendingPayment> = PendingPayments.prune(listOf(stale, fresh), now)

        assertEquals(listOf("fresh"), kept.map { it.id })
    }

    @Test
    fun `상한을 넘으면 오래된 것부터 버린다`() {
        val many: List<PendingPayment> = (0 until PendingPayments.MAX_ITEMS + 10).map { i ->
            item("key$i", postedAt = now - i.toLong() * 1000L)
        }

        val kept: List<PendingPayment> = PendingPayments.prune(many, now)

        assertEquals(PendingPayments.MAX_ITEMS, kept.size)
        assertEquals("key0", kept.first().id)
        assertTrue(kept.none { it.id == "key${PendingPayments.MAX_ITEMS + 5}" })
    }

    @Test
    fun `id 로 하나를 뺀다`() {
        val items: List<PendingPayment> = listOf(item("a"), item("b"))
        assertEquals(listOf("b"), PendingPayments.remove(items, "a").map { it.id })
    }

    @Test
    fun `앱 단위로 치운다`() {
        val items: List<PendingPayment> = listOf(
            item("a", packageName = "com.kakao.talk"),
            item("b", packageName = "com.samsung.android.messaging"),
        )

        val kept: List<PendingPayment> = PendingPayments.removeFrom(items, packageName = "com.kakao.talk")

        assertEquals(listOf("b"), kept.map { it.id })
    }

    @Test
    fun `발신자 단위로 치운다`() {
        val items: List<PendingPayment> = listOf(
            item("a", sender = "1588-1234"),
            item("b", sender = "신한카드"),
        )

        val kept: List<PendingPayment> = PendingPayments.removeFrom(items, sender = "1588-1234")

        assertEquals(listOf("b"), kept.map { it.id })
    }

    @Test
    fun `손으로 적은 기록과 같은 금액이 시간 안에 있으면 치울 후보를 찾는다`() {
        val items: List<PendingPayment> = listOf(item("a", amount = 4_500L, postedAt = now - 5 * 60_000L))

        assertEquals("a", PendingPayments.matchRecorded(items, 4_500L, now))
    }

    @Test
    fun `시간 폭을 벗어나면 치우지 않는다`() {
        val far: Long = now - (PendingPayments.MATCH_WINDOW_MINUTES + 5) * 60_000L
        val items: List<PendingPayment> = listOf(item("a", amount = 4_500L, postedAt = far))

        assertEquals(null, PendingPayments.matchRecorded(items, 4_500L, now))
    }

    @Test
    fun `금액이 다르면 치우지 않는다`() {
        val items: List<PendingPayment> = listOf(item("a", amount = 4_500L, postedAt = now))

        assertEquals(null, PendingPayments.matchRecorded(items, 5_000L, now))
    }

    @Test
    fun `같은 금액이 둘이면 시간이 가장 가까운 하나만 고른다`() {
        // 같은 금액을 하루에 두 번 썼을 때 둘 다 사라지면 한 건을 잃는다.
        val items: List<PendingPayment> = listOf(
            item("far", amount = 4_500L, postedAt = now - 20 * 60_000L),
            item("near", amount = 4_500L, postedAt = now - 2 * 60_000L),
        )

        assertEquals("near", PendingPayments.matchRecorded(items, 4_500L, now))
    }

    @Test
    fun `JSON 왕복에도 값이 그대로다`() {
        val items: List<PendingPayment> = listOf(item("a", amount = 12_000L), item("b", amount = 7_700L))

        val restored: List<PendingPayment> = PendingPaymentCodec.decode(PendingPaymentCodec.encode(items))

        assertEquals(items, restored)
    }

    @Test
    fun `해석할 수 없는 항목만 건너뛴다`() {
        // id 가 없거나 금액이 0 인 항목은 버리고 나머지는 살린다.
        val raw = """[{"amount":1000},{"id":"ok","amount":5000},{"id":"zero","amount":0}]"""

        val restored: List<PendingPayment> = PendingPaymentCodec.decode(raw)

        assertEquals(listOf("ok"), restored.map { it.id })
        assertEquals(5_000L, restored[0].amount)
    }

    @Test
    fun `빈 값이나 깨진 JSON 은 빈 목록이다`() {
        assertEquals(emptyList<PendingPayment>(), PendingPaymentCodec.decode(null))
        assertEquals(emptyList<PendingPayment>(), PendingPaymentCodec.decode(""))
        assertEquals(emptyList<PendingPayment>(), PendingPaymentCodec.decode("{깨진"))
    }
}
