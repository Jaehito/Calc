package com.calc.expense

/**
 * 사용자가 정하는 설정. 저장 방식과 분리해 두어 Android 없이도 다룰 수 있다.
 *
 * **여기에 저장소 좌표는 없다.** 어디에 기록하는지는 로그인 계정과 가정 id 가 정하고
 * ([PurseAccess]), 사용자가 타이핑하는 것은 예산 주기와 곳간 이름·금액뿐이다.
 */
data class Settings(
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
}
