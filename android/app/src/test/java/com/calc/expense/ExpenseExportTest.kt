package com.calc.expense

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 지출 백업 파일의 모양. 여기가 틀리면 정작 필요한 날 열리지 않거나 칸이 밀린 표가 나온다.
 */
class ExpenseExportTest {

    private fun row(
        name: String,
        amount: Long,
        date: String,
        category: String = "",
        purse: Purse = Purse.PERSONAL,
    ) = PursedRow(
        purse = purse,
        row = ExpenseRow(id = "x", name = name, amount = amount, date = LocalDate.parse(date), category = category),
    )

    private fun lines(csv: String): List<String> =
        csv.removePrefix(ExpenseExport.BOM).trimEnd('\r', '\n').split("\r\n")

    @Test
    fun `머리글이 먼저 오고 오래된 것부터 쌓인다`() {
        val csv: String = ExpenseExport.toCsv(
            listOf(
                row("스타벅스", 5_500L, "2026-09-10", "카페"),
                row("월세", 550_000L, "2026-09-05", "주거"),
            ),
        )

        val out: List<String> = lines(csv)
        assertEquals("날짜,이름,금액,카테고리,곳간", out[0])
        assertEquals("2026-09-05,월세,550000,주거,개인", out[1])
        assertEquals("2026-09-10,스타벅스,5500,카페,개인", out[2])
    }

    @Test
    fun `엑셀이 한글을 깨뜨리지 않게 표식을 앞에 둔다`() {
        assertTrue(ExpenseExport.toCsv(emptyList()).startsWith(ExpenseExport.BOM))
    }

    @Test
    fun `쉼표가 든 이름은 칸을 밀지 않는다`() {
        // 「이마트, 성수점」 같은 이름이 실제로 들어온다. 그대로 쓰면 그 뒤 모든 값이 어긋난다.
        val csv: String = ExpenseExport.toCsv(listOf(row("이마트, 성수점", 42_000L, "2026-09-01")))

        assertEquals("2026-09-01,\"이마트, 성수점\",42000,,개인", lines(csv)[1])
    }

    @Test
    fun `따옴표가 든 이름은 두 번 적어 감싼다`() {
        assertEquals("\"\"\"큰손\"\" 정육점\"", ExpenseExport.escape("\"큰손\" 정육점"))
    }

    @Test
    fun `줄바꿈이 든 이름은 한 줄로 편다`() {
        // 줄바꿈을 그대로 두면 한 지출이 두 줄로 쪼개져 표가 무너진다.
        assertEquals("메모 두 줄", ExpenseExport.escape("메모\n두 줄"))
        assertEquals("메모 두 줄", ExpenseExport.escape("메모\r\n두 줄"))
    }

    @Test
    fun `카테고리가 없으면 빈 칸으로 둔다`() {
        // 「미분류」라고 적어 넣지 않는다 — 저장된 값은 빈 문자열이고, 파일은 저장된 값을 담는다.
        assertEquals("2026-09-01,쿠팡,34000,,개인", lines(ExpenseExport.toCsv(listOf(row("쿠팡", 34_000L, "2026-09-01"))))[1])
    }

    @Test
    fun `곳간을 사람 말로 적는다`() {
        val csv: String = ExpenseExport.toCsv(
            listOf(row("장보기", 80_000L, "2026-09-01", purse = Purse.SHARED)),
        )

        assertTrue(lines(csv)[1].endsWith(",공용"))
    }

    @Test
    fun `비어 있어도 머리글은 남는다`() {
        // 빈 파일을 받으면 「내보내기가 실패했나」를 알 수 없다.
        assertEquals(listOf("날짜,이름,금액,카테고리,곳간"), lines(ExpenseExport.toCsv(emptyList())))
    }

    @Test
    fun `파일 이름에 날짜가 들어간다`() {
        assertEquals("곳간-지출-2026-09-16.csv", ExpenseExport.fileName(LocalDate.of(2026, 9, 16)))
    }
}
