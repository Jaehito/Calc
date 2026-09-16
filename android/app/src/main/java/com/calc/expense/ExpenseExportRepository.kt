package com.calc.expense

import android.content.Context
import java.io.File
import java.time.LocalDate

/** 내보내기 한 번의 결과. 성공하면 만든 파일과 담긴 줄 수를 준다. */
sealed interface ExportResult {
    data class Ok(val file: File, val count: Int) : ExportResult
    data class Err(val message: String) : ExportResult
}

/**
 * 지출 전부를 읽어 CSV 파일로 만든다. 저장소를 읽으므로 백그라운드 스레드에서 부른다.
 *
 * 파일은 앱 전용 캐시 안에 둔다 — 공유 시트로 넘기는 순간에만 밖으로 나가고, 다른 앱이
 * 마음대로 읽지 못한다([FileProvider] 로만 건넨다).
 */
object ExpenseExportRepository {

    private const val DIR = "export"

    fun write(context: Context, today: LocalDate = LocalDate.now()): ExportResult {
        val purses: List<Purse> = PurseAccess.linked(context)
        if (purses.isEmpty()) return ExportResult.Err("로그인이 풀렸습니다. 다시 로그인해 주세요")

        val rows = ArrayList<PursedRow>()
        for (purse in purses) {
            // 하나라도 못 읽으면 실패로 본다. 반쪽짜리 파일을 백업이라고 들고 있으면
            // 정작 필요한 날 없는 줄을 모른 채 잃는다.
            val read: List<ExpenseRow> =
                FirestoreExpenseReader.rowsBetween(context, purse, ExpenseExport.EARLIEST, today)
                    ?: return ExportResult.Err("불러오지 못했습니다. 인터넷을 확인하고 다시 해 주세요")
            for (row in read) rows.add(PursedRow(purse, row))
        }

        return try {
            val dir = File(context.cacheDir, DIR)
            dir.mkdirs()
            // 지난번 파일은 지운다. 캐시에 옛 내보내기가 쌓이면 공유 시트에서 어느 것이
            // 최신인지 알 수 없다.
            dir.listFiles()?.forEach { it.delete() }

            val file = File(dir, ExpenseExport.fileName(today))
            file.writeText(ExpenseExport.toCsv(rows), Charsets.UTF_8)
            ExportResult.Ok(file, rows.size)
        } catch (e: Exception) {
            ExportResult.Err("파일을 만들지 못했습니다: ${e.message ?: e.javaClass.simpleName}")
        }
    }
}
