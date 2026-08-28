package com.calc.expense

import kotlin.random.Random

/**
 * 챌린지 방 코드. 한 사람이 방을 만들면 이 코드가 나오고, 상대는 이걸 받아 참가한다.
 *
 * 헷갈리는 글자(0·1·I·L·O)를 아예 빼서 6자리로 만든다 — 눈으로 읽어 손으로 옮겨 적는
 * 코드라 오독이 곧 참가 실패가 된다. Android 에 의존하지 않아 단위 테스트로 고정한다.
 */
object ChallengeCode {

    /** I·L·O 와 0·1 을 뺀 글자만 쓴다. */
    private const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"

    const val LENGTH = 6

    /** 새 방 코드. 시드 [random] 을 받아 테스트에서 결과를 고정할 수 있다. */
    fun generate(random: Random = Random.Default): String =
        buildString {
            repeat(LENGTH) { append(ALPHABET[random.nextInt(ALPHABET.length)]) }
        }

    /**
     * 입력을 코드 형태로 다듬는다 — 대문자로 올리고, 코드에 쓰이는 글자만 남긴다(공백·하이픈 제거).
     * 다듬은 결과가 [isValid] 를 통과해야 참가에 쓴다.
     */
    fun normalize(input: String): String =
        input.uppercase().filter { it in ALPHABET }

    /** 코드 형식이 맞는가 — 길이 [LENGTH] 이고 전부 허용 글자. */
    fun isValid(code: String): Boolean =
        code.length == LENGTH && code.all { it in ALPHABET }
}
