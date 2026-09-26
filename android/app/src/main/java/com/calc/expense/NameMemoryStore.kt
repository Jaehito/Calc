package com.calc.expense

import android.content.Context

/**
 * 이름 기억 저장소. 규칙은 [NameMemories], 직렬화는 [NameMemoryCodec] 이 하고
 * 여기는 SharedPreferences 입출력만 한다.
 *
 * 폰 안에만 둔다 — 「내가 이 가게를 뭐라고 부르는가」는 내 기기의 습관이다
 * ([CategoryMemoryStore] 와 같은 판단). 계정이 바뀌면 [AccountScope] 가 지운다.
 */
object NameMemoryStore {

    private const val FILE = "name_memory"
    private const val KEY = "map"

    /** 옛 버전이 남긴 «바뀐 이름이 열쇠가 된 줄»을 한 번 걷어 냈다는 표시. */
    private const val KEY_CHAINS_CLEANED = "chains_cleaned"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /**
     * 기억을 읽는다. 잘못 쌓인 줄([NameMemories.sanitize])은 걷어 내고, 걷어 낸 게 있으면
     * 그 자리에서 저장해 다음부터는 깨끗한 채로 읽는다.
     */
    fun load(context: Context): Map<String, String> {
        val p = prefs(context)
        val raw: Map<String, String> = NameMemoryCodec.decode(p.getString(KEY, null))
        val firstClean: Boolean = !p.getBoolean(KEY_CHAINS_CLEANED, false)
        val clean: Map<String, String> = NameMemories.sanitize(raw, dropChains = firstClean)
        if (clean.size != raw.size) save(context, clean)
        if (firstClean) p.edit().putBoolean(KEY_CHAINS_CLEANED, true).apply()
        return clean
    }

    private fun save(context: Context, memory: Map<String, String>) {
        prefs(context).edit().putString(KEY, NameMemoryCodec.encode(memory)).apply()
    }

    /** 설정 › 더보기에서 한 줄을 지운다. [key] 는 목록에 보이는 열쇠 그대로다. */
    fun forget(context: Context, key: String) {
        save(context, load(context) - key)
    }

    /** 기록이 저장된 뒤에 부른다. 실패한 기록으로 배우면 안 된다. */
    fun remember(context: Context, from: String, to: String) {
        if (from.isBlank() || to.isBlank()) return
        save(context, NameMemories.put(load(context), from, to))
    }

    /** 알림이 읽은 이름을 대신할 이름. 없으면 null — 그러면 읽은 그대로 쓴다. */
    fun recall(context: Context, parsed: String): String? =
        NameMemories.lookup(load(context), parsed)

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }
}
