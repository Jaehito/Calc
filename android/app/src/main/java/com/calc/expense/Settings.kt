package com.calc.expense

/**
 * 사용자가 정하는 설정. 저장 방식과 분리해 두어 Android 없이도 다룰 수 있다.
 *
 * **여기에 저장소 좌표는 없다.** 어디에 기록하는지는 로그인 계정과 가정 id 가 정하고
 * ([PurseAccess]), 사용자가 타이핑하는 것은 예산 주기와 곳간 이름·금액뿐이다.
 */
data class Settings(
    /**
     * 개인 지갑 주기의 경계가 되는 날(새 주기가 시작하는 날). 1 이면 달력 월과 같다.
     * 화면은 이 전날을 «목표날»로 보여 준다. 이 폰에만 있다.
     */
    val payDay: Int = Payday.DEFAULT,
    val personal: PurseSettings = PurseSettings(),
    val shared: PurseSettings = PurseSettings(),
    /** 공용 지갑 주기의 경계. 두 폰이 같이 쓴다(가정 문서 [HouseholdSettings]). */
    val sharedPayDay: Int = Payday.DEFAULT,
) {
    /** 그 지갑의 주기 경계. 지갑마다 목표날이 다를 수 있다. */
    fun payDayOf(purse: Purse): Int = when (purse) {
        Purse.PERSONAL -> payDay
        Purse.SHARED -> sharedPayDay
    }

    /** 화면과 알림에 쓸 이름. 사용자가 정한 게 없으면 기본 이름. */
    fun labelOf(purse: Purse): String = of(purse).name.ifBlank { purse.defaultLabel }.let { Purse.display(it) }

    fun of(purse: Purse): PurseSettings = when (purse) {
        Purse.PERSONAL -> personal
        Purse.SHARED -> shared
    }
}
