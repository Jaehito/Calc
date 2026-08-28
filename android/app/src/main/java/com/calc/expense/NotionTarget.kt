package com.calc.expense

/**
 * Notion DB 하나를 가리키는 좌표. 클라이언트는 이것만 받는다.
 *
 * 두 곳간이 **같은 DB** 를 쓸 수 있다. 그때는 [purseProp] select 속성의 값 [purseTag] 로
 * 갈린다 — 노션에서는 한 표로 보고 앱에서는 곳간이 나뉜다. DB 를 따로 쓰면 이 둘을
 * 비워 두면 되고, 그러면 쓰지도 거르지도 않는다.
 */
data class NotionTarget(
    val token: String,
    val databaseId: String,
    val nameProp: String,
    val priceProp: String,
    val dateProp: String,
    /** 곳간을 가르는 select 속성 이름. 비어 있으면 곳간을 구분하지 않는다. */
    val purseProp: String = "",
    /** 이 곳간이 쓰는 select 값. 비어 있으면 곳간을 구분하지 않는다. */
    val purseTag: String = "",
    /** 통계용 카테고리 select 속성 이름. 비어 있으면 카테고리 통계를 만들지 않는다. */
    val categoryProp: String = "",
) {
    /** 곳간 속성으로 갈라야 하는지. 이름과 값이 둘 다 있어야 성립한다. */
    val splitsByPurse: Boolean
        get() = purseProp.isNotBlank() && purseTag.isNotBlank()
}
