package com.calc.expense

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 도감 탭에 필요한 것.
 *
 * @param fresh NEW 를 붙일 꽃. 도감에서 아직 못 본 채 핀 꽃이다
 */
data class DogamUi(
    val result: DogamResult = DogamResult(),
    val fresh: Set<Plant> = emptySet(),
    val loading: Boolean = false,
    val error: String? = null,
    val purseLabels: Map<Purse, String> = emptyMap(),
)

private val MeaningInk = Color(0xFF6A55B8)
private val MeaningBack = Color(0xFFF3EEFB)
private val SegmentBack = Color(0xFFE2E9E6)
private val NextBack = Color(0xFFF4F1EA)
private val ShelfTop = Color(0xFFEBDDC6)
private val ShelfBottom = Color(0xFFDCC8A8)

private val DayFormat: DateTimeFormatter get() = L10n.monthDay()
private val FullDayFormat: DateTimeFormatter get() = L10n.fullDay()
private val ShortDayFormat: DateTimeFormatter get() = L10n.shortDay()

/**
 * 도감 탭. 위는 선반(업적마다 한 송이), 옆 칸은 나의 기록(최고 기록과 쌓인 횟수).
 *
 * 곳간은 많이 쓴 날 줄고 월급날 잘려 「제자리」가 되기도 한다. 이 탭의 숫자는 **줄지 않는다** —
 * 그게 이 탭이 있는 이유다. 판정은 [Dogam] 이 하고 여기서는 그리기만 한다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DogamScreen(ui: DogamUi, today: LocalDate, onOpenSettings: () -> Unit = {}) {
    var page: Int by rememberSaveable { mutableIntStateOf(0) }
    var opened: Plant? by remember { mutableStateOf(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HomePalette.Ground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = tr("도감", "Garden", "Jardín"),
                color = HomePalette.Ink,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            SettingsGear(onOpenSettings)
        }
        Spacer(Modifier.height(14.dp))
        Segments(page = page, bloomed = ui.result.blooms.size, onSelect = { page = it })
        Spacer(Modifier.height(12.dp))

        if (page == 0) {
            NextCard(ui.result)
            for (shelf in Shelf.entries) {
                Spacer(Modifier.height(10.dp))
                ShelfCard(shelf, ui, onOpen = { opened = it })
            }
            Spacer(Modifier.height(12.dp))
            Foot(
                tr(
                    "한 번 핀 꽃은 시들지 않아요. 곳간이 비어도 선반은 그대로예요.",
                    "A flower that has bloomed never wilts. Your shelves stay even if your savings run out.",
                    "Una flor que ha florecido nunca se marchita. Tus estantes siguen aunque se acabe el ahorro.",
                ),
            )
        } else {
            RecordsCard(ui, today)
            Spacer(Modifier.height(10.dp))
            TalliesCard(ui.result.tallies)
            Spacer(Modifier.height(12.dp))
            Foot(
                tr(
                    "처음 쓴 첫 주는 금액 기록에서 빠져요. 곳간이 비어도 이 숫자들은 줄지 않아요.",
                    "Your very first week doesn't count toward amount records. These numbers never go down, even if your savings run out.",
                    "Tu primera semana no cuenta para los récords de importe. Estos números nunca bajan, aunque se acabe el ahorro.",
                ),
            )
        }

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
        ModalBottomSheet(
            onDismissRequest = { opened = null },
            containerColor = HomePalette.Card,
        ) {
            PlantSheet(plant, ui.result, onClose = { opened = null })
        }
    }
}

@Composable
private fun Segments(page: Int, bloomed: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SegmentBack)
            .padding(3.dp),
    ) {
        Segment(
            text = tr("선반", "Shelves", "Estantes"),
            badge = "$bloomed/${Plant.entries.size}",
            selected = page == 0,
            onClick = { onSelect(0) },
            modifier = Modifier.weight(1f),
        )
        Segment(text = tr("내 기록", "My records", "Mis marcas"), badge = null, selected = page == 1, onClick = { onSelect(1) }, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun Segment(text: String, badge: String?, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) HomePalette.Card else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
    ) {
        Text(
            text = text,
            color = if (selected) HomePalette.Ink else HomePalette.Ink2,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
        if (badge != null) {
            Spacer(Modifier.width(5.dp))
            Text(text = badge, color = HomePalette.Accent, fontSize = 12.sp, fontWeight = FontWeight.Medium, style = Figures)
        }
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

/** 꽃말 한 조각. 꽃말이 아니라 모습에서 붙인 뜻이면 「뜻」이라 쓴다. */
@Composable
private fun MeaningChip(plant: Plant) {
    Text(
        text = plant.meaningText,
        color = MeaningInk,
        fontSize = 12.5f.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(MeaningBack)
            .padding(horizontal = 11.dp, vertical = 4.dp),
    )
}

