package com.calc.expense

import android.content.Context

/**
 * 지금 어느 곳간에 기록할 수 있나.
 *
 * 노션을 쓰던 시절에는 «DB 를 연결했나»(`PurseSettings.databaseId`)가 이 질문의 답이었다.
 * 이제 저장소가 Firestore 라 답이 바뀐다 — 개인 곳간은 **로그인**이, 공용 곳간은 **가정에
 * 묶였는지**가 조건이다. 둘 다 사용자가 설정에 타이핑하는 값이 아니므로 [Settings] 가 답할
 * 수 없고, Context 를 받는 여기가 답한다.
 *
 * **판정은 [FirestoreExpenseStore.collectionFor] 하나가 한다.** 여기서 조건을 다시 적으면
 * «기록할 수 있다고 해놓고 실제로는 쓸 곳이 없는» 어긋남이 생긴다 — 쓸 곳이 있으면 기록할
 * 수 있는 것이고, 그 판단은 컬렉션을 실제로 만들어 보는 쪽이 정확하다.
 */
object PurseAccess {

    /** 이 곳간에 지금 기록할 수 있나. */
    fun isLinked(context: Context, purse: Purse): Boolean =
        FirestoreExpenseStore.collectionFor(context, purse) != null

    /** 기록할 수 있는 곳간들. 순서는 [Purse] 선언 순서를 따른다. */
    fun linked(context: Context): List<Purse> =
        Purse.entries.filter { isLinked(context, it) }

    /**
     * 곳간 숫자(하루치·곳간 잔액)가 성립하는 곳간들. 기록만 되고 예산이 없으면 빠진다 —
     * 예산이 0 이면 나눌 것이 없어 «오늘 쓸 수 있는 돈»을 만들 수 없다.
     */
    fun active(context: Context, settings: Settings): List<Purse> =
        linked(context).filter { settings.of(it).hasBudget }

    /**
     * 앱이 제 일을 할 수 있는 상태인가. 알림·워커가 «설정이 끝났나»를 묻던 자리를 대신한다.
     *
     * 예산까지는 묻지 않는다 — 예산이 없어도 기록은 되고, 숫자가 «—» 로 보일 뿐이다.
     */
    fun isReady(context: Context): Boolean = linked(context).isNotEmpty()
}
