package com.calc.expense

/**
 * 도감의 선반. 업적의 **종류**다 — 같은 선반의 화분은 같은 무늬라, 빈 화분만 봐도
 * 무슨 쪽 업적인지 보인다. 단계가 오를수록 무늬가 차오르고 마지막 단계는 금테다.
 */
enum class Shelf(val title: String, val potName: String) {
    RECORD("적는 습관", "공책 화분"),
    GRADE_S("S 등급", "별 화분"),
    KEEP("하루치 지킴", "달력 화분"),
    NO_SPEND("무지출", "밤하늘 화분"),
    PERIOD("한 주 · 한 주기", "무지개 띠 · 동전 화분"),
    TIDY("정리", "유리병 · 저금통"),
}

/**
 * 진척 막대로 보여주는 횟수. **더해지기만 하는 것만** 여기에 둔다 — 「연속 며칠」처럼 끊기면
 * 0 으로 돌아가는 숫자를 막대로 보여주면 끊긴 날 막대가 비면서 또 하나의 상실이 된다.
 */
enum class Tally { S_DAYS, NO_SPEND_DAYS, KEPT_WEEKS }

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
    val label: String,
    val shelf: Shelf,
    val meaning: String,
    val isFlowerLanguage: Boolean,
    val story: String,
    val condition: String,
    val short: String,
    val tally: Tally? = null,
    val target: Int = 0,
) {
    SPROUT(
        key = "sprout", label = "새싹", shelf = Shelf.RECORD,
        meaning = "시작", isFlowerLanguage = false,
        story = "모든 건 한 줄에서 시작해요",
        condition = "처음으로 지출을 적은 날", short = "첫 기록",
    ),
    ROSEMARY(
        key = "rosemary", label = "로즈마리", shelf = Shelf.RECORD,
        meaning = "기억", isFlowerLanguage = true,
        story = "적는 게 곧 기억이 돼요",
        condition = "7일 이어서 하루도 빠짐없이 적기", short = "7일 이어 적기",
    ),
    FORGET_ME_NOT(
        key = "forget_me_not", label = "물망초", shelf = Shelf.RECORD,
        meaning = "나를 잊지 마세요", isFlowerLanguage = true,
        story = "한 달 동안 하루도 안 잊었어요",
        condition = "30일 이어서 하루도 빠짐없이 적기", short = "30일 이어 적기",
    ),
    DAISY(
        key = "daisy", label = "데이지", shelf = Shelf.GRADE_S,
        meaning = "희망", isFlowerLanguage = true,
        story = "첫 S, 해볼 만하다는 신호예요",
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
        story = "S 스무 번이면 이긴 거예요",
        condition = "하루 등급 S를 20번 받기", short = "S 20번",
        tally = Tally.S_DAYS, target = 20,
    ),
    CLOVER(
        key = "clover", label = "세잎클로버", shelf = Shelf.KEEP,
        meaning = "행복", isFlowerLanguage = true,
        story = "네잎(행운)이 아니라 세잎이에요. 운이 아니라 매일 지킨 거예요",
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
        story = "2주를 꺾이지 않고 버텼어요",
        condition = "하루치 안에서 14일 이어 쓰기", short = "14일 이어 지킴",
    ),
    LAVENDER(
        key = "lavender", label = "라벤더", shelf = Shelf.NO_SPEND,
        meaning = "침묵", isFlowerLanguage = true,
        story = "돈이 조용했던 하루에 피었어요",
        condition = "곳간 하나라도 0원으로 마친 날", short = "무지출의 날",
        tally = Tally.NO_SPEND_DAYS, target = 1,
    ),
    LILY_OF_THE_VALLEY(
        key = "lily_of_the_valley", label = "은방울꽃", shelf = Shelf.NO_SPEND,
        meaning = "틀림없이 행복해진다", isFlowerLanguage = true,
        story = "조용한 날이 다섯 번 쌓였어요",
        condition = "무지출의 날 5번", short = "무지출 5번",
        tally = Tally.NO_SPEND_DAYS, target = 5,
    ),
    MARIGOLD(
        key = "marigold", label = "메리골드", shelf = Shelf.PERIOD,
        meaning = "반드시 오고야 말 행복", isFlowerLanguage = true,
        story = "일주일을 지켜 낸 보상이에요",
        condition = "월요일~일요일 한 주를 7일치 안에서 쓰기", short = "한 주 지키기",
        tally = Tally.KEPT_WEEKS, target = 1,
    ),
    OLIVE(
        key = "olive", label = "올리브", shelf = Shelf.PERIOD,
        meaning = "평화", isFlowerLanguage = true,
        story = "한 달치 주가 평화로웠어요",
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
    ),
    MONSTERA(
        key = "monstera", label = "몬스테라", shelf = Shelf.TIDY,
        meaning = "구멍 난 잎", isFlowerLanguage = false,
        story = "잎의 구멍처럼, 새는 돈(고정비)을 찾아냈어요",
        condition = "고정비를 정해 두기", short = "고정비 정하기",
    );

    /** 「꽃말 「침묵」」. 꽃말이 아니라 모습에서 붙인 뜻이면 「뜻」이라 쓴다. */
    val meaningText: String
        get() = (if (isFlowerLanguage) "꽃말" else "뜻") + " 「" + meaning + "」"

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
