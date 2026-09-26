package com.calc.expense

/** 앱에 들어오는 문. 새 진입점을 만들면 여기에 한 줄 늘린다. */
enum class EntryDoor {
    /** 바탕화면·앱 서랍의 아이콘. */
    LAUNCHER,

    /** 어제 등급 알림. */
    DAILY_GRADE,

    /** 주 1회 돌아보기 알림. */
    WEEKLY_REVIEW,

    /** 주기 결산 알림. */
    CYCLE_GRADE,

    /** 잠금화면 상시 카드. 가장 잦은 경로다. */
    LOCK_CARD,

    /** 결제 배너. 누르면 수집함이 열린다. */
    PENDING_INBOX,

    /** 아직 이름 없는 문 — 위젯·바로가기·앞으로 생길 것. */
    OTHER,
}

/** 그 문으로 들어오면 어디에 도착하나. */
enum class EntryRoute { HOME, RECORD }

/**
 * 진입 경로 → 도착 화면. **이 파일이 규칙의 유일한 근거다.**
 *
 * 규칙은 하나다 — **홈에 있는 것을 보러 오는 문만 홈이고, 나머지는 전부 기록 팝업이다.**
 * 이 앱을 여는 이유는 대부분 «방금 쓴 돈을 적으려고» 이므로, 기록이 기본값이고 홈이 예외다.
 *
 * [EntryDoor.OTHER] 가 기록으로 가는 것이 중요하다 — 나중에 위젯이나 바로가기를 붙였을 때
 * 아무것도 안 정해도 옳은 쪽으로 떨어진다. 홈으로 보내려면 그때 문을 하나 새로 만들어
 * [HOME_DOORS] 에 넣어야 하고, 그 손이 곧 «정말 홈이 맞나» 를 묻는 자리가 된다.
 *
 * Android 에 의존하지 않아 단위 테스트로 고정한다.
 */
object EntryRoutes {

    /**
     * 홈으로 보내는 문들. **홈 화면에 있는 것을 보러 오는 문만 여기 있다.**
     *
     * 성과 알림은 «숫자를 보러» 여는 것이라 홈이 맞다. 수집함도 홈 위에 뜨는 팝업이라
     * 홈으로 보낸다 — 기록 화면으로 보내면 빈 입력 칸이 열리고, 정작 확인해야 할 결제
     * 목록은 어디에도 없다.
     */
    val HOME_DOORS: Set<EntryDoor> = setOf(
        EntryDoor.LAUNCHER,
        EntryDoor.DAILY_GRADE,
        EntryDoor.WEEKLY_REVIEW,
        EntryDoor.CYCLE_GRADE,
        EntryDoor.PENDING_INBOX,
    )

    fun of(door: EntryDoor): EntryRoute =
        if (door in HOME_DOORS) EntryRoute.HOME else EntryRoute.RECORD
}
