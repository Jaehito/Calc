package com.calc.expense

/** Notion 식별자 다루기. Android 의존성이 없어 단위 테스트에서 바로 쓸 수 있다. */
object NotionIds {

    private val DASHED = Regex(
        "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"
    )
    private val BARE = Regex("[0-9a-fA-F]{32}")

    /**
     * 사용자는 DB ID 대신 URL을 통째로 붙여넣는 경우가 많다.
     * "https://notion.so/ws/27b8...?v=abcd..." 처럼 뷰 ID까지 섞여 있으므로
     * 경로에서 첫 번째로 나오는 32자리를 쓴다 (뒤쪽 v= 는 뷰 ID라 잘못된 값).
     */
    fun normalize(input: String): String {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return ""

        // 쿼리스트링(?v=... )은 뷰 ID라 후보에서 제외한다.
        val path = trimmed.substringBefore('?')

        DASHED.find(path)?.let { return it.value.replace("-", "").lowercase() }
        BARE.find(path)?.let { return it.value.lowercase() }

        // 경로에서 못 찾으면 입력 전체에서 마지막 시도
        DASHED.find(trimmed)?.let { return it.value.replace("-", "").lowercase() }
        BARE.find(trimmed)?.let { return it.value.lowercase() }

        return trimmed
    }
}
