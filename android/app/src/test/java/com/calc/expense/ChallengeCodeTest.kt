package com.calc.expense

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ChallengeCodeTest {

    @Test
    fun `코드는 6자리이고 허용 글자만 쓴다`() {
        val code: String = ChallengeCode.generate(Random(42))

        assertEquals(6, code.length)
        assertTrue(ChallengeCode.isValid(code))
    }

    @Test
    fun `헷갈리는 글자는 코드에 들어가지 않는다`() {
        // 여러 번 뽑아도 0·1·I·L·O 는 나오지 않는다
        val random = Random(7)
        repeat(200) {
            val code: String = ChallengeCode.generate(random)
            for (c in listOf('0', '1', 'I', 'L', 'O')) {
                assertFalse("코드에 '$c' 이 들어 있다: $code", code.contains(c))
            }
        }
    }

    @Test
    fun `같은 시드는 같은 코드를 낸다`() {
        assertEquals(ChallengeCode.generate(Random(99)), ChallengeCode.generate(Random(99)))
    }

    @Test
    fun `입력을 대문자로 올리고 공백과 하이픈을 없앤다`() {
        assertEquals("7K2QP9", ChallengeCode.normalize(" 7k2 qp9 "))
        assertEquals("ABCDEF", ChallengeCode.normalize("abc-def"))
    }

    @Test
    fun `허용되지 않는 글자는 다듬으며 버린다`() {
        // 0·1·I·L·O 는 코드 글자가 아니라 제거된다
        assertEquals("ABC", ChallengeCode.normalize("A0B1C"))
        assertEquals("", ChallengeCode.normalize("ILO"))
    }

    @Test
    fun `형식이 어긋나면 유효하지 않다`() {
        assertFalse(ChallengeCode.isValid("ABC"))        // 너무 짧다
        assertFalse(ChallengeCode.isValid("ABCDEFG"))    // 너무 길다
        assertFalse(ChallengeCode.isValid("ABC0EF"))     // 허용 안 되는 글자
        assertTrue(ChallengeCode.isValid("ABCDEF"))
    }
}
