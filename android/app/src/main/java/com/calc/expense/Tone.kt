package com.calc.expense

/**
 * 결과 줄과 숫자를 어떤 색으로 보여줄지.
 *
 * 화면마다 따로 판단하면 지금의 XML 화면과 나중의 Compose 화면이 어긋난다.
 * 판정은 여기 한 곳에서만 하고, 각 화면은 색 값만 고른다.
 */
enum class Tone {
    /** 오늘 쓸 수 있는 돈이 남아 있다. */
    REMAINING,

    /** 오늘 쓸 수 있는 돈을 넘겼다. */
    OVER,

    /** 기록 자체가 되지 않았다. */
    FAILED,

    /** 예산이 없거나 아직 진행 중이라 판단할 근거가 없다. */
    NEUTRAL,
    ;

    companion object {
        /**
         * 기록 한 건의 결과 색.
         *
         * 실패는 예산과 무관하게 실패다 — Notion 에 들어가지도 않은 건을 두고
         * 남았는지 넘었는지 말하면 거짓말이 된다.
         */
        fun of(ok: Boolean, snapshot: LedgerSnapshot?): Tone = when {
            !ok -> FAILED
            snapshot == null -> NEUTRAL
            snapshot.isOver -> OVER
            else -> REMAINING
        }
    }
}
