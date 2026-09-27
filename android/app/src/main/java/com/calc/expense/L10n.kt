package com.calc.expense

import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 앱이 말할 수 있는 언어. [code] 는 저장에만 쓴다. */
enum class Lang(val code: String, val locale: Locale) {
    KO("ko", Locale.KOREA),
    EN("en", Locale.ENGLISH),
    ES("es", Locale.forLanguageTag("es"));

    companion object {
        fun ofCode(code: String?): Lang? = entries.firstOrNull { it.code == code }

        /** 폰 언어가 영어·스페인어면 그 말로, 그 밖은 한국어로. */
        fun fromSystem(language: String?): Lang = when (language) {
            "en" -> EN
            "es" -> ES
            else -> KO
        }
    }
}

/**
 * 화면·알림 문구의 번역. 문구는 쓰는 자리에서 세 언어를 나란히 적는다 — `tr("오늘", "Today", "Hoy")`.
 *
 * 문구 조립이 [StatusText] 같은 순수 객체 안에 있어 Android 리소스를 쓰면 테스트가 Context 를
 * 들고 다녀야 한다. 그래서 지금 언어를 여기 하나에 두고 순수 코드가 그대로 읽는다. 앱이 뜰 때
 * [ExpenseApp] 이 저장된 선택을 넣고, 테스트에서는 기본값 한국어 그대로다.
 *
 * **번역은 화면에만 한다.** 저장하는 값(기본 카테고리 이름·곳간 기본 이름)은 한국어 그대로 두고
 * 보여줄 때만 옮긴다 — 가정 공용 곳간은 두 폰이 다른 언어를 써도 같은 이름을 봐야 한다.
 */
object L10n {

    @Volatile
    var lang: Lang = Lang.KO

    fun tr(ko: String, en: String, es: String): String = when (lang) {
        Lang.KO -> ko
        Lang.EN -> en
        Lang.ES -> es
    }

    val locale: Locale get() = lang.locale

    /** 쉼표 숫자. 원화 금액이라 소수점이 없고, 언어가 바뀌어도 자릿수 표기는 같게 둔다. */
    fun figure(amount: Long): String = NumberFormat.getNumberInstance(Locale.KOREA).format(amount)

    /** 원화 금액. «4,500원» / «₩4,500». 음수는 부호를 앞에 둔다. */
    fun won(amount: Long): String = when (lang) {
        Lang.KO -> figure(amount) + "원"
        else -> if (amount < 0L) "-₩" + figure(-amount) else "₩" + figure(amount)
    }

    /** 히어로 숫자 옆에 따로 붙이는 단위. 한국어는 뒤, 나머지는 앞에 붙는다([wonPrefix]). */
    val wonSuffix: String get() = if (lang == Lang.KO) "원" else ""
    val wonPrefix: String get() = if (lang == Lang.KO) "" else "₩"

    /** «9월 15일» / «Sep 15» / «15 sept». */
    fun monthDay(): DateTimeFormatter = when (lang) {
        Lang.KO -> DateTimeFormatter.ofPattern("M월 d일", locale)
        Lang.EN -> DateTimeFormatter.ofPattern("MMM d", locale)
        Lang.ES -> DateTimeFormatter.ofPattern("d MMM", locale)
    }

    /** «9월 15일 월요일» / «Monday, Sep 15» / «lunes, 15 sept». */
    fun fullDay(): DateTimeFormatter = when (lang) {
        Lang.KO -> DateTimeFormatter.ofPattern("M월 d일 EEEE", locale)
        Lang.EN -> DateTimeFormatter.ofPattern("EEEE, MMM d", locale)
        Lang.ES -> DateTimeFormatter.ofPattern("EEEE, d MMM", locale)
    }

    /** «9월 15일 (월)» / «Mon, Sep 15» / «lun, 15 sept». */
    fun dayWithWeekday(): DateTimeFormatter = when (lang) {
        Lang.KO -> DateTimeFormatter.ofPattern("M월 d일 (E)", locale)
        Lang.EN -> DateTimeFormatter.ofPattern("EEE, MMM d", locale)
        Lang.ES -> DateTimeFormatter.ofPattern("EEE, d MMM", locale)
    }

    /** «9/15» / «9/15» / «15/9». */
    fun shortDay(): DateTimeFormatter = when (lang) {
        Lang.ES -> DateTimeFormatter.ofPattern("d/M", locale)
        else -> DateTimeFormatter.ofPattern("M/d", locale)
    }

