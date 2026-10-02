package com.calc.expense

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 도감 탭에 필요한 것.
 *
 * @param fresh NEW 를 붙일 스탬프. 도감에서 아직 못 본 채 받은 것이다
 */
data class DogamUi(
    val result: DogamResult = DogamResult(),
    val fresh: Set<Plant> = emptySet(),
    val loading: Boolean = false,
    val error: String? = null,
    val purseLabels: Map<Purse, String> = emptyMap(),
)

private val DayFormat: DateTimeFormatter get() = L10n.monthDay()

/**
 * 도감 — 스탬프 북. 나무 탭의 «도감» 칸이다. 업적 하나에 도장 하나, 선반(업적 종류)마다 한 줄.
 *
 * 곳간은 많이 쓴 날 줄고 월급날 잘려 「제자리」가 되기도 한다. 이 탭의 도장은 **지워지지 않는다** —
 * 그게 이 탭이 있는 이유다. 판정은 [Dogam] 이 하고 여기서는 그리기만 한다.
 * 최고 기록은 통계의 «내 기록» 칸으로 옮겼다([RecordsScreen]).
 */
@Composable
fun DogamScreen(
    ui: DogamUi,
    today: LocalDate,
    onOpenSettings: () -> Unit = {},
    header: (@Composable () -> Unit)? = null,
) {
    var opened: Plant? by remember { mutableStateOf(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HomePalette.Ground)
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = TAB_TOP_PADDING, bottom = FLOATING_TOGGLE_SPACE),
    ) {
        // 나무 탭 안에서는 «나무 | 도감» 머리글을 함께 쓴다([TreeTab]).
        if (header != null) header() else Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = tr("도감", "Stamps", "Sellos"),
                color = HomePalette.Ink,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${ui.result.blooms.size} / ${Plant.entries.size}",
                color = HomePalette.Muted,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                style = Figures,
            )
            Spacer(Modifier.width(12.dp))
            SettingsGear(onOpenSettings)
        }
        Spacer(Modifier.height(14.dp))

        NextCard(ui.result, onOpen = { opened = it })
        for (shelf in Shelf.entries) {
            Spacer(Modifier.height(10.dp))
            ShelfCard(shelf, ui, onOpen = { opened = it })
        }
        Spacer(Modifier.height(12.dp))
        Foot(
            tr(
                "한 번 받은 스탬프는 지워지지 않아요. 곳간이 비어도 그대로예요.",
                "A stamp you've earned never fades. It stays even if your savings run out.",
                "Un sello conseguido no se borra. Se queda aunque se acabe el ahorro.",
            ),
        )

        if (ui.loading) {
            Spacer(Modifier.height(8.dp))
            Foot(tr("지난 기록을 다시 세는 중…", "Recounting past records…", "Recontando registros anteriores…"))
        }
        val error: String? = ui.error
        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Text(text = error, color = HomePalette.Over, fontSize = 12.sp)
        }
    }

    val plant: Plant? = opened
    if (plant != null) {
        StampDetailDialog(plant, ui.result, onDismiss = { opened = null })
    }
}

@Composable
private fun CardColumn(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(HomePalette.Card)
            .padding(16.dp),
    ) { content() }
}

@Composable
private fun Foot(text: String) {
    Text(text = text, color = HomePalette.Muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 4.dp))
}

