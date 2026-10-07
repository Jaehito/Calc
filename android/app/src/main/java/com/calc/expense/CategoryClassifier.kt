package com.calc.expense

/**
 * 지출 이름을 보고 카테고리를 짐작한다. 네트워크·LLM 없이 낱말 규칙만으로 판단한다 —
 * 잠금화면에서 타이핑하는 즉시 반응해야 하기 때문이다.
 *
 * **가장 긴 낱말이 이긴다.** 「코스트코 음식」에는 「코스트코」와 「음식」이 함께 들어 있는데,
 * 긴 쪽이 더 구체적이라 더 많은 것을 말해 준다 — 「음식」은 거의 모든 지출에 붙을 수 있는
 * 말이지만 「코스트코」는 한 곳을 가리킨다. 길이가 같으면 아래 [RULES] 의 순서가 가른다.
 *
 * **같은 낱말을 여러 칸에 둔다.** 그것이 곧 대비책이다 — 「기저귀」는 육아이면서 생활이고,
 * 사용자의 칩 목록에 「육아」가 있으면 육아로, 없으면 생활로 간다. 길이가 같아 순서가
 * 가르므로, 더 구체적인 칸을 위에 둔다. 존재하지 않는 칩을 가리키는 규칙은 조용히 꺼진다.
 *
 * **«기타» 는 규칙이 없다** — 무엇이든 «기타» 로 밀어넣는 건 분류가 아니다.
 *
 * **영어·스페인어는 낱말 단위로 맞춘다.** 한국어는 「다나약국」처럼 붙여 쓰니 글자 속 어디에
 * 있어도 잡지만, 띄어 쓰는 말을 그렇게 잡으면 bar 가 barber 에, tea 가 steak 에 걸린다.
 * 뒤에 s·es 가 붙은 복수형(tacos·panes)은 같은 낱말로 보고, 악센트는 떼고 본다(café = cafe).
 *
 * Android 에 의존하지 않아 단위 테스트로 고정한다.
 */
object CategoryClassifier {