    /** «오후 3:05» / «3:05 PM» / «15:05». */
    fun time(): DateTimeFormatter = when (lang) {
        Lang.KO -> DateTimeFormatter.ofPattern("a h:mm", locale)
        Lang.EN -> DateTimeFormatter.ofPattern("h:mm a", locale)
        Lang.ES -> DateTimeFormatter.ofPattern("H:mm", locale)
    }

    /** «9월 15일 오후 3:05» / «Sep 15, 3:05 PM» / «15 sept, 15:05». */
    fun dayTime(): DateTimeFormatter = when (lang) {
        Lang.KO -> DateTimeFormatter.ofPattern("M월 d일 a h:mm", locale)
        Lang.EN -> DateTimeFormatter.ofPattern("MMM d, h:mm a", locale)
        Lang.ES -> DateTimeFormatter.ofPattern("d MMM, H:mm", locale)
    }

    /** «9월» / «Sep» / «sept». */
    fun month(): DateTimeFormatter = when (lang) {
        Lang.KO -> DateTimeFormatter.ofPattern("M월", locale)
        else -> DateTimeFormatter.ofPattern("MMM", locale)
    }

    /** 요일 한 글자 이름. «월» / «Mon» / «lun». */
    fun weekday(): DateTimeFormatter = when (lang) {
        Lang.KO -> DateTimeFormatter.ofPattern("E", locale)
        else -> DateTimeFormatter.ofPattern("EEE", locale)
    }

    /** 개수·날수 단위. 영어는 단복수를 가린다. */
    fun days(n: Int): String = when (lang) {
        Lang.KO -> "${n}일"
        Lang.EN -> if (n == 1) "1 day" else "$n days"
        Lang.ES -> if (n == 1) "1 día" else "$n días"
    }

    fun items(n: Int): String = when (lang) {
        Lang.KO -> "${n}건"
        Lang.EN -> if (n == 1) "1 entry" else "$n entries"
        Lang.ES -> if (n == 1) "1 gasto" else "$n gastos"
    }

    /** 영어 서수. 1st, 2nd, 3rd, 11th, 22nd … */
    fun ordinal(n: Int): String {
        val suffix: String = when {
            n % 100 in 11..13 -> "th"
            n % 10 == 1 -> "st"
            n % 10 == 2 -> "nd"
            n % 10 == 3 -> "rd"
            else -> "th"
        }
        return "$n$suffix"
    }

    /** 월급날처럼 «매달 며칠». «15일» / «the 15th» / «el día 15». */
    fun dayOfMonth(n: Int): String = tr("${n}일", "the ${ordinal(n)}", "el día $n")

    /** 곳간 기본 이름·기본 카테고리처럼 한국어로 저장되는 이름을 화면에 옮긴다. 모르는 이름은 그대로. */
    fun name(stored: String): String {
        if (lang == Lang.KO) return stored
        val pair: Pair<String, String> = STORED_NAMES[stored] ?: return stored
        return if (lang == Lang.EN) pair.first else pair.second
    }

    /**
     * [name] 의 반대. 설정 칸에 번역된 이름으로 보인 카테고리를 저장할 한국어로 되돌린다.
     * 한국어 화면에서는 되돌리지 않는다 — 한국어 사용자가 직접 만든 «Food» 칩은 그대로 둔다.
     */
    fun storedName(shown: String): String {
        if (lang == Lang.KO) return shown
        for ((stored, pair) in STORED_NAMES) {
            if (pair.first.equals(shown, ignoreCase = true)) return stored
            if (pair.second.equals(shown, ignoreCase = true)) return stored
        }
        return shown
    }

    private val STORED_NAMES: Map<String, Pair<String, String>> = mapOf(
        "개인" to ("Personal" to "Personal"),
        "공용" to ("Shared" to "Compartido"),
        "식비" to ("Food" to "Comida"),
        "카페" to ("Café" to "Café"),
        "간식" to ("Snacks" to "Snacks"),
        "마트" to ("Groceries" to "Súper"),
        "교통" to ("Transport" to "Transporte"),
        "생활" to ("Household" to "Hogar"),
        "건강" to ("Health" to "Salud"),
        "육아" to ("Kids" to "Niños"),
        "문화" to ("Leisure" to "Ocio"),
        "패션" to ("Fashion" to "Moda"),
        "주거" to ("Housing" to "Vivienda"),
        "기타" to ("Other" to "Otros"),
        "미분류" to ("Uncategorized" to "Sin categoría"),
        "없음" to ("None" to "Ninguna"),
    )
}

/** 짧게 부르려고. */
fun tr(ko: String, en: String, es: String): String = L10n.tr(ko, en, es)
