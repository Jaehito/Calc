package com.calc.expense

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsTest {

    private val base = Settings(token = "secret_xxx")

    @Test
    fun `DB가 하나도 없으면 설정이 완성되지 않는다`() {
        assertFalse(base.isComplete)
        assertEquals(emptyList<Purse>(), base.linkedPurses)
    }

    @Test
    fun `개인만 연결해도 쓸 수 있다`() {
        // 공용 계좌가 아직 없는 상태 — 개인 하나로 시작한다
        val s = base.copy(personal = PurseSettings(databaseId = "db1", monthlyBudget = 930_000L))

        assertTrue(s.isComplete)
        assertEquals(listOf(Purse.PERSONAL), s.linkedPurses)
        assertNull(s.target(Purse.SHARED))
    }

    @Test
    fun `토큰이 없으면 좌표를 만들지 않는다`() {
        val s = Settings(personal = PurseSettings(databaseId = "db1"))

        assertNull(s.target(Purse.PERSONAL))
        assertFalse(s.isComplete)
    }

    @Test
    fun `두 곳간은 DB만 다르고 토큰과 속성을 공유한다`() {
        val s = base.copy(
            personal = PurseSettings("db-personal", 310_000L),
            shared = PurseSettings("db-shared", 1_550_000L),
        )

        val p = s.target(Purse.PERSONAL)!!
        val h = s.target(Purse.SHARED)!!

        assertEquals("db-personal", p.databaseId)
        assertEquals("db-shared", h.databaseId)
        assertEquals(p.token, h.token)
        assertEquals(p.nameProp, h.nameProp)
        assertEquals(p.priceProp, h.priceProp)
        assertEquals(p.dateProp, h.dateProp)
    }

    @Test
    fun `알림 액션 순서는 개인 먼저다`() {
        val s = base.copy(
            personal = PurseSettings("db-personal"),
            shared = PurseSettings("db-shared"),
        )

        assertEquals(listOf(Purse.PERSONAL, Purse.SHARED), s.linkedPurses)
    }

    @Test
    fun `DB만 있고 예산이 없으면 기록은 되고 곳간은 안 돈다`() {
        val p = PurseSettings(databaseId = "db1", monthlyBudget = 0L)

        assertTrue(p.isLinked)
        assertFalse(p.isActive)
    }

    @Test
    fun `이름을 정하지 않으면 기본 이름을 쓴다`() {
        assertEquals("개인", base.labelOf(Purse.PERSONAL))
        assertEquals("공용", base.labelOf(Purse.SHARED))
    }

    @Test
    fun `이름을 정하면 그 이름을 쓴다`() {
        val s = base.copy(
            personal = PurseSettings("db1", 310_000L, name = "재호 용돈"),
            shared = PurseSettings("db2", 1_550_000L, name = "우리집"),
        )

        assertEquals("재호 용돈", s.labelOf(Purse.PERSONAL))
        assertEquals("우리집", s.labelOf(Purse.SHARED))
    }

    @Test
    fun `공백만 있는 이름은 기본 이름으로 되돌아간다`() {
        val s = base.copy(personal = PurseSettings("db1", name = "   "))

        assertEquals("개인", s.labelOf(Purse.PERSONAL))
    }

    @Test
    fun `공용만 연결해도 된다`() {
        val s = base.copy(shared = PurseSettings("db-shared", 1_550_000L))

        assertTrue(s.isComplete)
        assertEquals(listOf(Purse.SHARED), s.linkedPurses)
    }
}
