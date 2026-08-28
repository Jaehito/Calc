package com.calc.expense

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Figures = TextStyle(fontFeatureSettings = "tnum")

/**
 * 통계 탭. 위에는 기간 대비(최근 7일·이번 달)를, 아래에는 카테고리별 막대를 둔다.
 *
 * 채점하지 않는다 — 덜/더 썼다는 사실만. 카테고리 막대는 큰 것부터, 원값과 퍼센트를 함께 보여준다.
 */
@Composable
fun StatsScreen(
    data: StatsData,
    onToggleCategoryMonth: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HomePalette.Ground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
    ) {
        Text(text = "통계", color = HomePalette.Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        CompareCard("최근 7일", data.recent7, "이전 7일", data.prev7)
        Spacer(Modifier.height(12.dp))
        CompareCard("이번 달", data.thisMonth, "지난 달", data.lastMonth)

        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "카테고리 · ${data.categoryMonthLabel}",
                color = HomePalette.Ink,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (data.categoryMonthLabel == "이번 달") "지난 달 보기" else "이번 달 보기",
                color = HomePalette.Accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onToggleCategoryMonth)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
        Spacer(Modifier.height(12.dp))

        CardBox {
            when {
                data.error != null ->
                    Text(text = data.error, color = HomePalette.Over, fontSize = 13.sp)
                data.loadingCategories ->
                    Text(text = "노션에서 불러오는 중…", color = HomePalette.Muted, fontSize = 13.sp)
                data.categories.isEmpty() ->
                    Text(
                        text = "이 달에는 카테고리 지출이 없습니다. 노션 «카테고리» 속성을 채우면 여기에 나옵니다.",
                        color = HomePalette.Ink2,
                        fontSize = 13.sp,
                    )
                else -> CategoryList(data)
            }
        }
    }
}

/** 두 기간 총액과 차이. 덜 썼으면 초록, 더 썼으면 빨강. */
@Composable
private fun CompareCard(thisLabel: String, thisAmount: Long, prevLabel: String, prevAmount: Long) {
    val diff: Long = thisAmount - prevAmount
    CardBox {
        Text(text = thisLabel, color = HomePalette.Ink2, fontSize = 13.sp)
        Spacer(Modifier.height(2.dp))
        Text(
            text = StatusText.won(thisAmount),
            color = HomePalette.Ink,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            style = Figures,
        )
        Spacer(Modifier.height(6.dp))
        val line: String = when {
            diff < 0L -> "$prevLabel보다 ${StatusText.won(-diff)} 덜 썼어요"
            diff > 0L -> "$prevLabel보다 ${StatusText.won(diff)} 더 썼어요"
            else -> "$prevLabel과 똑같이 썼어요"
        }
        val color = when {
            diff < 0L -> HomePalette.Accent
            diff > 0L -> HomePalette.Over
            else -> HomePalette.Muted
        }
        Text(text = line, color = color, fontSize = 13.sp, style = Figures)
    }
}

@Composable
private fun CategoryList(data: StatsData) {
    Column {
        Text(
            text = "합계 ${StatusText.won(data.categoryTotal)}",
            color = HomePalette.Ink2,
            fontSize = 13.sp,
            style = Figures,
        )
        Spacer(Modifier.height(12.dp))
        for (slice in data.categories) {
            CategoryBar(slice)
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun CategoryBar(slice: CategorySlice) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = slice.name, color = HomePalette.Ink, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(
            text = "${StatusText.won(slice.amount)} · ${slice.percent}%",
            color = HomePalette.Ink2,
            fontSize = 13.sp,
            style = Figures,
        )
    }
    Spacer(Modifier.height(6.dp))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(HomePalette.Soft),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth((slice.percent.coerceIn(0, 100)) / 100f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(5.dp))
                .background(HomePalette.Accent),
        )
    }
}

@Composable
private fun CardBox(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(HomePalette.Card)
            .padding(20.dp),
    ) {
        content()
    }
}
