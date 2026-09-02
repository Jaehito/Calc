package com.calc.expense

/**
 * 카테고리 칩 목록. 앱이 기본값을 주고, 설정에서 사용자가 바꾼다.
 *
 * 저장·화면과 분리한 순수 로직이라 단위 테스트로 고정한다. 목록은 노션이 아니라 앱이 갖는다 —
 * 잠금화면에서 네트워크 없이 바로 칩을 그려야 하고, 나중에 저장소를 옮겨도 그대로 살아남게 하려는
 * 것이다.
 */
object Categories {

    /** 처음 주는 기본 목록. 자주 쓰는 순으로. */
    val DEFAULT: List<String> = listOf("식비", "카페", "교통", "마트", "생활", "건강", "문화", "기타")

    /** 칩 하나의 이름 길이 상한. 칩이 한 줄에서 가로로 스크롤되므로 너무 길면 읽기 나쁘다. */
    const val MAX_NAME_LENGTH = 12

    /** 칩 개수 상한. 너무 많으면 고르는 게 일이 된다. */
    const val MAX_COUNT = 20

    /**
     * 설정 입력(쉼표·줄바꿈으로 나눈 글)을 목록으로 다듬는다.
     * 앞뒤 공백 제거, 빈 항목·중복 제거(첫 등장 순서 유지), 길이·개수 상한 적용.
     */
    fun parse(text: String): List<String> {
        val out = ArrayList<String>()
        for (raw in text.split(',', '\n')) {
            val name: String = raw.trim().take(MAX_NAME_LENGTH)
            if (name.isEmpty()) continue
            if (out.contains(name)) continue
            out.add(name)
            if (out.size >= MAX_COUNT) break
        }
        return out
    }

    /** 목록을 설정 화면에 보여줄 한 줄로 만든다. */
    fun format(list: List<String>): String = list.joinToString(", ")
}
