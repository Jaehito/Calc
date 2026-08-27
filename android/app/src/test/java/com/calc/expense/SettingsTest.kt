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
    fun `한 DB를 나눠 쓰면 곳간 속성으로 갈린다`() {
        val s = base.copy(
            personal = PurseSettings("db-one", 310_000L),
            shared = PurseSettings("db-one", 500_000L),
        )

        assertTrue(s.sharesOneDatabase)

        val p = s.target(Purse.PERSONAL)!!
        val h = s.target(Purse.SHARED)!!
        assertEquals("db-one", p.databaseId)
        assertEquals("db-one", h.databaseId)
        assertEquals("곳간", p.purseProp)
        assertEquals("개인", p.purseTag)
        assertEquals("공용", h.purseTag)
        assertTrue(p.splitsByPurse)
        assertTrue(h.splitsByPurse)
    }

    @Test
    fun `DB를 따로 쓰면 곳간 속성을 쓰지 않는다`() {
        // 거를 것이 없다. 속성 이름이 채워져 있어도 좌표에는 실리지 않는다
        val s = base.copy(
            personal = PurseSettings("db-personal", 310_000L),
            shared = PurseSettings("db-shared", 500_000L),
        )

        assertFalse(s.sharesOneDatabase)
        assertFalse(s.target(Purse.PERSONAL)!!.splitsByPurse)
        assertEquals("", s.target(Purse.SHARED)!!.purseProp)
    }

    @Test
    fun `URL과 ID로 각각 넣어도 같은 DB로 본다`() {
        val id = "3c9808f1cd0f80e78926d8dc541ac442"
        val s = base.copy(
            personal = PurseSettings(id),
            shared = PurseSettings("https://www.notion.so/gamsyu/$id?v=abc123"),
        )

        assertTrue(s.sharesOneDatabase)
    }

    @Test
    fun `곳간 속성을 비우면 한 DB여도 가르지 않는다`() {
        val s = base.copy(
            purseProp = "",
            personal = PurseSettings("db-one"),
            shared = PurseSettings("db-one"),
        )

        assertFalse(s.target(Purse.PERSONAL)!!.splitsByPurse)
    }

    @Test
    fun `Notion 값은 곳간 이름을 바꿔도 개인 공용 그대로다`() {
        // 이름을 바꿨다고 Notion select 값까지 바뀌면 새 옵션이 생겨 기록이 쪼개진다
        val s = base.copy(
            personal = PurseSettings("db-one", name = "재호 용돈"),
            shared = PurseSettings("db-one", name = "우리집"),
        )

        assertEquals("재호 용돈", s.labelOf(Purse.PERSONAL))
        assertEquals("개인", s.tagOf(Purse.PERSONAL))
        assertEquals("공용", s.tagOf(Purse.SHARED))
        assertEquals("개인", s.target(Purse.PERSONAL)!!.purseTag)
    }

    @Test
    fun `공용만 연결해도 된다`() {
        val s = base.copy(shared = PurseSettings("db-shared", 1_550_000L))

        assertTrue(s.isComplete)
        assertEquals(listOf(Purse.SHARED), s.linkedPurses)
    }
}