    /**
     * (카테고리, 그 안에 있으면 매칭으로 보는 낱말들).
     *
     * 실제 가계부 한 달 치(84건·이름 52가지)를 놓고 맞춰 본 목록이다. 상호명보다 **물건·끼니
     * 이름**이 훨씬 많다 — 손으로 적을 때 사람은 「우리카드 우아한형제들」이 아니라 「점심」·
     * 「기저귀」라고 쓴다. 결제 알림에서 오는 상호명은 따로 담는다.
     */
    private val RULES: List<Pair<String, List<String>>> = listOf(
        // 아이가 있는 집에서 가장 자주, 가장 크게 나가는 것들. 생활·식비보다 먼저 본다.
        "육아" to listOf(
            "기저귀", "이유식", "분유", "물티슈", "젖병", "쪽쪽이", "공갈젖꼭지", "턱받이",
            "유모차", "카시트", "아기", "신생아", "베이비", "유아", "돌봄", "돌봄비",
            "어린이집", "유치원", "놀이치료", "놀이방", "문화센터", "보행기", "내복",
            "기저귀크림", "체온계", "보리차", "장난감", "유아용", "아기옷", "아기물티슈",
        ),
        "과일" to listOf(
            "사과", "바나나", "딸기", "포도", "수박", "참외", "오렌지", "복숭아", "자두",
            "블루베리", "키위", "망고", "체리", "멜론", "토마토", "방울토마토", "한라봉",
            "샤인머스캣", "무화과", "과일", "귤",
        ),
        "간식" to listOf(
            "초코바", "초콜릿", "아이스크림", "과자", "젤리", "사탕", "쿠키", "도넛", "와플",
            "마카롱", "간식", "양갱", "약과", "붕어빵", "호떡", "군고구마", "팝콘", "견과", "빵",
        ),
        "카페" to listOf(
            "커피", "카페", "스타벅스", "이디야", "투썸", "메가커피", "빽다방", "컴포즈",
            "라떼", "아메리카노", "디저트", "케이크", "베이커리", "빵집", "스무디", "에이드",
            "버블티", "공차", "녹차",
        ),
        // 「술」은 낱말 하나로 두지 않는다 — 기술·미술·수술·예술이 전부 걸린다.
        // 술 이름과 마시러 가는 곳 이름으로만 잡는다.
        "술" to listOf(
            "소주", "맥주", "생맥", "막걸리", "와인", "위스키", "하이볼", "사케", "청주",
            "술집", "술값", "술자리", "안주", "포차", "이자카야", "호프집", "노래방",
            "참이슬", "처음처럼", "하이트", "맥주캔", "편맥",
        ),
        "식비" to listOf(
            "점심", "저녁", "아침", "식당", "국밥", "김밥", "분식", "치킨", "피자", "족발",
            "보쌈", "배달", "떡볶이", "라면", "컵라면", "삼겹살", "국수", "도시락", "회식",
            "고기", "닭", "닭가슴살", "계란", "달걀", "생선", "샐러드", "죽", "음식", "백반",
            "비빔밥", "덮밥", "우동", "돈까스", "마라탕", "쌀국수", "햄버거", "서브웨이",
            "맥도날드", "버거킹", "롯데리아", "김치", "반찬", "만두", "순대", "곱창", "초밥",
            "스시", "파스타", "샌드위치", "토스트", "삼각김밥", "감자탕", "설렁탕", "냉면",
            "짜장", "짬뽕", "탕수육", "배달의민족", "배민", "요기요", "쿠팡이츠", "우유", "두유",
        ),
        "생활" to listOf(
            "다이소", "세제", "휴지", "세탁", "생활용품", "청소", "수건", "쓰레기봉투",
            "손세정제", "비누", "샴푸", "린스", "바디워시", "치약", "칫솔", "섬유유연제",
            "건전지", "전구", "방향제", "락스", "키친타월", "지퍼백", "호일", "수세미",
            "고무장갑", "빨래", "드라이",
        ),
        "병원" to listOf(
            "병원", "의원", "진료", "치과", "한의원", "정형외과", "소아과", "이비인후과",
            "피부과", "안과", "내과", "산부인과", "응급실", "약국", "처방", "약",
        ),
        "건강" to listOf(
            "영양제", "비타민", "마사지", "헬스장", "필라테스", "요가", "단백질", "유산균",
            "홍삼", "건강검진", "도수치료",
        ),
        "마트" to listOf(
            "마트", "장보기", "이마트", "홈플러스", "롯데마트", "편의점", "gs25", "cu",
            "세븐일레븐", "코스트코", "슈퍼", "트레이더스", "노브랜드", "쿠팡", "마켓컬리",
            "오아시스", "생수", "음료", "음료수", "콜라", "사이다", "주스", "탄산수",
        ),
        "교통" to listOf(
            "택시", "버스", "버스비", "지하철", "주유", "주유소", "주차", "기차", "ktx", "srt",
            "고속버스", "톨게이트", "하이패스", "따릉이", "교통", "교통비", "기차표", "항공",
            "비행기", "렌트", "카카오티",
        ),
        "문화" to listOf(
            "영화", "공연", "전시", "도서", "서점", "넷플릭스", "유튜브프리미엄", "콘서트",
            "박물관", "책", "서적", "웨이브", "티빙", "디즈니", "왓챠", "지니뮤직", "스포티파이",
            "게임", "놀이공원",
        ),
        "패션" to listOf(
            "옷", "신발", "가방", "화장품", "미용실", "네일", "액세서리", "안경", "슬리퍼",
            "운동화", "양말", "속옷", "모자", "벨트", "지갑", "향수", "렌즈",
        ),
        "주거" to listOf(
            "월세", "관리비", "전기세", "전기요금", "가스비", "수도세", "공과금", "통신비",
            "보험료", "대출이자", "인터넷요금", "정수기", "도시가스",
        ),

        // 대비책 — 위 칸이 사용자 칩 목록에 없을 때만 걸린다. 낱말이 같아 길이가 같으므로
        // 위엣것이 먼저 이기고, 위가 꺼져 있을 때만 여기로 내려온다.
        "생활" to listOf("기저귀", "물티슈", "분유", "젖병"),
        "식비" to listOf("이유식", "술집", "포차", "이자카야", "호프집", "안주", "소주", "맥주", "막걸리", "와인"),
        "문화" to listOf("노래방"),
        "간식" to listOf("아이스크림", "과자", "초코바"),
        "건강" to listOf("병원", "약국", "약", "진료", "치과"),
        "마트" to listOf("과일", "사과", "바나나", "딸기", "수박", "포도"),
    )