/**
 * 「다음에 필 꽃」. 가장 가까운 한 송이만 보여준다. 이름과 꽃말을 미리 보여 줘서 「이걸 피우고
 * 싶다」가 생기게 한다. 막대는 줄지 않는 횟수로 세는 꽃에만 붙는다.
 */
@Composable
private fun NextCard(result: DogamResult) {
    val next: Plant? = Dogam.next(result)
    CardColumn {
        if (next == null) {
            Text(tr("${Plant.entries.size}송이를 모두 피웠어요", "All ${Plant.entries.size} flowers have bloomed", "Han florecido las ${Plant.entries.size} flores"), color = HomePalette.Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(tr("이제 내 기록을 깰 차례예요", "Now try breaking your records", "Ahora intenta batir tus récords"), color = HomePalette.Ink2, fontSize = 12.5f.sp)
        } else {
            NextPlant(next, result)
        }
    }
}

@Composable
private fun NextPlant(next: Plant, result: DogamResult) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(76.dp).clip(CircleShape).background(NextBack),
            ) {
                PlantPot(next, bloomed = false, size = 68.dp)
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(tr("다음에 필 꽃", "Next to bloom", "Próxima en florecer"), color = HomePalette.Ink2, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text(next.label, color = HomePalette.Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
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
        Spacer(Modifier.height(10.dp))
        MeaningChip(next)
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

/** 선반 하나. 칸은 셋 — 둘뿐인 선반은 한 칸이 빈다. */
@Composable
private fun ShelfCard(shelf: Shelf, ui: DogamUi, onOpen: (Plant) -> Unit) {
    val plants: List<Plant> = Plant.on(shelf)
    val bloomed: Int = plants.count { it in ui.result.blooms }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(HomePalette.Card)
            .padding(horizontal = 12.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
            Text(shelf.title, color = HomePalette.Ink, fontSize = 14.5f.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Text(shelf.potName, color = HomePalette.Muted, fontSize = 11.5f.sp, modifier = Modifier.weight(1f))
            Text("$bloomed/${plants.size}", color = HomePalette.Accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, style = Figures)
        }
        Spacer(Modifier.height(4.dp))

        Box(modifier = Modifier.fillMaxWidth()) {
            // 선반 판자. 화분이 그 위에 서 있도록 먼저 그린다.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 3.dp)
                    .fillMaxWidth()
                    .height(7.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Brush.verticalGradient(listOf(ShelfTop, ShelfBottom))),
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                for (slot in 0 until 3) {
                    val plant: Plant? = plants.getOrNull(slot)
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.weight(1f)) {
                        if (plant != null) {
                            PlantPot(
                                plant = plant,
                                bloomed = plant in ui.result.blooms,
                                size = 88.dp,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable { onOpen(plant) },
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(7.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            for (slot in 0 until 3) {
                val plant: Plant? = plants.getOrNull(slot)
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    if (plant != null) SlotLabel(plant, ui)
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
            text = plant.label,
            color = if (day != null) HomePalette.Ink else HomePalette.Ink2,
            fontSize = if (day != null) 13.sp else 12.5f.sp,
            fontWeight = if (day != null) FontWeight.Bold else FontWeight.SemiBold,
        )
        if (day != null && plant in ui.fresh) {
            Spacer(Modifier.width(4.dp))
            Tag("NEW")
        }
    }
    Text(
        text = if (day != null) day.format(DayFormat) else shortCondition(plant, ui.result.tallies),
        color = if (day != null) HomePalette.Muted else HomePalette.Ink2,
        fontSize = 11.sp,
        textAlign = TextAlign.Center,
        style = Figures,
        modifier = Modifier.padding(horizontal = 2.dp),
    )
}

/** 아직 안 핀 칸의 조건. 횟수로 세는 꽃은 지금까지를 붙인다(예: 「S 20번 · 8/20」). */
private fun shortCondition(plant: Plant, tallies: Tallies): String {
    val tally: Tally = plant.tally ?: return plant.short
    if (plant.target <= 1) return plant.short
    return "${plant.short} · ${minOf(tallies.of(tally), plant.target)}/${plant.target}"
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

/** 꽃 하나를 눌렀을 때. 피었으면 언제·무엇으로 폈는지, 아니면 무엇을 하면 피는지. */
@Composable
private fun PlantSheet(plant: Plant, result: DogamResult, onClose: () -> Unit) {
    val day: LocalDate? = result.blooms[plant]
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
    ) {
        PlantPot(plant, bloomed = day != null, size = 150.dp)
        Text(plant.label, color = HomePalette.Ink, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        MeaningChip(plant)
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (day != null) plant.story else tr("아직 피지 않았어요", "Not in bloom yet", "Aún no ha florecido"),
            color = HomePalette.Ink2,
            fontSize = 13.5f.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(HomePalette.Chip)
                .padding(horizontal = 14.dp, vertical = 4.dp),
        ) {
            if (day != null) {
                Fact(tr("핀 날", "Bloomed", "Floreció"), day.format(FullDayFormat))
                Fact(tr("어떻게", "How", "Cómo"), plant.condition)
            } else {
                Fact(tr("조건", "Condition", "Condición"), plant.condition)
                val tally: Tally? = plant.tally
                if (tally != null && plant.target > 1) {
                    Fact(tr("지금", "Now", "Ahora"), "${minOf(result.tallies.of(tally), plant.target)} / ${plant.target}${tally.unit}")
                }
            }
            Fact(
                tr("화분", "Pot", "Maceta"),
                tr(
                    "${plant.shelf.potName} · ${plant.shelf.title} 선반",
                    "${plant.shelf.potName} · ${plant.shelf.title} shelf",
                    "${plant.shelf.potName} · estante ${plant.shelf.title}",
                ),
            )
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onClose,
            colors = ButtonDefaults.buttonColors(containerColor = HomePalette.Accent),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            Text(tr("닫기", "Close", "Cerrar"), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun Fact(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Text(label, color = HomePalette.Ink2, fontSize = 13.sp, modifier = Modifier.width(76.dp))
        Text(value, color = HomePalette.Ink, fontSize = 13.sp, modifier = Modifier.weight(1f))
    }
}

/** 나의 기록 다섯 줄. 깨질 수만 있고 줄지 않는다. */
@Composable
private fun RecordsCard(ui: DogamUi, today: LocalDate) {
    val bests: PersonalBests = ui.result.bests
    CardColumn {
        val saved: Best? = bests.savedDay
        RecordRow(
            icon = R.drawable.dogam_ic_coin, tint = Color(0xFFD98E1F), back = Color(0xFFFFF4E0),
            title = tr("가장 많이 아낀 날", "Biggest saving day", "Día de mayor ahorro"),
            detail =
                if (saved != null) "${saved.from.format(DayFormat)} · " + tr("${StatusText.won(saved.spent)} 씀", "spent ${StatusText.won(saved.spent)}", "gastado ${StatusText.won(saved.spent)}")
                else tr("첫 주가 지나면 세기 시작해요", "Starts counting after your first week", "Empieza a contar tras tu primera semana"),
            value = saved?.let { L10n.wonPrefix + StatusText.figure(it.value) }, unit = L10n.wonSuffix,
            fresh = Dogam.isFresh(saved, today),
        )
        Divider()
        RecordRow(
            icon = R.drawable.dogam_ic_shield, tint = HomePalette.Accent, back = HomePalette.Soft,
            title = tr("하루치 최장 연속", "Longest on-budget streak", "Racha más larga en presupuesto"),
            detail = bests.keepRun?.let { range(it) } ?: tr(
                "하루치 안에서 마친 날이 이어지면 늘어요",
                "Grows as you end days within the daily amount in a row",
                "Crece al terminar días seguidos dentro de la cantidad diaria",
            ),
            value = bests.keepRun?.value?.toString(), unit = DAYS_UNIT,
            fresh = Dogam.isFresh(bests.keepRun, today),
        )
        Divider()
        val quiet: Best? = bests.noSpendRun
        RecordRow(
            icon = R.drawable.dogam_ic_moon, tint = Color(0xFF7C6BD6), back = Color(0xFFEEEBFA),
            title = tr("무지출 최장 연속", "Longest no-spend streak", "Racha más larga sin gastos"),
            detail = if (quiet != null) {
                val purse: Purse? = quiet.purse
                val where: String = if (purse != null) (ui.purseLabels[purse] ?: purse.defaultLabel) + " · " else ""
                where + range(quiet)
            } else {
                tr(
                    "지갑 하나라도 0원으로 마친 날부터 세요",
                    "Counts from a day when any wallet ends at ₩0",
                    "Cuenta desde un día en que alguna cartera termina en ₩0",
                )
            },
            value = quiet?.value?.toString(), unit = DAYS_UNIT,
            fresh = Dogam.isFresh(quiet, today),
        )
        Divider()
        RecordRow(
            icon = R.drawable.dogam_ic_week, tint = Color(0xFF3F82C9), back = Color(0xFFE6F0FA),
            title = tr("가장 적게 쓴 주", "Cheapest week", "Semana más barata"),
            detail = bests.cheapestWeek?.let { range(it) } ?: tr(
                "월요일부터 일요일까지 한 주가 지나면 나와요",
                "Shows up after a full Monday–Sunday week",
                "Aparece tras una semana completa de lunes a domingo",
            ),
            value = bests.cheapestWeek?.let { L10n.wonPrefix + StatusText.figure(it.value) }, unit = L10n.wonSuffix,
            fresh = Dogam.isFresh(bests.cheapestWeek, today),
        )
        Divider()
        RecordRow(
            icon = R.drawable.dogam_ic_pen, tint = Color(0xFFD9587F), back = Color(0xFFFCE8EF),
            title = tr("기록 최장 연속", "Longest logging streak", "Racha más larga anotando"),
            detail = bests.recordRun?.let { range(it) } ?: tr(
                "하루도 빠짐없이 적으면 늘어요",
                "Grows as you log without missing a day",
                "Crece al anotar sin saltarte ningún día",
            ),
            value = bests.recordRun?.value?.toString(), unit = DAYS_UNIT,
            fresh = Dogam.isFresh(bests.recordRun, today),
        )
    }
}

/** 기록 숫자 뒤 «일». */
private val DAYS_UNIT: String get() = tr("일", " days", " días")

private fun range(best: Best): String =
    if (best.from == best.to) best.from.format(ShortDayFormat)
    else "${best.from.format(ShortDayFormat)} ~ ${best.to.format(ShortDayFormat)}"

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(HomePalette.Line))
}

@Composable
private fun RecordRow(
    @DrawableRes icon: Int,
    tint: Color,
    back: Color,
    title: String,
    detail: String,
    value: String?,
    unit: String,
    fresh: Boolean,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 12.dp)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(back),
        ) {
            Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(21.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 제목이 길면(스페인어) 제목만 줄바꿈되고 «신기록» 표시는 자기 폭을 지킨다.
                Text(title, color = HomePalette.Ink, fontSize = 13.5f.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f, fill = false))
                if (fresh && value != null) {
                    Spacer(Modifier.width(6.dp))
                    Tag(tr("신기록", "New record", "Nuevo récord"))
                }
            }
            Text(detail, color = HomePalette.Muted, fontSize = 11.5f.sp, style = Figures)
        }
        Spacer(Modifier.width(8.dp))
        if (value == null) {
            Text("—", color = HomePalette.Muted, fontSize = 18.sp)
        } else {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, color = HomePalette.Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold, style = Figures)
                Text(unit, color = HomePalette.Ink, fontSize = 12.5f.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 1.dp, bottom = 2.dp))
            }
        }
    }
}

/** 더해지기만 하는 횟수 넷. 「다시 지킨 날」은 많이 쓴 다음 날을 칭찬하는 숫자다. */
@Composable
private fun TalliesCard(tallies: Tallies) {
    CardColumn {
        Text(tr("지금까지 쌓인 횟수", "Counts so far", "Totales hasta ahora"), color = HomePalette.Ink2, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TallyBox(tallies.sDays, tr("S 등급", "S grades", "Notas S"), Modifier.weight(1f))
            TallyBox(tallies.noSpendDays, tr("무지출한 날", "No-spend days", "Días sin gastos"), Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TallyBox(tallies.keptDays, tr("하루치 지킨 날", "On-budget days", "Días en presupuesto"), Modifier.weight(1f))
            TallyBox(tallies.comebacks, tr("다시 지킨 날", "Comeback days", "Días de recuperación"), Modifier.weight(1f))
        }
    }
}

@Composable
private fun TallyBox(count: Int, label: String, modifier: Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(HomePalette.Chip)
            .padding(vertical = 11.dp),
    ) {
        Text(count.toString(), color = HomePalette.Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold, style = Figures)
        Text(label, color = HomePalette.Ink2, fontSize = 11.5f.sp)
    }
}

/**
 * 「어제 등급」 팝업에 붙는 한 줄. 새 알림이나 팝업을 따로 만들지 않는다.
 * 같은 날 둘 이상 피었으면 「라벤더 외 1송이」로 묶는다.
 */
@Composable
fun BloomNotice(blooms: List<Plant>, onClick: () -> Unit) {
    val first: Plant = blooms.firstOrNull() ?: return
    val title: String =
        if (blooms.size == 1) tr(
            "${first.label}${Plant.subjectParticle(first.label)} 피었어요",
            "${first.label} bloomed",
            "Floreció ${first.label}",
        )
        else tr(
            "${first.label} 외 ${blooms.size - 1}송이가 피었어요",
            "${first.label} and ${blooms.size - 1} more bloomed",
            "Florecieron ${first.label} y ${blooms.size - 1} más",
        )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MeaningBack)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(46.dp).clip(CircleShape).background(HomePalette.Card),
        ) {
            PlantPot(first, bloomed = true, size = 42.dp)
        }
        Spacer(Modifier.width(11.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = HomePalette.Ink, fontSize = 13.5f.sp, fontWeight = FontWeight.Bold)
            Text("${first.meaningText} · " + tr("도감에서 보기", "See in Garden", "Ver en el jardín"), color = HomePalette.Ink2, fontSize = 11.5f.sp)
        }
        Chevron(tint = HomePalette.Ink2)
    }
}
