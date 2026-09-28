package com.calc.expense

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalBackupTest {

    private val settings = Settings(
        payDay = 15,
        personal = PurseSettings(monthlyBudget = 840_000L, name = "용돈"),
        shared = PurseSettings(monthlyBudget = 1_200_000L, name = "우리집"),
    )
    private val fixed = FixedCostPlan(
        monthlyIncome = 3_000_000L,
        items = listOf(FixedCostItem("월세", 700_000L), FixedCostItem("보험", 150_000L)),
    )

    private fun backup(): PersonalBackup =
        PersonalBackup.of(settings, listOf("식비", "카페", "육아"), fixed, asked = true)

    @Test
    fun `맡긴 값을 그대로 되찾는다`() {
        val restored: PersonalBackup? = PersonalBackup.fromMap(backup().toMap())

        assertEquals(backup(), restored)
        assertEquals(fixed, FixedCostCodec.decode(restored?.fixedCosts))
    }

    @Test
    fun `Firestore 가 돌려주는 모양(정수는 Long, 목록은 List)으로도 읽는다`() {
        val fromStore: Map<String, Any?> = mapOf(
            "v" to 1L,
            "payDay" to 15L,
            "budget" to 840_000L,
            "name" to "용돈",
            "categories" to arrayListOf<Any?>("식비", null, 3L, "카페"),
            "fixedCosts" to FixedCostCodec.encode(fixed),
            "asked" to true,
        )

        val restored: PersonalBackup = PersonalBackup.fromMap(fromStore)!!

        assertEquals(15, restored.payDay)
        assertEquals(listOf("식비", "카페"), restored.categories)
    }

    @Test
    fun `예산이 없는 빈 폰은 올리지 않는다`() {
        val empty = PersonalBackup.of(Settings(), Categories.DEFAULT, FixedCostPlan(), asked = true)

        assertFalse(empty.worthKeeping)
        assertTrue(backup().worthKeeping)
    }

    @Test
    fun `예산이 없거나 칸이 없으면 되찾을 것이 없다`() {
        assertNull(PersonalBackup.fromMap(null))
        assertNull(PersonalBackup.fromMap(mapOf("payDay" to 15L)))
        assertNull(PersonalBackup.fromMap(mapOf("budget" to 0L)))
    }

    @Test
    fun `되찾을 때 공용 곳간 칸은 건드리지 않는다`() {
        val local = Settings(shared = PurseSettings(monthlyBudget = 500_000L, name = "가정"))

        val applied: Settings = PersonalBackup.applyTo(local, backup())

        assertEquals(840_000L, applied.personal.monthlyBudget)
        assertEquals("용돈", applied.personal.name)
        assertEquals(15, applied.payDay)
        assertEquals(local.shared, applied.shared)
    }

    @Test
    fun `이상한 월급날·긴 이름은 다듬어 되찾는다`() {
        val restored: PersonalBackup = PersonalBackup.fromMap(
            mapOf("budget" to 100_000L, "payDay" to 45L, "name" to "아주아주긴곳간이름입니다"),
        )!!

        assertEquals(Payday.normalize(45), restored.payDay)
        assertEquals(Purse.MAX_NAME_LENGTH, restored.name.length)
        assertTrue(restored.asked)
    }
}
