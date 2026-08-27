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
    /**
     * 개인·공용을 가르는 select 속성 이름. 두 곳간이 같은 DB 를 쓸 때만 의미가 있다.
     * 비워 두면 곳간을 구분하지 않는다 — DB 를 따로 쓰는 구성이 그렇다.
     */
    val purseProp: String = "곳간",
    /** 예산 주기의 경계가 되는 날. 두 곳간이 공유한다. 1 이면 달력 월과 같다. */
    val payDay: Int = Payday.DEFAULT,
    val personal: PurseSettings = PurseSettings(),
    val shared: PurseSettings = PurseSettings(),
) {
    /** 화면과 알림에 쓸 이름. 사용자가 정한 게 없으면 기본 이름. */
    fun labelOf(purse: Purse): String = of(purse).name.ifBlank { purse.defaultLabel }

    /**
     * Notion 의 곳간 select 에 쓰는 값. 표시 이름과 **일부러 분리한다** —
     * 곳간 이름을 «재호 용돈» 으로 바꿨다고 Notion 값까지 바뀌면 select 에 새 옵션이
     * 조용히 생겨 기록이 두 갈래로 쪼개진다. Notion 쪽 값은 개인·공용으로 고정한다.
     */
    fun tagOf(purse: Purse): String = purse.defaultLabel

    /**
     * 두 곳간이 같은 DB 를 가리키는지. 그러면 곳간 속성 없이는 둘을 가를 수 없다.
     *
     * 저장 전 폼에는 붙여넣은 URL 이 그대로 들어 있고 저장된 값은 ID 로 줄어 있다.
     * 같은 DB 를 URL 과 ID 로 각각 넣어도 같게 보도록 양쪽을 정규화해 비교한다.
     */
    val sharesOneDatabase: Boolean
        get() = personal.isLinked &&
            NotionIds.normalize(personal.databaseId) == NotionIds.normalize(shared.databaseId)

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
            // 한 DB 를 나눠 쓸 때만 곳간 속성이 필요하다. DB 가 다르면 거를 것이 없다.
            purseProp = if (sharesOneDatabase) purseProp.trim() else "",
            purseTag = if (sharesOneDatabase) tagOf(purse) else "",
        )
    }
}
