package com.calc.expense

/**
 * 도감의 선반. 업적의 **종류**다 — 같은 선반의 화분은 같은 무늬라, 빈 화분만 봐도
 * 무슨 쪽 업적인지 보인다. 단계가 오를수록 무늬가 차오르고 마지막 단계는 금테다.
 */
enum class Shelf(title: String, potName: String) {
    RECORD("적는 습관", "공책 화분"),
    PILE("쌓인 날", "벽돌 화분"),
    GRADE_S("S 등급", "별 화분"),
    KEEP("하루치 지킴", "달력 화분"),
    COMEBACK("다시 일어서기", "비 갠 하늘 화분"),
    NO_SPEND("무지출", "밤하늘 화분"),
    PERIOD("한 주 · 한 주기", "무지개 띠 · 동전 화분"),
    TIDY("정리", "유리병 · 저금통");

    private val titleKo: String = title
    private val potNameKo: String = potName

    val title: String get() = DogamText.shelf(this, 0, titleKo)
    val potName: String get() = DogamText.shelf(this, 1, potNameKo)
}

/**
 * 진척 막대로 보여주는 횟수. **더해지기만 하는 것만** 여기에 둔다 — 「연속 며칠」처럼 끊기면
 * 0 으로 돌아가는 숫자를 막대로 보여주면 끊긴 날 막대가 비면서 또 하나의 상실이 된다.
 */
enum class Tally(unit: String) {
    S_DAYS("번"),
    NO_SPEND_DAYS("번"),
    KEPT_WEEKS("번"),

    /** 적은 날을 모두 합친 수. 중간에 끊겨도 이어서 센다. */
    RECORDED_DAYS("일"),

    /** 하루치를 넘긴 바로 다음 날, 다시 하루치 안에서 마친 횟수. */
    COMEBACKS("번"),

    /** 10건 넘게 적고 미분류가 0건인 주기의 수. */
    TIDY_CYCLES("번");

    private val unitKo: String = unit

    /** 숫자 뒤에 붙는 단위. 한국어는 붙여 쓰고(«5번»), 다른 말은 띄어 쓴다(« times»). */
    val unit: String
        get() = if (unitKo == "일") tr("일", " days", " días") else tr("번", " times", " veces")
}

/**
 * 도감의 꽃 하나 = 업적 하나. **꽃말이 그 업적의 뜻**이다.
 *
 * 돈을 쓰게 만드는 조건은 두지 않는다(「새 가게 10곳」 같은 것) — 모으려고 쓰게 되니까.
 * 전부 적는 습관, 덜 쓴 날, 정리한 일에만 핀다. 한 번 핀 꽃은 시들지 않는다.
 *
 * [key] 는 저장에 쓴다. 바꾸면 이미 핀 꽃을 잃는다.
 *
 * @param meaning 꽃말. [isFlowerLanguage] 가 false 면 꽃말이 아니라 모습에서 붙인 뜻이다
 * @param story 왜 이 꽃인지 한 줄
 * @param condition 피는 조건(자세히)
 * @param short 선반 밑에 쓰는 짧은 조건
 * @param tally 진척을 세는 횟수. null 이면 막대 없이 조건만 보인다
 * @param target [tally] 가 이 값에 닿으면 핀다
 */
