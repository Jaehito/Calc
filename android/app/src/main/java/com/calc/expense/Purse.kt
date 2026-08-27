package com.calc.expense

/**
 * 곳간의 종류. 둘은 서로 섞이지 않는다 — 공용에서 초과가 나도 개인 하루치는 줄지 않고,
 * 반대도 마찬가지다. 섞기 시작하면 "네 용돈에서 얼마 내"가 앱 안으로 들어온다.
 *
 * [key] 는 저장에만 쓴다. 값을 바꾸면 기존 곳간과 캐시를 잃는다.
 * [defaultLabel] 은 사용자가 이름을 정하지 않았을 때만 쓰인다 — 실제 표시 이름은
 * [Settings.labelOf] 로 얻는다.
 */
enum class Purse(val key: String, val defaultLabel: String) {
    PERSONAL("personal", "개인"),
    SHARED("shared", "공용");

    companion object {
        /**
         * 이름 길이 상한. 잠금화면 알림은 버튼 두 개를 한 줄에 넣으므로 길면 잘린다.
         * 자르는 건 저장할 때 한 번만 한다.
         */
        const val MAX_NAME_LENGTH = 8
    }
}
