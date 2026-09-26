package com.calc.expense

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 두 폰이 같이 쓰는 설정(공용 예산·이름·월급날)을 가정 문서와 주고받는 규칙.
 * 여기가 흔들리면 한쪽이 바꾼 예산이 다른 폰에서 0 이 되거나, 옛 값이 새 값을 덮는다.
 */
class HouseholdSettingsTest {

    private val mine = Settings(
        payDay = 25,
        personal = PurseSettings(monthlyBudget = 930_000L, name = "용돈"),
        shared = PurseSettings(monthlyBudget = 1_500_000L, name = "생활비"),
    )

    @Test
    fun `같이 쓰는 값만 뽑는다`() {
        assertEquals(SharedSettings(payDay = 25, sharedBudget = 1_500_000L, sharedName = "생활비"), HouseholdSettings.of(mine))
    }

    @Test
    fun `가정 값을 덮어도 개인 곳간은 그대로다`() {
        val remote = SharedSettings(payDay = 10, sharedBudget = 2_000_000L, sharedName = "우리집")
        val applied: Settings = HouseholdSettings.apply(mine, remote)

        assertEquals(10, applied.payDay)
        assertEquals(2_000_000L, applied.shared.monthlyBudget)
        assertEquals("우리집", applied.shared.name)
        assertEquals(mine.personal, applied.personal)
    }

    @Test
    fun `같으면 다르지 않다`() {
        assertFalse(HouseholdSettings.differs(mine, HouseholdSettings.of(mine)))
        assertTrue(HouseholdSettings.differs(mine, HouseholdSettings.of(mine).copy(sharedBudget = 1L)))
    }

    @Test
    fun `월급날이 없는 가정 문서는 비어 있는 것으로 본다`() {
        // 이 기능 전에 만든 가정. 빈 값으로 덮으면 공용 예산이 0 이 된다.
        assertNull(HouseholdSettings.fromFields(mapOf("code" to "K7PM2Q")))
    }

    @Test
    fun `Firestore 의 Long 을 읽는다`() {
        val fields: Map<String, Any?> = mapOf(
            "code" to "K7PM2Q",
            "payDay" to 25L,
            "sharedBudget" to 1_500_000L,
            "sharedName" to "생활비",
        )
        assertEquals(SharedSettings(25, 1_500_000L, "생활비"), HouseholdSettings.fromFields(fields))
    }

    @Test
    fun `이상한 월급날은 1에서 31 사이로 맞춘다`() {
        assertEquals(31, HouseholdSettings.fromFields(mapOf("payDay" to 40L))!!.payDay)
    }

    @Test
    fun `올린 값을 다시 읽으면 같다`() {
        val shared: SharedSettings = HouseholdSettings.of(mine)
        assertEquals(shared, HouseholdSettings.fromFields(HouseholdSettings.toFields(shared)))
    }

    @Test
    fun `같이 쓰는 값이 바뀌었을 때만 올린다`() {
        val renamed: Settings = mine.copy(personal = mine.personal.copy(name = "내돈"))
        // 개인 곳간만 바꿨으면 올리지 않는다 — 아직 못 받은 상대의 새 값을 옛 값으로 덮을 수 있다.
        assertFalse(HouseholdSettings.shouldPush(mine, renamed, remoteMissing = false))

        val payDayMoved: Settings = mine.copy(payDay = 1)
        assertTrue(HouseholdSettings.shouldPush(mine, payDayMoved, remoteMissing = false))
    }

    @Test
    fun `가정 문서가 비어 있으면 안 바뀌어도 한 번 채운다`() {
        assertTrue(HouseholdSettings.shouldPush(mine, mine, remoteMissing = true))
    }
}