enum class Plant(
    val key: String,
    label: String,
    val shelf: Shelf,
    meaning: String,
    val isFlowerLanguage: Boolean,
    story: String,
    condition: String,
    short: String,
    val tally: Tally? = null,
    val target: Int = 0,
) {
    SPROUT(
        key = "sprout", label = "새싹", shelf = Shelf.RECORD,
        meaning = "시작", isFlowerLanguage = false,
        story = "처음으로 적었어요",
        condition = "처음으로 지출을 적은 날", short = "첫 기록",
    ),
    ROSEMARY(
        key = "rosemary", label = "로즈마리", shelf = Shelf.RECORD,
        meaning = "기억", isFlowerLanguage = true,
        story = "일주일 내내 빠짐없이 적었어요",
        condition = "7일 이어서 하루도 빠짐없이 적기", short = "7일 이어 적기",
    ),
    FORGET_ME_NOT(
        key = "forget_me_not", label = "물망초", shelf = Shelf.RECORD,
        meaning = "나를 잊지 마세요", isFlowerLanguage = true,
        story = "한 달 동안 하루도 안 잊었어요",
        condition = "30일 이어서 하루도 빠짐없이 적기", short = "30일 이어 적기",
    ),
    VIOLET(
        key = "violet", label = "제비꽃", shelf = Shelf.PILE,
        meaning = "성실", isFlowerLanguage = true,
        story = "적은 날이 30일이 됐어요",
        condition = "적은 날이 모두 30일 (끊겨도 이어서 셈)", short = "적은 날 30일",
        tally = Tally.RECORDED_DAYS, target = 30,
    ),
    COSMOS(
        key = "cosmos", label = "코스모스", shelf = Shelf.PILE,
        meaning = "조화", isFlowerLanguage = true,
        story = "쓰고 적는 게 몸에 붙었어요",
        condition = "적은 날이 모두 100일 (끊겨도 이어서 셈)", short = "적은 날 100일",
        tally = Tally.RECORDED_DAYS, target = 100,
    ),
    EDELWEISS(
        key = "edelweiss", label = "에델바이스", shelf = Shelf.PILE,
        meaning = "소중한 추억", isFlowerLanguage = true,
        story = "적은 날이 1년 치가 됐어요",
        condition = "적은 날이 모두 365일 (끊겨도 이어서 셈)", short = "적은 날 365일",
        tally = Tally.RECORDED_DAYS, target = 365,
    ),
    DAISY(
        key = "daisy", label = "데이지", shelf = Shelf.GRADE_S,
        meaning = "희망", isFlowerLanguage = true,
        story = "처음으로 S를 받았어요",
        condition = "하루 등급 S를 처음 받기", short = "S 첫 번째",
        tally = Tally.S_DAYS, target = 1,
    ),
    SUNFLOWER(
        key = "sunflower", label = "해바라기", shelf = Shelf.GRADE_S,
        meaning = "해를 따라 도는 꽃", isFlowerLanguage = false,
        story = "S가 자꾸 찾아와요",
        condition = "하루 등급 S를 5번 받기", short = "S 5번",
        tally = Tally.S_DAYS, target = 5,
    ),
    LAUREL(
        key = "laurel", label = "월계관", shelf = Shelf.GRADE_S,
        meaning = "승리", isFlowerLanguage = true,
        story = "S를 스무 번 받았어요",
        condition = "하루 등급 S를 20번 받기", short = "S 20번",
        tally = Tally.S_DAYS, target = 20,
    ),
    CLOVER(
        key = "clover", label = "세잎클로버", shelf = Shelf.KEEP,
        meaning = "행복", isFlowerLanguage = true,
        story = "사흘 연속 하루치를 지켰어요",
        condition = "하루치 안에서 3일 이어 쓰기", short = "3일 이어 지킴",
    ),
    PLUM(
        key = "plum", label = "매화", shelf = Shelf.KEEP,
        meaning = "인내", isFlowerLanguage = true,
        story = "추위를 버티고 먼저 피는 꽃이에요",
        condition = "하루치 안에서 7일 이어 쓰기", short = "7일 이어 지킴",
    ),
    BAMBOO(
        key = "bamboo", label = "대나무", shelf = Shelf.KEEP,
        meaning = "절개", isFlowerLanguage = true,
        story = "2주 연속 하루치를 지켰어요",
        condition = "하루치 안에서 14일 이어 쓰기", short = "14일 이어 지킴",
    ),
    CHAMOMILE(
        key = "chamomile", label = "캐모마일", shelf = Shelf.COMEBACK,
        meaning = "역경에 굴하지 않는 강인함", isFlowerLanguage = true,
        story = "많이 쓴 다음 날, 다시 지켰어요",
        condition = "하루치를 넘긴 바로 다음 날 하루치 안에서 쓰기", short = "넘긴 다음 날 지킴",
        tally = Tally.COMEBACKS, target = 1,
    ),
    SNOWDROP(
        key = "snowdrop", label = "설강화", shelf = Shelf.COMEBACK,
        meaning = "희망", isFlowerLanguage = true,
        story = "눈 속에서도 피는 꽃이에요",
        condition = "넘긴 다음 날 다시 지키기 5번", short = "다시 지킴 5번",
        tally = Tally.COMEBACKS, target = 5,
    ),
    HIBISCUS(
        key = "hibiscus", label = "무궁화", shelf = Shelf.COMEBACK,
        meaning = "끈기", isFlowerLanguage = true,
        story = "피고 지고 또 피는 꽃이에요",
        condition = "넘긴 다음 날 다시 지키기 15번", short = "다시 지킴 15번",
        tally = Tally.COMEBACKS, target = 15,
    ),
    LAVENDER(
        key = "lavender", label = "라벤더", shelf = Shelf.NO_SPEND,
        meaning = "침묵", isFlowerLanguage = true,
        story = "하루 동안 한 푼도 안 썼어요",
        condition = "개인 지갑을 0원으로 마친 날", short = "무지출한 날",
        tally = Tally.NO_SPEND_DAYS, target = 1,
    ),
    LILY_OF_THE_VALLEY(
        key = "lily_of_the_valley", label = "은방울꽃", shelf = Shelf.NO_SPEND,
        meaning = "틀림없이 행복해진다", isFlowerLanguage = true,
        story = "무지출한 날이 다섯 번이에요",
        condition = "무지출한 날 5번", short = "무지출 5번",
        tally = Tally.NO_SPEND_DAYS, target = 5,
    ),
    EVENING_PRIMROSE(
        key = "evening_primrose", label = "달맞이꽃", shelf = Shelf.NO_SPEND,
        meaning = "기다림", isFlowerLanguage = true,
        story = "무지출한 날이 스무 번이에요",
        condition = "무지출한 날 20번", short = "무지출 20번",
        tally = Tally.NO_SPEND_DAYS, target = 20,
    ),
    MARIGOLD(
        key = "marigold", label = "메리골드", shelf = Shelf.PERIOD,
        meaning = "반드시 오고야 말 행복", isFlowerLanguage = true,
        story = "한 주를 예산 안에서 보냈어요",
        condition = "월요일~일요일 한 주를 7일치 안에서 쓰기", short = "한 주 지키기",
        tally = Tally.KEPT_WEEKS, target = 1,
    ),
    OLIVE(
        key = "olive", label = "올리브", shelf = Shelf.PERIOD,
        meaning = "평화", isFlowerLanguage = true,
        story = "네 주를 예산 안에서 보냈어요",
        condition = "한 주 지키기 4번", short = "한 주 4번",
        tally = Tally.KEPT_WEEKS, target = 4,
    ),
    MONEY_TREE(
        key = "money_tree", label = "금전수", shelf = Shelf.PERIOD,
        meaning = "번영", isFlowerLanguage = true,
        story = "한 주기를 예산 안에서 마쳤어요",
        condition = "월급날부터 다음 월급날 전날까지 예산 안에서 쓰기", short = "한 주기 지키기",
    ),
    MINT(
        key = "mint", label = "민트", shelf = Shelf.TIDY,
        meaning = "상쾌함", isFlowerLanguage = false,
        story = "미분류 없이 깔끔하게 정리했어요",
        condition = "10건 넘게 적은 한 주기에 미분류 0건", short = "미분류 0건",
        tally = Tally.TIDY_CYCLES, target = 1,
    ),
    MONSTERA(
        key = "monstera", label = "몬스테라", shelf = Shelf.TIDY,
        meaning = "구멍 난 잎", isFlowerLanguage = false,
        story = "매달 나가는 돈을 정리했어요",
        condition = "고정비를 정해 두기", short = "고정비 정하기",
    ),
    BABYS_BREATH(
        key = "babys_breath", label = "안개꽃", shelf = Shelf.TIDY,
        meaning = "맑은 마음", isFlowerLanguage = true,
        story = "세 주기 내내 깔끔하게 정리했어요",
        condition = "10건 넘게 적고 미분류가 0건인 주기 3번", short = "미분류 0건 3번",
        tally = Tally.TIDY_CYCLES, target = 3,
    );

    // 한국어 원문. 다른 언어는 [DogamText] 가 [key] 로 찾아 준다.
    private val ko: Array<String> = arrayOf(label, meaning, story, condition, short)

    val label: String get() = DogamText.plant(this, 0, ko[0])
    val meaning: String get() = DogamText.plant(this, 1, ko[1])
    val story: String get() = DogamText.plant(this, 2, ko[2])
    val condition: String get() = DogamText.plant(this, 3, ko[3])
    val short: String get() = DogamText.plant(this, 4, ko[4])

    /** 「꽃말 「침묵」」. 꽃말이 아니라 모습에서 붙인 뜻이면 「뜻」이라 쓴다. */
    val meaningText: String
        get() = (
            if (isFlowerLanguage) tr("꽃말", "Flower meaning", "Significado")
            else tr("뜻", "Meaning", "Sentido")
            ) + tr(" ‘" + meaning + "’", ": “$meaning”", ": «$meaning»")

    companion object {
        fun ofKey(key: String): Plant? = entries.firstOrNull { it.key == key }

        fun on(shelf: Shelf): List<Plant> = entries.filter { it.shelf == shelf }

        /** 받침이 있으면 「이」, 없으면 「가」 — 「새싹이」「라벤더가」. 한글이 아니면 「가」. */
        fun subjectParticle(word: String): String {
            val last: Char = word.lastOrNull() ?: return "가"
            if (last !in '가'..'힣') return "가"
            return if ((last - '가') % 28 != 0) "이" else "가"
        }
    }
}
