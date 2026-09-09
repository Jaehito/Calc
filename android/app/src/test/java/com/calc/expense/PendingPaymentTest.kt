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
        merchant: String = "스타벅스",
        category: String = "카페",
        issuer: String = "",
    ) = PendingPayment(
        id = id,
        amount = amount,
        merchant = merchant,
        category = category,
        packageName = packageName,
        sender = sender,
        postedAt = postedAt,
        issuer = issuer,
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

    // ── 한 결제가 두 알림으로 올 때 (문자앱 + 은행앱) ──────────────────────────────

    @Test
    fun `같은 금액이 몇 초 차이로 또 오면 한 건으로 합친다`() {
        // 실제로 물린 것: 문자앱(발신번호)과 은행앱이 같은 1원 이체를 각각 올려 팝업이 두 번 떴다.
        val fromSms: PendingPayment =
            item("sms", amount = 1L, postedAt = now, sender = "1577-8000", merchant = "최재호")
        val fromBank: PendingPayment = item(
            "bank",
            amount = 1L,
            postedAt = now + 20_000L,
            packageName = "com.kakaobank.channel",
            sender = "카카오뱅크",
            merchant = "카카오뱅크 최재호",
        )

        var items: List<PendingPayment> = PendingPayments.add(emptyList(), fromSms, now)
        items = PendingPayments.add(items, fromBank, now)

        assertEquals(1, items.size)
        // 이름을 더 잘 읽은 쪽이 남는다.
        assertEquals("카카오뱅크 최재호", items[0].merchant)
        // 시각은 이른 쪽 — 문자는 결제보다 늦게 온다.
        assertEquals(now, items[0].postedAt)
    }

    @Test
    fun `합칠 때 빈 카테고리는 진 쪽에서 채운다`() {
        val rich: PendingPayment = item("a", amount = 9_000L, merchant = "이마트성수점", category = "")
        val poor: PendingPayment = item("b", amount = 9_000L, postedAt = now + 5_000L, merchant = "이마트", category = "마트")

        var items: List<PendingPayment> = PendingPayments.add(emptyList(), rich, now)
        items = PendingPayments.add(items, poor, now)

        assertEquals(1, items.size)
        assertEquals("이마트성수점", items[0].merchant)
        assertEquals("마트", items[0].category)
    }

    @Test
    fun `이름을 못 읽은 쪽은 읽은 쪽에 진다`() {
        val blank: PendingPayment = item("a", amount = 3_000L, merchant = "")
        val named: PendingPayment = item("b", amount = 3_000L, postedAt = now + 1_000L, merchant = "메가커피")

        var items: List<PendingPayment> = PendingPayments.add(emptyList(), blank, now)
        items = PendingPayments.add(items, named, now)

        assertEquals(listOf("메가커피"), items.map { it.merchant })
    }

    @Test
    fun `시간 폭 밖의 같은 금액은 다른 결제다`() {
        // 같은 커피를 아침저녁으로 두 번 사면 두 건이어야 한다.
        val far: Long = now + (PendingPayments.DUPLICATE_WINDOW_MINUTES + 1) * 60_000L
        var items: List<PendingPayment> = PendingPayments.add(emptyList(), item("a", amount = 4_500L), now)
        items = PendingPayments.add(items, item("b", amount = 4_500L, postedAt = far), far)

        assertEquals(2, items.size)
    }

    @Test
    fun `금액이 다르면 잇따라 와도 합치지 않는다`() {
        var items: List<PendingPayment> = PendingPayments.add(emptyList(), item("a", amount = 4_500L), now)
        items = PendingPayments.add(items, item("b", amount = 5_500L, postedAt = now + 3_000L), now)

        assertEquals(2, items.size)
    }

    // ── 출처 이름 — 발신번호는 이름 노릇을 못 한다 ────────────────────────────────

    @Test
    fun `발신자가 번호면 내용에서 읽은 은행명을 보인다`() {
        val fromSms: PendingPayment = item("a", sender = "1577-8000", issuer = "신한은행")
        assertEquals("신한은행", fromSms.sourceName)
    }

    @Test
    fun `발신자가 이름이면 그대로 보인다`() {
        assertEquals("카카오뱅크", item("a", sender = "카카오뱅크", issuer = "카카오뱅크").sourceName)
    }

    @Test
    fun `번호인데 은행명도 모르면 번호라도 보인다`() {
        // 아무것도 안 보이는 것보다는 낫다 — «안 보기» 를 누를 단서가 필요하다.
        assertEquals("1577-8000", item("a", sender = "1577-8000", issuer = "").sourceName)
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
