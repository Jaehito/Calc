package com.calc.expense

/**
 * 기본 카테고리마다 붙는 선 아이콘. 통계 화면과 기록 창이 같은 그림을 쓰도록 여기 한 곳에 둔다.
 *
 * 열쇠는 저장된 이름(한국어)이다 — 화면 언어가 바뀌어도 아이콘은 그대로다.
 * 사용자가 만든 카테고리는 아이콘이 없어서 null 이고, 부르는 쪽이 첫 글자를 대신 그린다.
 */
object CategoryIcons {

    fun of(storedName: String): Int? = when (storedName) {
        "식비" -> R.drawable.ic_cat_food
        "카페" -> R.drawable.ic_cat_cafe
        "간식" -> R.drawable.ic_cat_snack
        "술" -> R.drawable.ic_cat_drinks
        "마트" -> R.drawable.ic_cat_mart
        "교통" -> R.drawable.ic_cat_transport
        "생활" -> R.drawable.ic_cat_household
        "건강" -> R.drawable.ic_cat_health
        "육아" -> R.drawable.ic_cat_kids
        "문화" -> R.drawable.ic_cat_culture
        "패션" -> R.drawable.ic_cat_fashion
        "주거" -> R.drawable.ic_cat_housing
        "기타" -> R.drawable.ic_cat_other
        CategoryBreakdown.UNCATEGORIZED -> R.drawable.ic_cat_uncategorized
        else -> null
    }
}
