package com.calc.expense

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Base64

class SettingsCodecTest {

    private val full = Settings(
        payDay = 25,
        personal = PurseSettings(930_000L, name = "재호 용돈"),
        shared = PurseSettings(1_550_000L, name = "우리집"),
    )

    @Test
    fun `내보낸 코드를 다시 불러오면 그대로 돌아온다`() {
        val code: String = SettingsCodec.encode(full)
        val back: Settings? = SettingsCodec.decode(code)
        assertEquals(full, back)
    }

    @Test
    fun `개인만 설정한 것도 왕복한다`() {
        val s = Settings(personal = PurseSettings(310_000L))
        assertEquals(s, SettingsCodec.decode(SettingsCodec.encode(s)))
    }

    @Test
    fun `이름에 공백이 있어도 왕복한다`() {
        val s = full.copy(personal = full.personal.copy(name = "재호 의 용돈"))
        assertEquals(s, SettingsCodec.decode(SettingsCodec.encode(s)))
    }

    @Test
    fun `노션 시절 v1 코드도 읽어 예산과 이름을 살린다`() {
        // 옛 코드를 들고 있던 사람을 막지 않는다 — 토큰·DB 줄은 버리고 나머지만 되살린다.
        val v1: String = Base64.getEncoder().encodeToString(
            listOf(
                "v1",
                "token\tsecret_abc123",
                "nameProp\t이름",
                "payDay\t25",
                "personal.db\tdb-personal",
                "personal.budget\t930000",
                "personal.name\t재호 용돈",
                "shared.budget\t1550000",
                "shared.name\t우리집",
            ).joinToString("\n").toByteArray(Charsets.UTF_8),
        )

        assertEquals(full, SettingsCodec.decode(v1))
    }

    @Test
    fun `엉뚱한 코드는 null 이다`() {
        assertNull(SettingsCodec.decode("그냥 아무 텍스트"))
        assertNull(SettingsCodec.decode(""))
    }

    @Test
    fun `아는 버전이 아니면 null 이다`() {
        val v9: String = Base64.getEncoder().encodeToString(
            "v9\npayDay\t25".toByteArray(Charsets.UTF_8),
        )
        assertNull(SettingsCodec.decode(v9))
    }
}