    /**
     * 자주 쓰는 한국 가게·서비스 이름. 결제 알림은 물건이 아니라 상호로 오므로([PaymentParse]) 이 목록이
     * 그 몫을 한다. 흔한 낱말과 겹치는 이름(«자주»·«타다»·«플로» 등)은 뺐다 — 한글은 글자 속 어디서든 잡히니까.
     */
    private val BRANDS: List<Pair<String, List<String>>> = listOf(
        "육아" to listOf("키즈카페", "아기용품", "유아용품", "베이비페어", "맘큐"),
        "간식" to listOf("배스킨라빈스", "베스킨라빈스", "설빙", "던킨", "크리스피크림", "와플대학"),
        "카페" to listOf(
            "할리스", "탐앤탐스", "폴바셋", "커피빈", "블루보틀", "매머드", "더벤티", "텐퍼센트", "파스쿠찌",
            "엔제리너스", "커피에반하다", "바나프레소", "하삼동", "감성커피", "카페베네", "파리바게뜨", "뚜레쥬르",
            "성심당", "투썸플레이스",
        ),
        "술" to listOf("와인앤모어", "생활맥주", "역전할머니맥주", "투다리", "봉구비어"),
        "식비" to listOf(
            "맘스터치", "노브랜드버거", "프랭크버거", "쉐이크쉑", "써브웨이", "교촌", "비비큐", "bhc", "굽네",
            "네네치킨", "처갓집", "푸라닭", "도미노", "파파존스", "피자헛", "피자스쿨", "본죽", "김밥천국", "한솥",
            "이삭토스트", "홍콩반점", "역전우동", "명륜진사갈비", "아웃백", "빕스", "애슐리", "쿠우쿠우", "땡겨요",
            "김가네", "고봉민김밥", "죠스떡볶이", "엽기떡볶이", "신전떡볶이", "bbq", "kfc",
        ),
        "생활" to listOf("이케아", "모던하우스", "무인양품", "세탁특공대", "런드리고", "크린토피아"),
        "병원" to listOf("동물병원", "치과의원", "한방병원"),
        "건강" to listOf("헬스클럽", "피트니스", "크로스핏", "스포애니"),
        "마트" to listOf(
            "이마트24", "미니스톱", "이마트에브리데이", "gs더프레시", "롯데슈퍼", "하나로마트", "컬리", "ssg",
            "홈플러스익스프레스", "킴스클럽", "농협하나로", "식자재마트",
        ),
        "교통" to listOf(
            "카카오t", "카카오택시", "티머니", "코레일", "쏘카", "그린카", "gs칼텍스", "sk에너지",
            "s-oil", "에쓰오일", "현대오일뱅크", "알뜰주유소", "대한항공", "아시아나", "제주항공", "진에어",
            "티웨이", "에어부산", "공항철도", "고속도로", "주차장", "전기차충전",
        ),
        "문화" to listOf(
            "cgv", "메가박스", "롯데시네마", "교보문고", "영풍문고", "예스24", "알라딘", "밀리의서재",
            "쿠팡플레이", "인터파크", "티켓링크", "볼링", "당구", "pc방", "피씨방", "코인노래", "방탈출",
        ),
        "패션" to listOf(
            "무신사", "지그재그", "에이블리", "29cm", "w컨셉", "유니클로", "탑텐", "스파오", "에잇세컨즈",
            "나이키", "아디다스", "뉴발란스", "올리브영", "시코르", "아리따움", "다비치안경", "블루클럽",
        ),
        "주거" to listOf(
            "kt", "skt", "lgu+", "lg유플러스", "엘지유플러스", "알뜰폰", "한국전력", "한전", "아파트관리비",
            "수도요금", "코웨이", "sk매직", "쿠쿠렌탈", "청호나이스",
        ),
    )

