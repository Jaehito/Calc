package com.calc.expense

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 고정비를 «찾는» 규칙. 여기가 흔들리면 화면이 엉뚱한 것을 고정비라고 우기거나,
 * 정작 월세를 못 찾는다 — 둘 다 사용자가 이 기능을 다시 안 쓰게 만든다.
 */
class RecurringCostsTest {

    /** 월급날 1일 — 주기가 달력 달과 같아져 날짜를 눈으로 읽을 수 있다. */
    private val payDay: Int = 1

    /** 6·7·8월 세 주기. 오늘이 9월 10일일 때 돌아보는 범위다. */
    private val cycles: List<BudgetCycle> =
        RecurringCosts.recentCycles(LocalDate.of(2026, 9, 10), payDay)

    private fun event(
        name: String,
        amount: Long,
        date: String,
        fromRecord: Boolean = false,
    ) = MoneyEvent(name = name, amount = amount, date = LocalDate.parse(date), fromRecord = fromRecord)

    @Test
    fun `끝난 주기 셋만 돌아본다`() {
        // 9월 10일은 9월 주기 한가운데다. 이번 달 월세가 아직 안 나갔을 수 있으므로
        // 진행 중인 주기를 한 칸으로 세면 «아직»과 «원래 없음»이 구별되지 않는다.
        assertEquals(3, cycles.size)
        assertEquals(LocalDate.of(2026, 8, 1), cycles[0].start)
        assertEquals(LocalDate.of(2026, 7, 1), cycles[1].start)
        assertEquals(LocalDate.of(2026, 6, 1), cycles[2].start)
    }

    @Test
    fun `월급날이 달 중간이어도 끝난 주기를 센다`() {
        val paid25: List<BudgetCycle> = RecurringCosts.recentCycles(LocalDate.of(2026, 9, 10), payDay = 25)

        // 9월 10일은 «8월 25일 ~ 9월 24일» 주기 안이다. 그 앞 셋을 본다.
        assertEquals(LocalDate.of(2026, 7, 25), paid25[0].start)
        assertEquals(LocalDate.of(2026, 6, 25), paid25[1].start)
        assertEquals(LocalDate.of(2026, 5, 25), paid25[2].start)
    }

    @Test
    fun `두 주기에 같은 금액이 나오면 고정비로 본다`() {
        val found: List<FixedCostCandidate> = RecurringCosts.of(
            listOf(
                event("행복주택 월세", 550_000L, "2026-07-05"),
                event("행복주택 월세", 550_000L, "2026-08-05"),
            ),
            cycles,
        )

        assertEquals(1, found.size)
        assertEquals("행복주택 월세", found[0].name)
        assertEquals(550_000L, found[0].amount)
        assertEquals(2, found[0].cycles)
    }

    @Test
    fun `한 주기에만 있으면 고정비가 아니다`() {
        // 한 번 산 노트북은 되풀이가 아니다.
        val found: List<FixedCostCandidate> = RecurringCosts.of(
            listOf(event("애플스토어", 1_800_000L, "2026-07-05")),
            cycles,
        )

        assertTrue(found.isEmpty())
    }

    @Test
    fun `금액이 조금 흔들려도 같은 고정비로 본다`() {
        // 관리비·통신비는 달마다 조금씩 다르다. 이걸 다른 항목으로 갈라 버리면
        // 정작 찾아야 할 것을 못 찾는다. 10% 차이는 같은 것으로 본다.
        val found: List<FixedCostCandidate> = RecurringCosts.of(
            listOf(
                event("관리비", 100_000L, "2026-07-20"),
                event("관리비", 110_000L, "2026-08-20"),
            ),
            cycles,
        )

        assertEquals(1, found.size)
    }

    @Test
    fun `금액이 크게 다르면 다른 지출이다`() {
        // 50,000 과 70,000 은 40% 차이다. 같은 가게에서 다른 것을 산 것이지 고정비가 아니다.
        val found: List<FixedCostCandidate> = RecurringCosts.of(
            listOf(
                event("이마트", 50_000L, "2026-07-20"),
                event("이마트", 70_000L, "2026-08-20"),
            ),
            cycles,
        )

        assertTrue(found.isEmpty())
    }

    @Test
    fun `자주 가는 가게는 금액이 같아도 고정비가 아니다`() {
        // 같은 카페를 한 달에 다섯 번 갔다. 금액이 늘 같아도 이건 고정비가 아니다.
        val visits: List<MoneyEvent> = listOf(
            event("메가커피", 5_500L, "2026-07-02"),
            event("메가커피", 5_500L, "2026-07-09"),
            event("메가커피", 5_500L, "2026-07-16"),
            event("메가커피", 5_500L, "2026-08-04"),
            event("메가커피", 5_500L, "2026-08-11"),
        )

        assertTrue(RecurringCosts.of(visits, cycles).isEmpty())
    }

