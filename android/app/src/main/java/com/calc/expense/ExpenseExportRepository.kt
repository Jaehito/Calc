package com.calc.expense

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.time.LocalDate

/**
 * 내보내기 한 번의 결과.
 *
 * @param file 공유 시트로 넘길 앱 안의 사본. 폰에 내려받지 못했을 때 남은 길이다
 * @param savedTo 폰의 «다운로드»에 저장됐으면 사람이 읽을 위치, 못 했으면 null
 */
sealed interface ExportResult {
    data class Ok(val file: File, val count: Int, val savedTo: String?) : ExportResult
    data class Err(val message: String) : ExportResult
}

/**
 * 지출 전부를 읽어 CSV 로 만든다. 저장소를 읽으므로 백그라운드 스레드에서 부른다.
 *
 * **폰의 «다운로드» 폴더에 바로 넣는다.** 공유 시트를 거치면 받는 앱을 고르는 일이 한 번 더
 * 생기고, 고른 앱이 파일을 어디에 두는지는 앱마다 다르다. 다운로드 폴더는 어느 폰에서나
 * 같은 자리라 나중에 찾기 쉽다.
 *
 * 앱 안에도 사본을 하나 만든다 — 내려받기가 막힌 기기에서 공유 시트로 보낼 때 쓴다.
 */
object ExpenseExportRepository {

    private const val DIR = "export"
    private const val MIME = "text/csv"

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

        val csv: String = ExpenseExport.toCsv(rows)
        val name: String = ExpenseExport.fileName(today)

        val cached: File = try {
            cache(context, name, csv)
        } catch (e: Exception) {
            return ExportResult.Err("파일을 만들지 못했습니다: ${e.message ?: e.javaClass.simpleName}")
        }

        return ExportResult.Ok(cached, rows.size, savedTo = download(context, name, csv))
    }

    /** 앱 전용 캐시의 사본. 공유 시트에 넘길 때만 쓴다([FileProvider] 로만 건넨다). */
    private fun cache(context: Context, name: String, csv: String): File {
        val dir = File(context.cacheDir, DIR)
        dir.mkdirs()
        // 지난번 파일은 지운다. 옛 내보내기가 쌓이면 공유 시트에서 어느 것이 최신인지 알 수 없다.
        dir.listFiles()?.forEach { it.delete() }

        val file = File(dir, name)
        file.writeText(csv, Charsets.UTF_8)
        return file
    }

    /**
     * 폰의 «다운로드» 폴더에 넣는다. 성공하면 사람이 읽을 위치, 실패하면 null.
     *
     * 안드로이드 10부터는 [MediaStore] 로 넣으면 **저장 권한이 필요 없다** — 앱이 자기가 만든
     * 파일 하나만 더하는 것이라 폰 전체를 열 이유가 없다. 그 아래 버전에서는 권한을 받아야
     * 하는데, 파일 하나 내보내려고 «사진과 파일에 접근» 권한을 물어보는 건 값이 너무 크다.
     * 그래서 옛 기기에서는 내려받지 않고 공유 시트로 남긴다.
     */
    private fun download(context: Context, name: String, csv: String): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null

        return try {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, MIME)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null

            resolver.openOutputStream(uri)?.use { it.write(csv.toByteArray(Charsets.UTF_8)) }
                ?: return null

            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)

            Environment.DIRECTORY_DOWNLOADS + "/" + name
        } catch (_: Exception) {
            // 제조사에 따라 막히는 경우가 있다. 그럴 때도 내보내기 자체는 성공으로 둔다 —
            // 앱 안의 사본이 있으니 공유 시트로 내보낼 수 있다.
            null
        }
    }
}