/** 「다음 스탬프」. 가장 가까운 하나만. 막대는 줄지 않는 횟수로 세는 것에만 붙는다. */
@Composable
private fun NextCard(result: DogamResult, onOpen: (Plant) -> Unit) {
    val next: Plant? = Dogam.next(result)
    CardColumn {
        if (next == null) {
            Text(tr("스탬프 ${Plant.entries.size}개를 모두 모았어요", "You've collected all ${Plant.entries.size} stamps", "Has reunido los ${Plant.entries.size} sellos"), color = HomePalette.Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(tr("이제 내 기록을 깰 차례예요", "Now try breaking your records", "Ahora intenta batir tus récords"), color = HomePalette.Ink2, fontSize = 12.5f.sp)
        } else {
            NextStamp(next, result, onOpen)
        }
    }
}

@Composable
private fun NextStamp(next: Plant, result: DogamResult, onOpen: (Plant) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onOpen(next) }) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(72.dp).clip(CircleShape).background(HomePalette.Ground),
        ) {
            Stamp(next, got = false, size = 62.dp)
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(tr("다음 스탬프", "Next stamp", "Próximo sello"), color = HomePalette.Ink2, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(next.short, color = HomePalette.Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(next.condition, color = HomePalette.Ink2, fontSize = 12.sp)

            val tally: Tally? = next.tally
            if (tally != null && next.target > 0) {
                val count: Int = minOf(result.tallies.of(tally), next.target)
                Spacer(Modifier.height(8.dp))
                Bar(fraction = count.toFloat() / next.target)
                Spacer(Modifier.height(5.dp))
                Row {
                    Text(tr("지금 ", "Now ", "Ahora ") + "$count${tally.unit}", color = HomePalette.Ink2, fontSize = 12.sp, style = Figures, modifier = Modifier.weight(1f))
                    Text(tr("${next.target - count}${tally.unit} 더", "${next.target - count}${tally.unit} to go", "faltan ${next.target - count}${tally.unit}"), color = HomePalette.Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, style = Figures)
                }
            }
        }
    }
}

@Composable
private fun Bar(fraction: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(99.dp))
            .background(HomePalette.Chip),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(8.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(HomePalette.AccentBright),
        )
    }
}

/** 선반 하나 = 스탬프 한 줄. 칸은 셋 — 둘뿐인 선반은 한 칸이 빈다. */
@Composable
private fun ShelfCard(shelf: Shelf, ui: DogamUi, onOpen: (Plant) -> Unit) {
    val plants: List<Plant> = Plant.on(shelf)
    val got: Int = plants.count { it in ui.result.blooms }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(HomePalette.Card)
            .padding(horizontal = 12.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
            Text(shelf.title, color = HomePalette.Ink, fontSize = 14.5f.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("$got / ${plants.size}", color = HomePalette.Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, style = Figures)
        }
        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            for (slot in 0 until 3) {
                val plant: Plant? = plants.getOrNull(slot)
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    if (plant != null) {
                        Stamp(
                            plant = plant,
                            got = plant in ui.result.blooms,
                            size = 68.dp,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { onOpen(plant) },
                        )
                        Spacer(Modifier.height(6.dp))
                        SlotLabel(plant, ui)
                    }
                }
            }
        }
    }
}

@Composable
private fun SlotLabel(plant: Plant, ui: DogamUi) {
    val day: LocalDate? = ui.result.blooms[plant]
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = plant.short,
            color = if (day != null) HomePalette.Ink else HomePalette.Ink2,
            fontSize = 12.sp,
            fontWeight = if (day != null) FontWeight.Bold else FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (day != null && plant in ui.fresh) {
            Spacer(Modifier.width(4.dp))
            Tag("NEW")
        }
    }
    val sub: String? = if (day != null) day.format(DayFormat) else progress(plant, ui.result.tallies)
    if (sub != null) {
        Text(
            text = sub,
            color = HomePalette.Muted,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            style = Figures,
        )
    }
}

/** 아직 못 받은 칸의 진척(«8/20»). 횟수로 세지 않는 것은 없다. */
private fun progress(plant: Plant, tallies: Tallies): String? {
    val tally: Tally = plant.tally ?: return null
    if (plant.target <= 1) return null
    return "${minOf(tallies.of(tally), plant.target)}/${plant.target}"
}

@Composable
private fun Tag(text: String) {
    Text(
        text = text,
        color = Color.White,
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(HomePalette.AccentBright)
            .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}