    @Test
    fun `가장 최근 금액을 제안한다`() {
        // 보험료가 올랐으면 오른 값이 다음 달에 나간다. 평균을 내면 둘 다 아닌 숫자가 된다.
        val found: List<FixedCostCandidate> = RecurringCosts.of(
            listOf(
                event("삼성화재", 100_000L, "2026-06-15"),
                event("삼성화재", 100_000L, "2026-07-15"),
                event("삼성화재", 108_000L, "2026-08-15"),
            ),
            cycles,
        )

        assertEquals(108_000L, found[0].amount)
        assertEquals(3, found[0].cycles)
    }

    @Test
    fun `띄어쓰기가 달라도 같은 곳으로 본다`() {
        val found: List<FixedCostCandidate> = RecurringCosts.of(
            listOf(
                event("우리카드 넷플릭스", 17_000L, "2026-07-11"),
                event("우리카드넷플릭스", 17_000L, "2026-08-11"),
            ),
            cycles,
        )

        assertEquals(1, found.size)
    }

    @Test
    fun `이미 적어 둔 고정비는 다시 제안하지 않는다`() {
        val events: List<MoneyEvent> = listOf(
            event("행복주택 월세", 550_000L, "2026-07-05"),
            event("행복주택 월세", 550_000L, "2026-08-05"),
        )

        assertTrue(RecurringCosts.of(events, cycles, known = listOf("행복주택 월세")).isEmpty())
        // 띄어쓰기만 다른 것도 같은 줄이다.
        assertTrue(RecurringCosts.of(events, cycles, known = listOf("행복주택월세")).isEmpty())
    }

    @Test
    fun `너무 적은 금액은 제안하지 않는다`() {
        // 2,000원짜리까지 늘어놓으면 목록이 길어져 정작 큰 것을 못 본다.
        val found: List<FixedCostCandidate> = RecurringCosts.of(
            listOf(
                event("편의점", 2_000L, "2026-07-03"),
                event("편의점", 2_000L, "2026-08-03"),
            ),
            cycles,
        )

        assertTrue(found.isEmpty())
    }

    @Test
    fun `돌아보는 범위 밖의 것은 세지 않는다`() {
        // 3월·4월에 나가고 끊긴 구독은 지금 고정비가 아니다.
        val found: List<FixedCostCandidate> = RecurringCosts.of(
            listOf(
                event("왓챠", 12_900L, "2026-03-10"),
                event("왓챠", 12_900L, "2026-04-10"),
            ),
            cycles,
        )

        assertTrue(found.isEmpty())
    }

    @Test
    fun `기록과 알림을 한 덩어리로 센다`() {
        // 7월엔 손으로 적었고 8월엔 안 적어 알림만 남았다. 사람에게는 같은 통신비다.
        val found: List<FixedCostCandidate> = RecurringCosts.of(
            listOf(
                event("KT 통신비", 55_000L, "2026-07-25", fromRecord = true),
                event("KT 통신비", 55_000L, "2026-08-25"),
            ),
            cycles,
        )

        assertEquals(1, found.size)
        assertTrue(found[0].fromRecord)
    }

    @Test
    fun `알림에서만 본 것은 기록 표시가 없다`() {
        val found: List<FixedCostCandidate> = RecurringCosts.of(
            listOf(
                event("현대해상", 62_000L, "2026-07-08"),
                event("현대해상", 62_000L, "2026-08-08"),
            ),
            cycles,
        )

        assertFalse(found[0].fromRecord)
    }

    @Test
    fun `큰 금액부터 보여준다`() {
        val found: List<FixedCostCandidate> = RecurringCosts.of(
            listOf(
                event("넷플릭스", 17_000L, "2026-07-11"),
                event("넷플릭스", 17_000L, "2026-08-11"),
                event("행복주택 월세", 550_000L, "2026-07-05"),
                event("행복주택 월세", 550_000L, "2026-08-05"),
                event("KT 통신비", 55_000L, "2026-07-25"),
                event("KT 통신비", 55_000L, "2026-08-25"),
            ),
            cycles,
        )

        assertEquals(listOf("행복주택 월세", "KT 통신비", "넷플릭스"), found.map { it.name })
    }

    @Test
    fun `이름이 비면 세지 않는다`() {
        // 파싱이 가맹점을 못 읽은 건들이 «이름 없음» 한 덩어리로 뭉쳐 고정비가 되면 안 된다.
        val found: List<FixedCostCandidate> = RecurringCosts.of(
            listOf(
                event("", 30_000L, "2026-07-05"),
                event("  ", 30_000L, "2026-08-05"),
            ),
            cycles,
        )

        assertTrue(found.isEmpty())
        assertNull(found.firstOrNull())
    }
}
