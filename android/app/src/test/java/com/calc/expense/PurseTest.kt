package com.calc.expense

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 곳간은 둘뿐이고 순서가 고정이다. [AccountScope] 가 계정을 비울 때 곳간마다 도는 것도,
 * 알림 버튼 순서도 이 순서를 따른다 — 늘어나면 여기서 먼저 걸린다.
 */
class PurseTest {

    @Test
    fun `개인이 먼저고 공용이 다음이다`() {
        assertEquals(listOf(Purse.PERSONAL, Purse.SHARED), Purse.entries.toList())
    }

    @Test
    fun `키는 저장에 쓰이므로 바뀌면 안 된다`() {
        // 저장소 문서의 purse 필드와 SharedPreferences 키 접두사가 이 값이다.
        assertEquals("personal", Purse.PERSONAL.key)
        assertEquals("shared", Purse.SHARED.key)
    }
}