    /**
     * 영어·스페인어 낱말([RULES] 와 같은 모양·같은 순서). 칸 이름은 저장되는 한국어 그대로다 —
     * 화면에서만 번역된다([L10n.name]). 각 칸의 번역 이름(Food·Comida 등)도 낱말로 넣었다.
     *
     * 뜻이 둘인 말은 뺐다 — 「ticket」(차표·공연표), 「agua」(생수·수도 요금), 「té」(차·대명사 te).
     */
    private val WORDS: List<Pair<String, List<String>>> = listOf(
        "육아" to listOf(
            "kids", "baby", "diaper", "nappy", "nappies", "baby wipes", "stroller", "car seat", "daycare",
            "kindergarten", "preschool", "nursery", "babysitter", "pacifier", "toy", "baby food",
            "niños", "bebé", "pañal", "toallita", "carrito", "guardería", "chupete", "juguete", "papilla",
        ),
        "과일" to listOf(
            "fruit", "apple", "banana", "strawberry", "strawberries", "grape", "watermelon", "orange",
            "peach", "blueberry", "blueberries", "kiwi", "mango", "cherry", "cherries", "melon", "tomato",
            "fruta", "manzana", "plátano", "fresa", "uva", "sandía", "naranja", "melocotón", "durazno",
            "cereza", "melón",
        ),
        "간식" to listOf(
            "snack", "chocolate", "candy", "ice cream", "icecream", "gelato", "cookie", "chips", "crisps",
            "donut", "doughnut", "waffle", "popcorn", "nuts", "gum", "bread",
            "chuches", "golosina", "helado", "galleta", "palomitas", "dulce", "chicle", "merienda", "pan",
        ),
        "카페" to listOf(
            "café", "coffee", "latte", "cappuccino", "espresso", "americano", "mocha", "starbucks",
            "dunkin", "tim hortons", "tea", "bubble tea", "boba", "smoothie", "bakery", "cake",
            "dessert", "pastry", "croissant", "muffin",
            "cafetería", "panadería", "pastelería", "pastel", "tarta", "postre", "batido",
        ),
        "술" to listOf(
            "drinks", "beer", "wine", "soju", "whisky", "whiskey", "vodka", "gin", "rum", "tequila",
            "cocktail", "sake", "bar", "pub", "brewery", "liquor", "booze", "alcohol", "happy hour", "karaoke",
            "bebidas", "cerveza", "caña", "vino", "cóctel", "cubata", "licor", "mezcal", "sidra",
        ),
        "식비" to listOf(
            "food", "lunch", "dinner", "breakfast", "brunch", "meal", "restaurant", "takeout", "takeaway",
            "delivery", "pizza", "burger", "hamburger", "sushi", "ramen", "noodles", "pasta", "sandwich",
            "taco", "burrito", "kebab", "chicken", "steak", "salad", "soup", "rice", "bbq", "eggs", "meat",
            "fish", "milk", "mcdonald", "mcdonalds", "kfc", "domino", "dominos", "chipotle", "doordash",
            "uber eats", "ubereats", "grubhub", "deliveroo", "just eat", "glovo", "rappi", "pedidosya",
            "comida", "almuerzo", "cena", "desayuno", "restaurante", "menú", "tapas", "hamburguesa",
            "pollo", "carne", "pescado", "ensalada", "sopa", "arroz", "paella", "bocadillo", "empanada",
            "huevo", "leche",
        ),
        "생활" to listOf(
            "household", "toilet paper", "detergent", "laundry", "cleaning", "soap", "shampoo",
            "toothpaste", "toothbrush", "tissues", "paper towels", "trash bags", "batteries", "light bulb",
            "dry cleaning", "sponge", "ikea", "daiso", "dollar store",
            "hogar", "detergente", "lavandería", "limpieza", "jabón", "champú", "papel higiénico",
            "pasta de dientes", "cepillo de dientes", "pañuelos", "basura", "pila", "bombilla",
            "tintorería", "suavizante", "lejía",
        ),
        "병원" to listOf(
            "hospital", "clinic", "doctor", "dentist", "dental", "pharmacy", "drugstore", "medicine",
            "prescription", "urgent care", "walgreens", "cvs",
            "clínica", "médico", "dentista", "farmacia", "medicina", "medicamento", "urgencias",
        ),
        "건강" to listOf(
            "health", "gym", "vitamin", "supplement", "protein", "massage", "yoga", "pilates", "fitness",
            "physio", "physiotherapy", "checkup",
            "salud", "gimnasio", "vitamina", "suplemento", "proteína", "masaje", "fisio", "fisioterapia",
        ),
        "마트" to listOf(
            "groceries", "grocery", "supermarket", "market", "walmart", "costco", "aldi", "lidl",
            "trader joe", "whole foods", "kroger", "tesco", "7-eleven", "seven eleven",
            "convenience store", "soda", "coke", "juice", "bottled water", "sparkling water",
            "súper", "supermercado", "mercado", "mercadona", "carrefour", "oxxo", "soriana", "la compra",
            "refresco", "zumo", "jugo",
        ),
        "교통" to listOf(
            "transport", "taxi", "cab", "uber", "lyft", "cabify", "bus", "metro", "subway", "train", "tram",
            "gas", "gas station", "fuel", "petrol", "parking", "toll", "flight", "airline", "plane",
            "car rental", "transit", "fare",
            "transporte", "autobús", "tren", "gasolina", "gasolinera", "aparcamiento", "estacionamiento",
            "peaje", "vuelo", "avión", "renfe",
        ),
        "문화" to listOf(
            "leisure", "movie", "cinema", "theater", "theatre", "concert", "museum", "book", "bookstore",
            "netflix", "spotify", "youtube", "disney", "hbo", "prime video", "game", "steam", "playstation",
            "xbox", "nintendo", "amusement park", "theme park", "zoo",
            "ocio", "cine", "película", "teatro", "concierto", "museo", "libro", "librería", "juego",
            "videojuego", "parque de atracciones",
        ),
        "패션" to listOf(
            "fashion", "clothes", "clothing", "shoe", "sneaker", "shirt", "dress", "jeans", "pants",
            "jacket", "coat", "bag", "handbag", "cosmetics", "makeup", "haircut", "barber", "salon",
            "nails", "manicure", "perfume", "glasses", "sunglasses", "sock", "underwear", "hat", "belt",
            "wallet", "zara", "h&m", "uniqlo", "nike", "adidas",
            "moda", "ropa", "zapato", "zapatilla", "camiseta", "camisa", "vestido", "pantalón", "chaqueta",
            "abrigo", "bolso", "maquillaje", "cosmético", "peluquería", "corte de pelo", "manicura",
            "gafas", "calcetín", "gorra", "cinturón", "cartera",
        ),
        "주거" to listOf(
            "housing", "rent", "electricity", "electric bill", "gas bill", "water bill", "utilities",
            "internet", "wifi", "phone bill", "mortgage", "insurance",
            "vivienda", "alquiler", "luz", "electricidad", "factura del gas", "butano", "hipoteca",
            "seguro", "comunidad",
        ),

        // 대비책 — [RULES] 의 대비책과 같다.
        "생활" to listOf("diaper", "nappy", "nappies", "baby wipes", "pañal", "toallita"),
        "식비" to listOf("baby food", "papilla", "bar", "pub", "beer", "wine", "cerveza", "vino"),
        "문화" to listOf("karaoke"),
        "간식" to listOf("ice cream", "icecream", "gelato", "helado", "chocolate", "cookie", "galleta", "candy"),
        "건강" to listOf(
            "hospital", "clinic", "doctor", "dentist", "dental", "pharmacy", "medicine",
            "clínica", "médico", "dentista", "farmacia", "medicina", "medicamento",
        ),
        "마트" to listOf("fruit", "fruta", "apple", "manzana", "banana", "plátano"),
    )

