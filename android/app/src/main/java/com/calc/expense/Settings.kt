package com.calc.expense

/**
 * Notion 연결 정보와 곳간 설정. 저장 방식과 분리해 두어 Android 없이도 다룰 수 있다.
 *
 * 토큰과 속성 이름은 두 곳간이 공유한다 — 같은 인테그레이션을 두 DB 에 연결하고
 * 스키마도 같게 두는 것이 전제다. 다른 것은 DB 와 예산뿐이다.
 */
data class Settings(
    val token: String = "",
    val nameProp: String = "이름",
    val priceProp: String = "금액",
    val dateProp: String = "날짜",
    /** 예산 주기의 경계가 되는 날. 두 곳간이 공유한다. 1 이면 달력 월과 같다. */
    val payDay: Int = Payday.DEFAULT,
    val personal: PurseSettings = PurseSettings(),
    val shared: PurseSettings = PurseSettings(),
) {
    /** 화면과 알림에 쓸 이름. 사용자가 정한 게 없으면 기본 이름. */
    fun labelOf(purse: Purse): String = of(purse).name.ifBlank { purse.defaultLabel }

    fun of(purse: Purse): PurseSettings = when (purse) {
        Purse.PERSONAL -> personal
        Purse.SHARED -> shared
    }

    /** 기록할 수 있는 곳간들. 순서는 [Purse] 선언 순서를 따른다. */
    val linkedPurses: List<Purse>
        get() = Purse.entries.filter { of(it).isLinked }

    /** 토큰·속성이 채워져 있고 DB 가 하나라도 연결됐는지. */
    val isComplete: Boolean
        get() = token.isNotBlank() &&
            nameProp.isNotBlank() && priceProp.isNotBlank() && dateProp.isNotBlank() &&
            linkedPurses.isNotEmpty()

    /** 그 곳간의 Notion 좌표. 연결돼 있지 않으면 null. */
    fun target(purse: Purse): NotionTarget? {
        val p = of(purse)
        if (!p.isLinked || token.isBlank()) return null
        return NotionTarget(
            token = token,
            databaseId = p.databaseId,
            nameProp = nameProp,
            priceProp = priceProp,
            dateProp = dateProp,
        )
    }
}
