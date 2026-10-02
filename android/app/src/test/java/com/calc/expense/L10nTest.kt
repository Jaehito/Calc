package com.calc.expense

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class L10nTest {

    private val personal = LedgerSnapshot(
        purse = Purse.PERSONAL,
        label = "Personal",
        dailyRate = 30_000L,
        vault = 23_400L,
        todaySpent = 12_400L,
        cycleSpent = 387_600L,
        monthlyBudget = 930_000L,
        targetDay = LocalDate.of(2026, 9, 24),
        daysLeft = 22,
    )

    /** 언어는 전역 하나다. 다른 테스트가 한국어를 기대하므로 반드시 되돌린다. */
    @After
    fun resetLanguage() {
        L10n.lang = Lang.KO
    }

    @Test
    fun `기본은 한국어다`() {
        assertEquals(Lang.KO, L10n.lang)
        assertEquals("4,500원", L10n.won(4_500L))
    }

    @Test
    fun `폰 언어가 영어·스페인어면 그 말로, 그 밖은 한국어로`() {
        assertEquals(Lang.EN, Lang.fromSystem("en"))
        assertEquals(Lang.ES, Lang.fromSystem("es"))
        assertEquals(Lang.KO, Lang.fromSystem("ko"))
        assertEquals(Lang.KO, Lang.fromSystem("ja"))
        assertEquals(Lang.KO, Lang.fromSystem(null))
    }

    @Test
    fun `영어·스페인어는 원화 기호를 앞에 붙인다`() {
        L10n.lang = Lang.EN
        assertEquals("₩4,500", L10n.won(4_500L))
        assertEquals("-₩4,500", L10n.won(-4_500L))

        L10n.lang = Lang.ES
        assertEquals("₩930,000", L10n.won(930_000L))
    }

    @Test
    fun `영어로 기록 결과를 말한다`() {
        L10n.lang = Lang.EN
        val lines = StatusText.recorded("coffee", 4_500L, personal, "3:21 PM")

        assertEquals("✓ coffee 4,500 · left today 41,000", lines.summary)
        assertTrue(lines.detail.startsWith("✓ coffee ₩4,500 logged · 3:21 PM"))
        assertTrue(lines.detail.contains("Left to spend today ₩41,000"))
        assertEquals(
            "₩542,400 until Sep 24 · 22 days left (24,654/day)",
            StatusText.untilTarget(personal),
        )
    }

    @Test
    fun `스페인어로 기록 결과를 말한다`() {
        L10n.lang = Lang.ES
        val lines = StatusText.recorded("café", 4_500L, personal, "15:21")

        assertEquals("✓ café 4,500 · disponible hoy 41,000", lines.summary)
        assertTrue(lines.detail.startsWith("✓ café ₩4,500 anotado · 15:21"))
        assertTrue(StatusText.untilTarget(personal).startsWith("₩542,400 hasta el 24 "))
    }

    @Test
    fun `한 날·한 건은 단수로 센다`() {
        L10n.lang = Lang.EN
        assertEquals("1 day", L10n.days(1))
        assertEquals("3 days", L10n.days(3))
        assertEquals("1 entry", L10n.items(1))

        L10n.lang = Lang.ES
        assertEquals("1 día", L10n.days(1))
        assertEquals("5 gastos", L10n.items(5))
    }

    @Test
    fun `영어 서수`() {
        assertEquals("1st", L10n.ordinal(1))
        assertEquals("2nd", L10n.ordinal(2))
        assertEquals("3rd", L10n.ordinal(3))
        assertEquals("11th", L10n.ordinal(11))
        assertEquals("22nd", L10n.ordinal(22))
        assertEquals("25th", L10n.ordinal(25))
    }

    @Test
    fun `저장된 한국어 이름은 화면에서만 옮기고 되돌리면 같은 값이다`() {
        L10n.lang = Lang.EN
        assertEquals("Food", L10n.name("식비"))
        assertEquals("식비", L10n.storedName("Food"))
        assertEquals("식비", L10n.storedName("food"))

        L10n.lang = Lang.ES
        assertEquals("Comida", L10n.name("식비"))
        assertEquals("식비", L10n.storedName("Comida"))
    }

    @Test
    fun `모든 기본 카테고리가 오간다`() {
        for (lang in listOf(Lang.EN, Lang.ES)) {
            L10n.lang = lang
            for (stored in Categories.DEFAULT) {
                val shown: String = L10n.name(stored)
                assertTrue("$lang 에 $stored 번역이 없다", shown != stored)
                assertEquals(stored, L10n.storedName(shown))
            }
        }
    }

    @Test
    fun `사용자가 지은 이름은 번역하지 않는다`() {
        L10n.lang = Lang.EN
        assertEquals("우리집 장보기", L10n.name("우리집 장보기"))
        assertEquals("Groceries run", L10n.storedName("Groceries run"))
    }

    @Test
    fun `한국어 화면에서는 영어 칩 이름을 되돌리지 않는다`() {
        assertEquals("Food", L10n.storedName("Food"))
    }

    @Test
    fun `곳간 기본 이름은 언어를 따르고 저장 키는 그대로다`() {
        assertEquals("개인", Purse.PERSONAL.defaultLabel)

        L10n.lang = Lang.EN
        assertEquals("Personal", Purse.PERSONAL.defaultLabel)
        assertEquals("Shared", Purse.SHARED.defaultLabel)
        assertEquals("shared", Purse.SHARED.key)

        L10n.lang = Lang.ES
        assertEquals("Compartido", Purse.SHARED.defaultLabel)
    }

    @Test
    fun `도감의 모든 꽃과 선반에 영어·스페인어가 있다`() {
        for (lang in listOf(Lang.EN, Lang.ES)) {
            L10n.lang = lang
            for (plant in Plant.entries) {
                L10n.lang = Lang.KO
                val ko: List<String> = listOf(plant.label, plant.meaning, plant.story, plant.condition, plant.short)
                L10n.lang = lang
                val shown: List<String> = listOf(plant.label, plant.meaning, plant.story, plant.condition, plant.short)
                for (i in ko.indices) {
                    assertTrue("$lang ${plant.key}[$i] 번역이 없다", shown[i] != ko[i] || !ko[i].any { it in '가'..'힣' })
                }
            }
            for (shelf in Shelf.entries) {
                assertTrue("$lang ${shelf.name} 제목 번역이 없다", shelf.title.none { it in '가'..'힣' })
                assertTrue("$lang ${shelf.name} 화분 번역이 없다", shelf.potName.none { it in '가'..'힣' })
            }
        }
    }

    @Test
    fun `꽃말 줄도 언어를 따른다`() {
        L10n.lang = Lang.EN
        assertEquals("Flower meaning: “Silence”", Plant.LAVENDER.meaningText)
        assertEquals("Meaning: “Leaves with holes”", Plant.MONSTERA.meaningText)
    }

    @Test
    fun `추천 고정비 이름은 저장 길이 안에 든다`() {
        for (lang in Lang.entries) {
            L10n.lang = lang
            for (name in FixedCosts.SUGGESTED) {
                assertTrue("$lang «$name» 이 잘린다", name.length <= FixedCosts.MAX_NAME_LENGTH)
            }
        }
    }

    @Test
    fun `기본 지갑 이름이 그대로 저장돼 있으면 지금 언어로 보인다`() {
        val settings = Settings(personal = PurseSettings(name = "개인"), shared = PurseSettings(name = "공용"))
        L10n.lang = Lang.ES
        assertEquals("Compartido", settings.labelOf(Purse.SHARED))
        assertEquals("Personal", settings.labelOf(Purse.PERSONAL))
        L10n.lang = Lang.EN
        assertEquals("Shared", settings.labelOf(Purse.SHARED))
        // 직접 지은 이름은 옮기지 않는다.
        assertEquals("우리집", Settings(shared = PurseSettings(name = "우리집")).labelOf(Purse.SHARED))
        L10n.lang = Lang.KO
        assertEquals("공용", settings.labelOf(Purse.SHARED))
    }
}
