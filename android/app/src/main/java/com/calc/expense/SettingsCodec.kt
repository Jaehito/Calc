package com.calc.expense

import java.util.Base64

/**
 * 설정을 한 줄 텍스트 코드로 옮기고 되돌린다. 재설치 뒤 다시 타이핑하지 않으려는 용도.
 *
 * 계정·서버가 아니라 «내보내기 코드» 방식이다 — 코드를 복사해 두었다가 새 기기·새 설치에서
 * 붙여넣으면 토큰·DB·예산이 한 번에 복원된다. 노션 토큰이 들어 있으므로 이 코드는 비밀이다.
 *
 * Android 에 의존하지 않아(Base64 는 JVM 표준) 단위 테스트로 고정한다.
 */
object SettingsCodec {

    private const val VERSION = "v1"
    private const val SEP = "\t"

    fun encode(s: Settings): String {
        val lines: List<String> = listOf(
            VERSION,
            "token$SEP${s.token}",
            "nameProp$SEP${s.nameProp}",
            "priceProp$SEP${s.priceProp}",
            "dateProp$SEP${s.dateProp}",
            "purseProp$SEP${s.purseProp}",
            "categoryProp$SEP${s.categoryProp}",
            "payDay$SEP${s.payDay}",
            "personal.db$SEP${s.personal.databaseId}",
            "personal.budget$SEP${s.personal.monthlyBudget}",
            "personal.name$SEP${s.personal.name}",
            "shared.db$SEP${s.shared.databaseId}",
            "shared.budget$SEP${s.shared.monthlyBudget}",
            "shared.name$SEP${s.shared.name}",
        )
        val body: ByteArray = lines.joinToString("\n").toByteArray(Charsets.UTF_8)
        return Base64.getEncoder().encodeToString(body)
    }

    /** 코드를 되돌린다. 형식이 아니거나 버전이 다르면 null — 붙여넣기 오류를 조용히 삼키지 않는다. */
    fun decode(code: String): Settings? {
        val text: String = try {
            String(Base64.getDecoder().decode(code.trim()), Charsets.UTF_8)
        } catch (e: IllegalArgumentException) {
            return null
        }

        val lines: List<String> = text.split("\n")
        if (lines.firstOrNull() != VERSION) return null

        val map: Map<String, String> = lines.drop(1)
            .mapNotNull { line ->
                val i: Int = line.indexOf(SEP)
                if (i < 0) null else line.substring(0, i) to line.substring(i + 1)
            }
            .toMap()

        // 최소한 토큰 키라도 있어야 유효한 코드로 본다.
        if (!map.containsKey("token")) return null

        return Settings(
            token = map["token"].orEmpty(),
            nameProp = map["nameProp"].orEmpty().ifBlank { Settings().nameProp },
            priceProp = map["priceProp"].orEmpty().ifBlank { Settings().priceProp },
            dateProp = map["dateProp"].orEmpty().ifBlank { Settings().dateProp },
            purseProp = map["purseProp"].orEmpty().ifBlank { Settings().purseProp },
            categoryProp = map["categoryProp"].orEmpty().ifBlank { Settings().categoryProp },
            payDay = Payday.normalize(map["payDay"]?.toIntOrNull() ?: Payday.DEFAULT),
            personal = PurseSettings(
                databaseId = map["personal.db"].orEmpty(),
                monthlyBudget = map["personal.budget"]?.toLongOrNull() ?: 0L,
                name = map["personal.name"].orEmpty(),
            ),
            shared = PurseSettings(
                databaseId = map["shared.db"].orEmpty(),
                monthlyBudget = map["shared.budget"]?.toLongOrNull() ?: 0L,
                name = map["shared.name"].orEmpty(),
            ),
        )
    }
}
