package com.calc.expense

/**
 * 아이콘 자리마다 정한 색 하나. 동그라미 바탕은 이 색을 흰색 쪽으로 옅게 한 [back],
 * 그림의 선은 이 색을 짙게 한 [ink] 다. 그림 파일(ic_cat_*·ic_envelope 등)에는 선 색이 이미
 * 들어 있으니, 여기 색을 바꾸면 그림도 다시 그려야 한다.
 *
 * 카테고리 색은 기록 창 격자·통계 목록·도넛·리포트가 같이 쓴다 — 같은 카테고리는 어디서나 같은 색.
 * 사용자가 만든 카테고리는 기본 카테고리가 쓰지 않는 색 중에서 이름으로 하나를 고른다(늘 같은 색).
 *
 * 색은 ARGB Int 다. Compose 는 Color(int), XML 화면은 그대로 쓴다.
 */
object IconHues {

    /** 홈 세 줄 — 하루치(용돈 봉투)·곳간(돼지 저금통)·오늘 쓴 돈(쇼핑백). 셋이 서로 다른 색. */
    val DAILY: Int = 0xFF12C08B.toInt()
    val GOTGAN: Int = 0xFFE86A9A.toInt()
    val SPENT: Int = 0xFFF08A3C.toInt()

    /** 도감 «나의 기록» 다섯 줄과 지난 주기 리포트. */
    val SAVED_DAY: Int = 0xFFD98E1F.toInt()
    val KEEP_RUN: Int = 0xFF12C08B.toInt()
    val NO_SPEND: Int = 0xFF7C6BD6.toInt()
    val CHEAP_WEEK: Int = 0xFF3F82C9.toInt()
    val RECORD_RUN: Int = 0xFFD9587F.toInt()
    val REPORT: Int = 0xFFD98E1F.toInt()

    /** 통계의 «나머지 N개» 묶음. */
    val REST: Int = 0xFF9AA8A2.toInt()

    private val categories: Map<String, Int> = mapOf(
        CategoryBreakdown.UNCATEGORIZED to 0xFF8C9A94.toInt(),
        "식비" to 0xFF12C08B.toInt(),
        "카페" to 0xFFA9713F.toInt(),
        "간식" to 0xFFF08A3C.toInt(),
        "마트" to 0xFF6FA832.toInt(),
        "교통" to 0xFF3F82C9.toInt(),
        "생활" to 0xFF1FA3A0.toInt(),
        "건강" to 0xFFE5534B.toInt(),
        "육아" to 0xFFE86A9A.toInt(),
        "문화" to 0xFF7C6BD6.toInt(),
        "패션" to 0xFFD9587F.toInt(),
        "주거" to 0xFFD98E1F.toInt(),
        "기타" to 0xFF7D8B94.toInt(),
    )

    /** 사용자가 만든 카테고리용 — 기본 13개와 겹치지 않는 색. */
    private val extras: List<Int> = listOf(
        0xFF5C6BC0.toInt(), // 남색
        0xFFAB47BC.toInt(), // 자주
        0xFF9E9D24.toInt(), // 올리브
        0xFF546E9A.toInt(), // 회청
        0xFFB8923A.toInt(), // 모래
        0xFF2E8B57.toInt(), // 짙은 초록
    )

    /** 저장된 이름(한국어) 기준 — 화면 언어가 바뀌어도 색은 그대로다. */
    fun category(storedName: String): Int =
        categories[storedName] ?: extras[Math.floorMod(storedName.hashCode(), extras.size)]

    /** 동그라미 바탕. */
    fun back(hue: Int): Int = mix(hue, 0xFFFFFFFF.toInt(), 0.84f)

    /** 그림 선·첫 글자 색. 그림 파일의 선 색과 같은 규칙이다. */
    fun ink(hue: Int): Int = mix(hue, 0xFF0B1F19.toInt(), 0.35f)

    private fun mix(from: Int, to: Int, weight: Float): Int {
        fun channel(shift: Int): Int {
            val a: Int = (from shr shift) and 0xFF
            val b: Int = (to shr shift) and 0xFF
            return Math.round(a + (b - a) * weight) and 0xFF
        }
        return (0xFF shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }
}
