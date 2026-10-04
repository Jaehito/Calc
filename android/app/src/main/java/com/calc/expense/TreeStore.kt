package com.calc.expense

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import java.time.LocalDate
import java.time.YearMonth

/**
 * 나무([TreeState])를 폰에 둔다. 바뀔 때마다 계정에도 맡긴다([TreeBackupSync]).
 *
 * 기록은 백그라운드 스레드(기록 창·잠금화면 답장·수집함)에서 오고 물 주기는 메인 스레드에서 오므로,
 * 읽고-고치고-쓰기를 한 자물쇠 안에서 한다.
 */
object TreeStore {

    private const val FILE = "tree"
    private const val KEY_WATER = "water"
    private const val KEY_GIVEN = "given"
    private const val KEY_PLANTED = "plantedOn"
    private const val KEY_SETTLED = "settledThrough"
    private const val KEY_RESTORED = "restoreChecked"
    private const val KEY_BUBBLE_SEEN = "bubbleSeen"
    private const val KEY_MY_SHARED = "mySharedRows"

    private val lock = Any()
    private val main = Handler(Looper.getMainLooper())

    /**
     * 나무가 바뀌면 부르는 곳(메인 스레드). 홈이 떠 있는 동안 등록해, 어디서 물을 얻든(기록 창·잠금화면 답장·
     * 수집함·아침 보너스) 나무 탭 숫자가 바로 바뀌게 한다. 한 번에 하나만 둔다 — 홈은 하나뿐이다.
     */
    @Volatile
    var onChange: ((TreeState) -> Unit)? = null

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun load(context: Context): TreeState {
        val p: SharedPreferences = prefs(context)
        return TreeState(
            water = p.getInt(KEY_WATER, 0),
            given = p.getInt(KEY_GIVEN, 0),
            plantedOn = parseDay(p.getString(KEY_PLANTED, null)),
            settledThrough = parseDay(p.getString(KEY_SETTLED, null)),
        )
    }

    /** 처음 보는 날 심는다. 이 날 전의 하루들은 보너스를 주지 않는다. */
    fun plantIfNeeded(context: Context, today: LocalDate = LocalDate.now()) {
        update(context) { if (it.plantedOn == null) it.copy(plantedOn = today, settledThrough = today.minusDays(1)) else it }
    }

    /**
     * 내가 한 건 적었다 — 개인이든 공용이든. 이 폰에서 적은 것은 언제나 내가 적은 것이다.
     * 공용 줄은 [rowId] 를 기억해 둔다 — 지울 때 배우자가 적은 줄인지 가려야 해서([loseRecord]).
     */
    fun earnRecord(context: Context, purse: Purse, rowId: String) {
        synchronized(lock) {
            if (purse == Purse.SHARED && rowId.isNotBlank()) {
                val mine: Set<String> = mySharedRows(context)
                prefs(context).edit().putStringSet(KEY_MY_SHARED, mine + rowId).commit()
            }
            update(context) { it.copy(water = it.water + WaterRules.PER_RECORD, plantedOn = it.plantedOn ?: LocalDate.now()) }
        }
    }

    /**
     * 한 건 지웠다. 공용은 내가 적은 줄일 때만 물을 뺀다 — 배우자 기록을 지웠다고 내 물이 줄면 안 된다.
     * 0 밑으로는 안 내려간다 — 이미 준 물은 돌려받지 않는다.
     */
    fun loseRecord(context: Context, purse: Purse, rowId: String) {
        synchronized(lock) {
            if (purse == Purse.SHARED) {
                val mine: Set<String> = mySharedRows(context)
                if (rowId !in mine) return
                prefs(context).edit().putStringSet(KEY_MY_SHARED, mine - rowId).commit()
            }
            update(context) { it.copy(water = maxOf(0, it.water - WaterRules.PER_RECORD)) }
        }
    }

    /** 내가 적은 공용 줄을 고치면 id 가 바뀐다([RecordExpense.edit]). 새 id 로 옮겨 적는다. */
    fun moveRecord(context: Context, oldRowId: String, newRowId: String) {
        synchronized(lock) {
            val mine: Set<String> = mySharedRows(context)
            if (oldRowId !in mine) return
            prefs(context).edit().putStringSet(KEY_MY_SHARED, mine - oldRowId + newRowId).commit()
        }
    }

