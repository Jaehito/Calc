package com.calc.expense

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 진입 경로 → 도착 화면. 규칙이 흩어지지 않도록 문마다 못 박아 둔다.
 */
class EntryRouteTest {

    @Test
    fun `홈에 있는 것을 보러 오는 문만 홈이다`() {
        assertEquals(EntryRoute.HOME, EntryRoutes.of(EntryDoor.LAUNCHER))
        assertEquals(EntryRoute.HOME, EntryRoutes.of(EntryDoor.DAILY_GRADE))
        assertEquals(EntryRoute.HOME, EntryRoutes.of(EntryDoor.WEEKLY_REVIEW))
        assertEquals(EntryRoute.HOME, EntryRoutes.of(EntryDoor.CYCLE_GRADE))
    }

    @Test
    fun `결제 배너는 수집함이 뜨는 홈으로 간다`() {
        // 기록 화면으로 보내면 빈 입력 칸이 열리고, 정작 확인해야 할 결제 목록은 어디에도 없다.
        assertEquals(EntryRoute.HOME, EntryRoutes.of(EntryDoor.PENDING_INBOX))
    }

    @Test
    fun `나머지는 전부 기록이다`() {
        assertEquals(EntryRoute.RECORD, EntryRoutes.of(EntryDoor.LOCK_CARD))
    }

    @Test
    fun `이름 없는 문은 기록으로 떨어진다`() {
        // 나중에 위젯·바로가기를 붙여도 아무것도 안 정하면 옳은 쪽으로 간다.
        assertEquals(EntryRoute.RECORD, EntryRoutes.of(EntryDoor.OTHER))
    }

    @Test
    fun `홈으로 가는 문은 다섯뿐이다`() {
        // 문이 늘 때 실수로 홈에 끼워 넣으면 여기서 걸린다.
        assertEquals(5, EntryRoutes.HOME_DOORS.size)
    }
}
