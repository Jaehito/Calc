package com.calc.expense

import java.time.LocalDate

/**
 * 지출 전부를 CSV 한 장으로 만든다.
 *
 * **이 앱에는 지금까지 지출 백업이 없었다.** 설정 내보내기는 예산·칩 목록뿐이고, 실제 기록은
 * Firestore 에만 있어 계정 사고가 나면 그대로 사라진다. 그래서 만든다 — 사람이 열어 볼 수도,
 * 표 프로그램에 넣을 수도 있는 가장 흔한 꼴이 CSV 다.
 *
 * Android 에 의존하지 않아 단위 테스트로 고정한다.
 */
object ExpenseExport {

    /** 표 프로그램이 한글을 깨뜨리지 않게 앞에 붙이는 표식(BOM). 없으면 엑셀이 깨진 글자로 연다. */
    const val BOM: String = "﻿"

    private val HEADER: List<String> = listOf("날짜", "이름", "금액", "카테고리", "곳간")

    /**
     * [rows] 를 CSV 로. 날짜 오름차순(오래된 것부터) — 가계부를 위에서 아래로 읽는 순서다.
     *
     * 줄 끝은 CRLF 다. 엑셀이 LF 만으로는 줄을 나누지 못하는 경우가 있다.
     */
    fun toCsv(rows: List<PursedRow>): String {
        val sorted: List<PursedRow> = rows.sortedWith(
            compareBy({ it.row.date }, { it.row.name }),
        )

        val out = StringBuilder(BOM)
        out.append(HEADER.joinToString(",")).append("\r\n")
        for (item in sorted) {
            out.append(
                listOf(
                    item.row.date.toString(),
                    escape(item.row.name),
                    item.row.amount.toString(),
                    escape(item.row.category),
                    escape(label(item.purse)),
                ).joinToString(","),
            ).append("\r\n")
        }
        return out.toString()
    }

    /**
     * CSV 한 칸을 안전하게 감싼다.
     *
     * 쉼표·따옴표·줄바꿈이 든 이름을 그대로 쓰면 칸이 밀려 그 뒤 모든 값이 어긋난다.
     * 「이마트, 성수점」 같은 이름은 실제로 들어온다.
     */
    fun escape(raw: String): String {
        val text: String = raw.replace("\r\n", " ").replace('\n', ' ').replace('\r', ' ')
        if (!text.contains(',') && !text.contains('"')) return text
        return "\"" + text.replace("\"", "\"\"") + "\""
    }

    private fun label(purse: Purse): String = when (purse) {
        Purse.PERSONAL -> "개인"
        Purse.SHARED -> "공용"
    }

    /** 파일 이름. 언제 뽑은 것인지 이름만 보고 알 수 있어야 여러 개 쌓였을 때 구별된다. */
    fun fileName(today: LocalDate): String = "곳간-지출-$today.csv"

    /**
     * 내보내기가 훑을 가장 이른 날. 이 앱보다 앞선 기록은 없다.
     *
     * 「전부」를 훑어야 백업이 된다 — 최근 몇 달만 담으면 그건 백업이 아니라 발췌다.
     */
    val EARLIEST: LocalDate = LocalDate.of(2020, 1, 1)
}
