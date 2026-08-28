package com.calc.expense

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsCodecTest {

    private val full = Settings(
        token = "secret_abc123",
        purseProp = "곳간",
        payDay = 25,
        personal = PurseSettings("db-personal", 930_000L, name = "재호 용돈"),
        shared = PurseSettings("db-shared", 1_550_000L, name = "우리집"),
    )

    @Test
    fun `내보낸 코드를 다시 불러오면 그대로 돌아온다`() {
        val code: String = SettingsCodec.encode(full)
        val back: Settings? = SettingsCodec.decode(code)
        assertEquals(full, back)
    }

    @Test
    fun `개인만 설정한 것도 왕복한다`() {
        val s = Settings(token = "t", personal = PurseSettings("db1", 310_000L))
        assertEquals(s, SettingsCodec.decode(SettingsCodec.encode(s)))
    }

    @Test
    fun `이름에 공백이 있어도 왕복한다`() {
        val s = full.copy(personal = full.personal.copy(name = "재호 의 용돈"))
        assertEquals(s, SettingsCodec.decode(SettingsCodec.encode(s)))
    }

    @Test
    fun `엉뚱한 코드는 null 이다`() {
        assertNull(SettingsCodec.decode("그냥 아무 텍스트"))
        assertNull(SettingsCodec.decode(""))
    }
}