    /** 목록들을 한 번만 접어 둔다(소문자·악센트 뗌). 순서가 동점을 가르므로 한국어가 앞이다. */
    private val TABLE: List<Pair<String, List<String>>> =
        (RULES + BRANDS + WORDS).map { (category, keywords) -> category to keywords.map(::fold) }

    /** 적는 중 앞부분으로 짐작할 때 최소 길이. 한글은 세 글자(«스타벅»), 로마자는 네 글자(«coff»). */
    private const val MIN_PREFIX_HANGUL = 3
    private const val MIN_PREFIX_LATIN = 4

    /**
     * [name] 에서 카테고리를 짐작한다. 맞는 규칙이 없거나, 맞는 카테고리가 지금 칩 목록
     * ([categories]) 에 없으면 null — 그러면 화면은 «없음» 을 유지한다.
     *
     * 가장 긴 낱말이 이기고, 길이가 같으면 [RULES] 의 앞엣것이 이긴다.
     */
    fun classify(name: String, categories: List<String>, typing: Boolean = false): String? {
        val text: String = fold(name)
        if (text.isEmpty()) return null

        var best: String? = null
        var bestLength: Int = 0
        for ((category, keywords) in TABLE) {
            if (category !in categories) continue
            for (keyword in keywords) {
                if (keyword.length <= bestLength) continue
                if (!matches(text, keyword)) continue
                best = category
                bestLength = keyword.length
            }
        }
        if (best != null || !typing) return best
        return completePrefix(text, categories)
    }

