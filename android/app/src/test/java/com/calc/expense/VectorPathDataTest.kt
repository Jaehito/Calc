package com.calc.expense

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 벡터 아이콘의 pathData 가 안드로이드 뷰(ImageView)에서 읽히는지 본다.
 *
 * SVG 는 호(a/A)의 두 플래그를 숫자에 붙여 쓰는 걸 허용한다(«a4 4 0 004.2 4.3»).
 * Compose 의 벡터 파서는 이걸 읽지만, 안드로이드 기본 파서는 «004.2» 를 숫자 하나로 읽어
 * 개수가 어긋나고 죽는다 — 기록 창이 카테고리 아이콘을 그리다 통째로 안 뜬 원인이었다.
 * 그래서 플래그 바로 뒤에 구분자 없이 값이 붙은 곳이 하나라도 있으면 실패시킨다.
 */
class VectorPathDataTest {

    private val argCount = mapOf(
        'm' to 2, 'l' to 2, 'h' to 1, 'v' to 1, 'c' to 6, 's' to 4, 'q' to 4, 't' to 2, 'a' to 7, 'z' to 0,
    )
    private val number = Regex("""[+-]?(?:\d+\.?\d*|\.\d+)(?:[eE][+-]?\d+)?""")

    /** 붙어 쓴 호 플래그가 있으면 그 위치 설명, 없으면 null. */
    private fun gluedFlag(d: String): String? {
        var i = 0
        var cmd = 'm'
        var arg = 0
        while (i < d.length) {
            val ch = d[i]
            when {
                ch == ' ' || ch == ',' || ch == '\t' || ch == '\n' || ch == '\r' -> i++
                ch.isLetter() -> { cmd = ch.lowercaseChar(); arg = 0; i++ }
                else -> {
                    val n: Int = argCount[cmd] ?: return "알 수 없는 명령 $cmd"
                    val pos: Int = if (n == 0) 0 else arg % n
                    if (cmd == 'a' && (pos == 3 || pos == 4)) {
                        val next: Char? = d.getOrNull(i + 1)
                        if (next != null && (next.isDigit() || next == '.' || next == '-' || next == '+')) {
                            return d.substring(maxOf(0, i - 12), minOf(d.length, i + 12))
                        }
                        i++
                    } else {
                        val m = number.matchAt(d, i) ?: return "숫자를 못 읽음: ${d.substring(i, minOf(d.length, i + 12))}"
                        i = m.range.last + 1
                    }
                    arg++
                }
            }
        }
        return null
    }

    @Test fun `붙어 쓴 호 플래그를 잡는다`() {
        assertTrue(gluedFlag("M1 1a4 4 0 004.2 4.3") != null)
        assertTrue(gluedFlag("M1 1a4 4 0 0 0 4.2 4.3") == null)
    }

    @Test fun `모든 벡터 아이콘의 pathData 를 안드로이드가 읽을 수 있다`() {
        // 단위 테스트는 모듈 폴더(android/app)에서 돈다.
        val dir = File("src/main/res/drawable")
        assertTrue("drawable 폴더를 못 찾음: ${dir.absolutePath}", dir.isDirectory)

        val attr = Regex("""android:pathData="([^"]*)"""")
        val problems = mutableListOf<String>()
        for (file in dir.listFiles().orEmpty().filter { it.name.endsWith(".xml") }.sortedBy { it.name }) {
            for (m in attr.findAll(file.readText())) {
                val where: String? = gluedFlag(m.groupValues[1])
                if (where != null) problems.add("${file.name}: …$where…")
            }
        }
        assertTrue("안드로이드가 못 읽는 pathData:\n" + problems.joinToString("\n"), problems.isEmpty())
    }
}
