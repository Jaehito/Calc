package com.calc.expense

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpenseParserTest {

    private fun ok(raw: String): Expense {
        val r = ExpenseParser.parse(raw)
        assertTrue("기대: 성공, 실제: $r", r is ParseResult.Ok)
        return (r as ParseResult.Ok).expense
    }

    private fun err(raw: String) {
        assertTrue("기대: 실패", ExpenseParser.parse(raw) is ParseResult.Err)
    }

    @Test fun `이름 뒤에 금액`() = assertEquals(Expense("커피", 4500), ok("커피 4500"))

    @Test fun `금액 뒤에 이름`() = assertEquals(Expense("커피", 4500), ok("4500 커피"))

    @Test fun `이름에 공백 포함`() = assertEquals(Expense("점심 김밥", 6000), ok("점심 김밥 6000"))

    @Test fun `천단위 쉼표`() = assertEquals(Expense("택시", 12000), ok("택시 12,000"))

    @Test fun `원 단위 접미사`() = assertEquals(Expense("택시", 12000), ok("택시 12,000원"))

    @Test fun `한글 단위 천`() = assertEquals(Expense("커피", 4000), ok("커피 4천"))

    @Test fun `한글 단위 만 소수`() = assertEquals(Expense("장보기", 15000), ok("장보기 1.5만"))

    @Test fun `앞뒤 공백과 중복 공백`() = assertEquals(Expense("커피", 4500), ok("  커피    4500  "))

    @Test fun `이름에 숫자가 섞여 있어도 마지막 숫자를 금액으로`() =
        assertEquals(Expense("2000년 동창회비", 50000), ok("2000년 동창회비 50000"))

    @Test fun `금액 없음`() = err("커피")

    @Test fun `이름 없음`() = err("4500")

    @Test fun `빈 입력`() = err("   ")

    @Test fun `0원`() = err("커피 0")

    @Test fun `과도한 금액`() = err("집 999999999999")

    @Test fun `단위만 있는 토큰`() = assertEquals(4500L, ExpenseParser.parseAmount("4500"))

    @Test fun `금액이 아닌 토큰`() {
        assertNull(ExpenseParser.parseAmount("커피"))
        assertNull(ExpenseParser.parseAmount(""))
        assertNull(ExpenseParser.parseAmount("2000년"))
    }
}
