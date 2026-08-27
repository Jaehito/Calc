package com.calc.expense

import java.text.NumberFormat
import java.util.Locale

/** 알림 한 줄과 펼쳤을 때의 본문. */
data class StatusLines(val summary: String, val detail: String)

/**
 * 기록 결과를 사람이 읽는 문구로 옮긴다.
 *
 * 문구가 이 앱의 보상이다. 입력한 순간 돌아오는 답이 없으면 기록은 순수 비용이 되고,
 * 순수 비용은 반드시 중단된다. 그래서 조립을 화면에서 떼어내 테스트로 고정한다.
 *
 * 나를 채점하는 표현은 쓰지 않는다 — 느낌표도, 잘했다는 말도, 몇 건 밀렸다는 말도 없다.
 * 돈에 관한 숫자만 말한다.
 */
object StatusText {

    private fun format(amount: Long): String =
        NumberFormat.getNumberInstance(Locale.KOREA).format(amount)

    fun won(amount: Long): String = format(amount) + "원"

    /** 기록이 성공했을 때. [snapshot] 이 null 이면 월 예산이 아직 없다. */
    fun recorded(
        name: String,
        amount: Long,
        snapshot: LedgerSnapshot?,
        time: String,
    ): StatusLines {
        val head = "✓ $name ${won(amount)} 기록됨 · $time"

        if (snapshot == null) {
            return StatusLines(
                summary = head,
                detail = head + "\n\n앱에서 월 예산을 정하면 오늘 쓸 수 있는 돈이 함께 표시됩니다.",
            )
        }

        val available: Long = snapshot.available
        if (available < 0L) {
            return StatusLines(
                summary = "✓ $name ${format(amount)} · 오늘 ${format(-available)} 초과",
                detail = head +
                    "\n\n오늘 ${won(-available)} 초과" +
                    "\n곳간을 다 쓰고 넘은 만큼은 남은 날에 나눠 조정됩니다.",
            )
        }

        return StatusLines(
            summary = "✓ $name ${format(amount)} · 오늘 쓸 수 있는 돈 ${format(available)}",
            detail = head +
                "\n\n오늘 쓸 수 있는 돈 ${won(available)}" +
                "\n하루치 ${format(snapshot.dailyRate)}" +
                " + 곳간 ${format(snapshot.vault)}" +
                " − 오늘 ${format(snapshot.todaySpent)}",
        )
    }

    /** 파싱이나 기록이 실패했을 때. 실패는 그 자리에서 무엇이 잘못됐는지 말한다. */
    fun failed(message: String, time: String): StatusLines {
        val line = "✗ $message · $time"
        return StatusLines(summary = line, detail = line)
    }

    /** 설정 화면에 보여줄 현재 상태. 홈 화면이 생기기 전까지 곳간을 눈으로 확인하는 창구다. */
    fun overview(snapshot: LedgerSnapshot?): String {
        if (snapshot == null) return "월 예산을 정하면 오늘 쓸 수 있는 돈이 여기에 표시됩니다."

        val available: Long = snapshot.available
        val headline: String =
            if (available >= 0L) "오늘 쓸 수 있는 돈  ${won(available)}"
            else "오늘 ${won(-available)} 초과"

        return headline +
            "\n하루치 ${format(snapshot.dailyRate)}" +
            " + 곳간 ${format(snapshot.vault)}" +
            " − 오늘 ${format(snapshot.todaySpent)}" +
            "\n이번 달 ${format(snapshot.monthSpent)} / ${format(snapshot.monthlyBudget)}"
    }
}
