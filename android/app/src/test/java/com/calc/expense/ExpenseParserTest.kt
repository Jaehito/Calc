package com.calc.expense

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test fun `띄어 쓰지 않아도 이름 뒤 금액을 가른다`() = assertEquals(Expense("커피", 4500), ok("커피4500"))

    @Test fun `띄어 쓰지 않아도 금액 뒤 이름을 가른다`() = assertEquals(Expense("커피", 4500), ok("4500커피"))

    @Test fun `띄어 쓰지 않은 영어 이름`() {
        assertEquals(Expense("coffee", 4500), ok("coffee4500"))
        assertEquals(Expense("coffee", 4500), ok("4500coffee"))
    }

    @Test fun `띄어 쓰지 않아도 쉼표·원·한글 단위를 금액으로`() {
        assertEquals(Expense("택시", 12000), ok("택시12,000원"))
        assertEquals(Expense("커피", 4000), ok("커피4천"))
        assertEquals(Expense("장보기", 15000), ok("장보기1.5만"))
    }

    @Test fun `이름 첫 글자가 만·원이어도 단위로 먹지 않는다`() {
        assertEquals(Expense("만두", 2000), ok("2000만두"))
        assertEquals(Expense("원두", 4500), ok("4500원두"))
    }

    @Test fun `몇 년·몇 잔 같은 숫자는 금액으로 보지 않는다`() {
        assertEquals(Expense("아메리카노2잔", 9000), ok("아메리카노2잔9000"))
        assertEquals(Expense("도시락", 4500), ok("4500도시락"))
        err("2000년")
    }

    @Test fun `금액 위치 - 띄어 쓰지 않은 금액`() {
        assertEquals(2..5, ExpenseParser.amountRange("커피4500"))
        assertEquals(0..3, ExpenseParser.amountRange("4500커피"))
        assertEquals(2..8, ExpenseParser.amountRange("택시12,000원"))
    }

    @Test fun `금액 없음`() = err("커피")

    @Test fun `이름 없음`() = err("4500")

    @Test fun `이름 없이 금액만 적으면 대체 이름`() {
        val r = ExpenseParser.parse("4500", fallbackName = "카페") as ParseResult.Ok
        assertEquals(Expense("카페", 4500), r.expense)
        assertFalse(r.named)
    }

    @Test fun `이름을 적었으면 대체 이름을 쓰지 않는다`() {
        val r = ExpenseParser.parse("커피 4500", fallbackName = "카페") as ParseResult.Ok
        assertEquals(Expense("커피", 4500), r.expense)
        assertTrue(r.named)
    }

    @Test fun `대체 이름이 비어 있으면 실패`() =
        assertTrue(ExpenseParser.parse("4500", fallbackName = "  ") is ParseResult.Err)

    @Test fun `줄바꿈 없는 공백도 낱말을 가른다`() =
        assertEquals(Expense("커피", 4500), ok("커피\u00A04500"))

    @Test fun `금액 위치 - 이름 뒤`() = assertEquals(8..11, ExpenseParser.amountRange("스타벅스 커피 5600"))

    @Test fun `금액 위치 - 앞뒤 공백과 쉼표`() = assertEquals(5..11, ExpenseParser.amountRange("  택시 12,000원 "))

    @Test fun `금액 위치 - 금액이 앞에`() = assertEquals(0..3, ExpenseParser.amountRange("4500 커피"))

    @Test fun `금액 위치 - 금액이 없거나 0원이면 없음`() {
        assertNull(ExpenseParser.amountRange("스타벅스"))
        assertNull(ExpenseParser.amountRange("커피 0"))
        assertNull(ExpenseParser.amountRange(""))
    }

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