    /**
     * 적는 중이면 마지막 낱말을 앞부분으로 보고 맞는 낱말을 찾는다 — «스타벅»까지 쳤으면 «스타벅스».
     * 숫자가 든 낱말(금액)은 건너뛴다. 맞는 게 여럿이면 [TABLE] 앞엣것(더 구체적인 칸)이 이긴다.
     * 다 친 이름에는 쓰지 않는다(결제 알림 상호 등) — 그건 앞부분이 아니라 다른 이름일 수 있다.
     */
    private fun completePrefix(text: String, categories: List<String>): String? {
        val tail: String = text.split(' ', '\t').lastOrNull { word -> word.isNotEmpty() && word.none { it.isDigit() } }
            ?: return null
        val hangul: Boolean = tail.any { it in '가'..'힣' }
        if (tail.length < (if (hangul) MIN_PREFIX_HANGUL else MIN_PREFIX_LATIN)) return null
        for ((category, keywords) in TABLE) {
            if (category !in categories) continue
            for (keyword in keywords) {
                if (keyword.length > tail.length && keyword.startsWith(tail)) return category
            }
        }
        return null
    }

    /** 한글이 든 낱말은 글자 속 어디서든, 아니면 낱말 단위로([containsWord]). */
    private fun matches(text: String, keyword: String): Boolean =
        if (keyword.any { it in '가'..'힣' }) text.contains(keyword) else containsWord(text, keyword)

    /**
     * [word] 가 앞뒤로 다른 영문자·숫자에 붙지 않고 들어 있는지. 뒤에 s·es 가 붙은 것까지 본다.
     * 한글은 낱말 글자로 치지 않는다 — 「cu편의점」의 cu 도 잡힌다.
     */
    private fun containsWord(text: String, word: String): Boolean {
        var from: Int = 0
        while (true) {
            val start: Int = text.indexOf(word, from)
            if (start < 0) return false
            from = start + 1
            if (start > 0 && isWordChar(text[start - 1])) continue
            val end: Int = start + word.length
            if (endsWord(text, end) || (text.startsWith("s", end) && endsWord(text, end + 1)) ||
                (text.startsWith("es", end) && endsWord(text, end + 2))
            ) return true
        }
    }

    private fun endsWord(text: String, at: Int): Boolean = at >= text.length || !isWordChar(text[at])

    private fun isWordChar(c: Char): Boolean = c in 'a'..'z' || c in '0'..'9'

    /**
     * 소문자로 바꾸고 라틴 악센트를 뗀다. 유니코드 분해(NFD)는 쓰지 않는다 — 한글 음절까지
     * 자모로 쪼개져 「약」이 「야구」 속에서 잡힌다.
     */
    private fun fold(raw: String): String {
        val out = StringBuilder(raw.length)
        for (c in raw.lowercase()) {
            out.append(
                when (c) {
                    'á', 'à', 'â', 'ä', 'ã' -> 'a'
                    'é', 'è', 'ê', 'ë' -> 'e'
                    'í', 'ì', 'î', 'ï' -> 'i'
                    'ó', 'ò', 'ô', 'ö', 'õ' -> 'o'
                    'ú', 'ù', 'û', 'ü' -> 'u'
                    'ñ' -> 'n'
                    'ç' -> 'c'
                    else -> c
                },
            )
        }
        return out.toString()
    }
}
