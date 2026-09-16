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
    fun `손으로 적은 제목은 금액이 달라도 고정비로 본다`() {
        // 석 달 내리 「관리비」라고 적었다면 그건 관리비다. 그 제목은 사용자가 직접 고른 말이라
        // 앱이 짐작한 이름보다 믿을 만하다. 달마다 액수가 달라지는 것들(관리비·전기·학원비)이
        // 정작 사용자가 모르는 고정비인데, 금액 폭을 걸면 그것들만 골라서 빠진다.
        val found: List<FixedCostCandidate> = RecurringCosts.of(
            listOf(
                event("관리비", 98_000L, "2026-06-20", fromRecord = true),
                event("관리비", 131_000L, "2026-07-20", fromRecord = true),
                event("관리비", 112_000L, "2026-08-20", fromRecord = true),
            ),
            cycles,
        )

        assertEquals(1, found.size)
        assertEquals("관리비", found[0].name)
        assertTrue(found[0].fromRecord)
    }

    @Test
    fun `금액이 흔들리면 평균을 제안한다`() {
        // 「지금 값」이라 할 만한 것이 없을 때 마지막 달을 집으면, 그 달이 유난히 많았을 뿐일 수 있다.
        val found: List<FixedCostCandidate> = RecurringCosts.of(
            listOf(
                event("관리비", 98_000L, "2026-06-20", fromRecord = true),
                event("관리비", 131_000L, "2026-07-20", fromRecord = true),
                event("관리비", 112_000L, "2026-08-20", fromRecord = true),
            ),
            cycles,
        )

        assertEquals(113_667L, found[0].amount)
        assertTrue(found[0].averaged)
    }

    @Test
    fun `금액이 고르면 평균이 아니라 최근 값이다`() {
        // 보험료가 올랐으면 오른 값이 다음 달에 나간다. 평균을 내면 둘 다 아닌 숫자가 된다.
        val found: List<FixedCostCandidate> = RecurringCosts.of(
            listOf(
                event("보험", 100_000L, "2026-07-15", fromRecord = true),
                event("보험", 108_000L, "2026-08-15", fromRecord = true),
            ),
            cycles,
        )

        assertEquals(108_000L, found[0].amount)
        assertFalse(found[0].averaged)
    }

    @Test
    fun `알림에서만 온 것은 금액이 흔들리면 여전히 아니다`() {
        // 앱이 문구에서 짐작한 이름이라 「같은 곳」이라는 근거가 이름 하나뿐이다.
        // 금액까지 제각각이면 같은 가게에서 다른 것을 산 것일 뿐이다.
        val found: List<FixedCostCandidate> = RecurringCosts.of(
            listOf(
                event("우리카드 이마트성수점", 50_000L, "2026-07-20"),
                event("우리카드 이마트성수점", 70_000L, "2026-08-20"),
            ),
            cycles,
        )

        assertTrue(found.isEmpty())
    }

    @Test
    fun `손으로 적었어도 한 주기에 여러 번이면 아니다`() {
        // 「점심」을 달마다 여러 번 적는다. 제목이 같아도 이건 고정비가 아니다 —
        // 기록 쪽 잣대를 느슨하게 한 뒤에도 이 줄이 목록을 지킨다.
        val lunches: List<MoneyEvent> = listOf(
            event("점심", 9_000L, "2026-07-02", fromRecord = true),
            event("점심", 12_000L, "2026-07-09", fromRecord = true),
            event("점심", 8_500L, "2026-07-16", fromRecord = true),
            event("점심", 11_000L, "2026-08-04", fromRecord = true),
            event("점심", 9_500L, "2026-08-11", fromRecord = true),
        )

        assertTrue(RecurringCosts.of(lunches, cycles).isEmpty())
    }

    @Test
    fun `평균이 너무 적으면 제안하지 않는다`() {
        val found: List<FixedCostCandidate> = RecurringCosts.of(
            listOf(
                event("편의점", 2_000L, "2026-07-03", fromRecord = true),
                event("편의점", 6_000L, "2026-08-03", fromRecord = true),
            ),
            cycles,
        )

        assertTrue(found.isEmpty())
    }

    @Test
    fun `기록이 하나라도 섞이면 느슨한 잣대를 쓴다`() {
        // 7월엔 손으로 적었고 8월엔 알림만 남았다. 사용자가 그 이름을 한 번이라도 직접 적었으면
        // 그 이름은 믿을 만하다 — 안 적은 달이 있다고 잣대가 되돌아가면 안 된다.
        val found: List<FixedCostCandidate> = RecurringCosts.of(
            listOf(
                event("학원비", 250_000L, "2026-07-05", fromRecord = true),
                event("학원비", 320_000L, "2026-08-05"),
            ),
            cycles,
        )

        assertEquals(1, found.size)
        assertEquals(285_000L, found[0].amount)
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
