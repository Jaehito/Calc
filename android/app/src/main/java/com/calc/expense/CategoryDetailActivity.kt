package com.calc.expense

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.LocalDate
import java.util.concurrent.Executors

/**
 * 한 카테고리를 이름으로 펼쳐 보고, 이름 단위로 다시 분류하는 화면.
 *
 * 통계 도넛의 조각과 주기 리포트의 막대에서 들어온다. 「미분류」 조각도 같은 화면이다 —
 * 분류 안 된 것을 치우는 일과 이미 분류된 것을 다시 나누는 일이 같은 동작이기 때문이다.
 * 이것이 있어야 카테고리를 어떻게 나눌지 **지금 완벽하게 정하지 않아도 된다.**
 */
class CategoryDetailActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_CATEGORY = "category"
        private const val EXTRA_FROM = "from"
        private const val EXTRA_TO = "to"

        /** @param category 빈 문자열이면 미분류 */
        fun intent(
            context: android.content.Context,
            category: String,
            from: LocalDate,
            to: LocalDate,
        ): Intent = Intent(context, CategoryDetailActivity::class.java)
            .putExtra(EXTRA_CATEGORY, category)
            .putExtra(EXTRA_FROM, from.toString())
            .putExtra(EXTRA_TO, to.toString())
    }

    private val io = Executors.newSingleThreadExecutor()

    private var detail: CategoryDetail by mutableStateOf(CategoryDetail("", emptyList(), loading = true))
    private var chips: List<String> by mutableStateOf(emptyList())
    private var busyName: String? by mutableStateOf(null)
    private var message: String? by mutableStateOf(null)

    private val category: String by lazy { intent.getStringExtra(EXTRA_CATEGORY).orEmpty() }
    private val from: LocalDate by lazy { readDate(EXTRA_FROM) }
    private val to: LocalDate by lazy { readDate(EXTRA_TO) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        chips = CategoryStore.load(this)
        detail = CategoryDetail(category, emptyList(), loading = true)

        setContent {
            CategoryDetailScreen(
                detail = detail,
                chips = chips,
                busyName = busyName,
                message = message,
                onAssign = { group, chosen -> assign(group, chosen) },
                onClose = { finish() },
            )
        }
        load()
    }

    override fun onDestroy() {
        io.shutdown()
        super.onDestroy()
    }

    private fun readDate(key: String): LocalDate = try {
        LocalDate.parse(intent.getStringExtra(key).orEmpty())
    } catch (_: Exception) {
        LocalDate.now()
    }

    private fun load() {
        io.execute {
            val loaded: CategoryDetail = CategoryDetailRepository.load(this, from, to, category)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                detail = loaded
            }
        }
    }

    /**
     * 그 이름의 줄 전체를 [chosen] 으로 옮긴다.
     *
     * 옮기고 나면 이 화면에서 그 덩어리가 사라진다 — 이 화면은 «이 카테고리에 무엇이 있나»를
     * 보여주는 자리라, 다른 카테고리로 간 것이 남아 있으면 거짓말이 된다. 그래서 다시 읽는다.
     */
    private fun assign(group: NameGroup, chosen: String) {
        if (chosen == category) return
        busyName = group.name
        message = null

        io.execute {
            val moved: Int = try {
                CategoryDetailRepository.assign(this, group, chosen)
            } catch (e: Exception) {
                0
            }

            val reloaded: CategoryDetail = CategoryDetailRepository.load(this, from, to, category)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                busyName = null
                detail = reloaded
                message = when {
                    moved <= 0 -> "옮기지 못했습니다"
                    chosen.isBlank() -> "${group.name} ${moved}건을 미분류로 뒀어요"
                    else -> "${group.name} ${moved}건을 ${chosen}으로 옮겼어요"
                }
            }
        }
    }
}