    /** 복사해서 돌려준다 — SharedPreferences 가 준 집합을 그대로 고치면 안 된다. */
    private fun mySharedRows(context: Context): Set<String> =
        HashSet(prefs(context).getStringSet(KEY_MY_SHARED, null).orEmpty())

    /** 나무에 한 방울 준다. 물이 없으면 null. */
    fun give(context: Context): TreeState? {
        var after: TreeState? = null
        update(context) {
            if (it.water <= 0) it else it.copy(water = it.water - 1, given = it.given + 1).also { next -> after = next }
        }
        return after
    }

    /**
     * 어제까지 안 센 날의 보너스를 주고, 준 만큼을 돌려준다. 앱을 열 때 부른다.
     * 등급과 같은 로컬 캐시만 읽는다 — 네트워크가 없어도 된다.
     */
    fun settle(context: Context, today: LocalDate = LocalDate.now()): WaterGift {
        if (!PurseAccess.isLinked(context, GradeRepository.GRADED)) return WaterGift()
        plantIfNeeded(context, today)
        val months = HashMap<YearMonth, Map<LocalDate, Long>>()
        val recorded: (LocalDate) -> Boolean = { day ->
            months.getOrPut(YearMonth.from(day)) { SpendingCache.totals(context, GradeRepository.GRADED, YearMonth.from(day)) }
                .containsKey(day)
        }
        val grade: (LocalDate) -> Grade? = { day -> (GradeRepository.day(context, day) as? SpendingGrade.Graded)?.grade }

        var gift = WaterGift()
        update(context) { state ->
            val (found: WaterGift, through: LocalDate) = WaterRules.settle(state, today, recorded, grade)
            gift = found
            state.copy(water = state.water + found.total, settledThrough = through)
        }
        return gift
    }

    /** 기록 창 말풍선을 저절로 한 번 보여 줬는지. 처음 물을 받을 때 한 번만 알려 준다. */
    fun bubbleSeen(context: Context): Boolean = prefs(context).getBoolean(KEY_BUBBLE_SEEN, false)

    fun markBubbleSeen(context: Context) {
        prefs(context).edit().putBoolean(KEY_BUBBLE_SEEN, true).apply()
    }

    /** 계정 백업과 한 번이라도 맞춰 봤는지. 맞춰 보기 전에는 올리지 않는다 — 빈 나무가 맡겨 둔 나무를 덮지 않게. */
    fun restoreChecked(context: Context): Boolean = prefs(context).getBoolean(KEY_RESTORED, false)

    /** 계정에서 되찾은 나무를 합쳐 넣는다([TreeState.merge]). */
    fun applyRestored(context: Context, remote: TreeState?) {
        synchronized(lock) {
            val local: TreeState = load(context)
            val merged: TreeState = if (remote == null) local else TreeState.merge(local, remote)
            write(context, merged)
            prefs(context).edit().putBoolean(KEY_RESTORED, true).commit()
        }
        TreeBackupSync.schedulePush(context)
    }

    /** 계정이 바뀌면 그 계정의 나무다. */
    fun clear(context: Context) {
        synchronized(lock) { prefs(context).edit().clear().commit() }
    }

    private fun update(context: Context, change: (TreeState) -> TreeState) {
        val changed: Boolean
        synchronized(lock) {
            val before: TreeState = load(context)
            val after: TreeState = change(before)
            changed = after != before
            if (changed) write(context, after)
        }
        if (changed) TreeBackupSync.schedulePush(context)
    }

    private fun write(context: Context, state: TreeState) {
        val listener: ((TreeState) -> Unit)? = onChange
        if (listener != null) main.post { listener(state) }
        prefs(context).edit()
            .putInt(KEY_WATER, state.water)
            .putInt(KEY_GIVEN, state.given)
            .putString(KEY_PLANTED, state.plantedOn?.toString())
            .putString(KEY_SETTLED, state.settledThrough?.toString())
            .commit()
    }

    private fun parseDay(raw: String?): LocalDate? = try {
        raw?.let { LocalDate.parse(it) }
    } catch (_: Exception) {
        null
    }
}
