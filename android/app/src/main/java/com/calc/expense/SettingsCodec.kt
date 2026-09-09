package com.calc.expense

import java.util.Base64

/**
 * 설정을 한 줄 텍스트 코드로 옮기고 되돌린다. 재설치 뒤 다시 타이핑하지 않으려는 용도.
 *
 * 노션을 쓰던 시절에는 토큰·DB 를 다시 넣는 것이 재설치의 가장 큰 짐이라 이 코드가 비밀이었다.
 * 이제 저장소는 구글 로그인이 따라오므로 여기 담기는 것은 **예산 주기와 곳간 이름·금액뿐**이고,
 * 비밀이 아니다. 그래도 남겨 두는 이유는 예산을 다시 정하는 일이 여전히 성가시기 때문이다.
 *
 * Android 에 의존하지 않아(Base64 는 JVM 표준) 단위 테스트로 고정한다.
 */
object SettingsCodec {

    /**
     * v2 부터 노션 칸이 빠졌다. **v1 코드도 읽는다** — 옛 코드를 들고 있던 사람이 붙여넣으면
     * 예산·이름·월급날만 살려 낸다(토큰·DB 줄은 버린다). 옛 코드를 거절하면 그 사람은
     * 되돌릴 방법이 없다.
     */
    private const val VERSION = "v2"
    private const val LEGACY_VERSION = "v1"
    private const val SEP = "\t"

    fun encode(s: Settings): String {
        val lines: List<String> = listOf(
            VERSION,
            "payDay$SEP${s.payDay}",
            "personal.budget$SEP${s.personal.monthlyBudget}",
            "personal.name$SEP${s.personal.name}",
            "shared.budget$SEP${s.shared.monthlyBudget}",
            "shared.name$SEP${s.shared.name}",
        )
        val body: ByteArray = lines.joinToString("\n").toByteArray(Charsets.UTF_8)
        return Base64.getEncoder().encodeToString(body)
    }

    /** 코드를 되돌린다. 형식이 아니거나 아는 버전이 아니면 null — 붙여넣기 오류를 조용히 삼키지 않는다. */
    fun decode(code: String): Settings? {
        val text: String = try {
            String(Base64.getDecoder().decode(code.trim()), Charsets.UTF_8)
        } catch (e: IllegalArgumentException) {
            return null
        }

        val lines: List<String> = text.split("\n")
        val version: String? = lines.firstOrNull()
        if (version != VERSION && version != LEGACY_VERSION) return null

        val map: Map<String, String> = lines.drop(1)
            .mapNotNull { line ->
                val i: Int = line.indexOf(SEP)
                if (i < 0) null else line.substring(0, i) to line.substring(i + 1)
            }
            .toMap()

        // 최소한 월급날 키라도 있어야 유효한 코드로 본다.
        if (!map.containsKey("payDay")) return null

        return Settings(
            payDay = Payday.normalize(map["payDay"]?.toIntOrNull() ?: Payday.DEFAULT),
            personal = PurseSettings(
                monthlyBudget = map["personal.budget"]?.toLongOrNull() ?: 0L,
                name = map["personal.name"].orEmpty(),
            ),
            shared = PurseSettings(
                monthlyBudget = map["shared.budget"]?.toLongOrNull() ?: 0L,
                name = map["shared.name"].orEmpty(),
            ),
        )
    }
}
