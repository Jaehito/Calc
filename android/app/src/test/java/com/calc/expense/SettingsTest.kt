package com.calc.expense

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 설정에 남은 것은 «사용자가 정하는 값»뿐이다 — 예산 주기와 곳간 이름·금액.
 *
 * 어디에 기록하는지(로그인·가정 연결)는 [PurseAccess] 가 판정하고 Firebase 를 타므로 여기서
 * 다루지 않는다. 노션을 쓸 때는 그 판정이 설정 값(토큰·DB id)이라 이 파일이 검사했었다.
 */
class SettingsTest {

    private val base = Settings()

    @Test
    fun `예산이 없으면 곳간 숫자가 나오지 않는다`() {
        assertFalse(PurseSettings(monthlyBudget = 0L).hasBudget)
        assertTrue(PurseSettings(monthlyBudget = 930_000L).hasBudget)
    }

    @Test
    fun `이름을 정하지 않으면 기본 이름을 쓴다`() {
        assertEquals("개인", base.labelOf(Purse.PERSONAL))
        assertEquals("공용", base.labelOf(Purse.SHARED))
    }

    @Test
    fun `이름을 정하면 그 이름을 쓴다`() {
        val s = base.copy(
            personal = PurseSettings(310_000L, name = "재호 용돈"),
            shared = PurseSettings(1_550_000L, name = "우리집"),
        )

        assertEquals("재호 용돈", s.labelOf(Purse.PERSONAL))
        assertEquals("우리집", s.labelOf(Purse.SHARED))
    }

    @Test
    fun `공백만 있는 이름은 기본 이름으로 되돌아간다`() {
        val s = base.copy(personal = PurseSettings(310_000L, name = "   "))

        assertEquals("개인", s.labelOf(Purse.PERSONAL))
    }

    @Test
    fun `곳간마다 예산이 따로다`() {
        // 개인과 공용은 어디에서도 합쳐지지 않는다.
        val s = base.copy(
            personal = PurseSettings(310_000L),
            shared = PurseSettings(1_550_000L),
        )

        assertEquals(310_000L, s.of(Purse.PERSONAL).monthlyBudget)
        assertEquals(1_550_000L, s.of(Purse.SHARED).monthlyBudget)
    }

    @Test
    fun `월급날 기본값은 달력 월이다`() {
        assertEquals(Payday.DEFAULT, base.payDay)
    }
}
