package com.calc.expense

/**
 * 곳간의 종류. 둘은 서로 섞이지 않는다 — 공용에서 초과가 나도 개인 하루치는 줄지 않고,
 * 반대도 마찬가지다. 섞기 시작하면 "네 용돈에서 얼마 내"가 앱 안으로 들어온다.
 *
 * [key] 는 저장에만 쓴다. 값을 바꾸면 기존 곳간과 캐시를 잃는다.
 */
enum class Purse(val key: String, val label: String) {
    PERSONAL("personal", "개인"),
    SHARED("shared", "공용"),
}
