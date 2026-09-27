package com.calc.expense

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp

/**
 * 꽃과 화분 그림. 둘을 따로 그려 겹친다 — 아직 안 핀 자리에는 같은 화분에 팻말만 꽂으면 되고,
 * 그래서 빈 화분만 봐도 무슨 종류의 업적인지 보인다.
 *
 * [Plant] 는 Android 를 모르게 두고 그림 연결은 여기서만 한다.
 */
object DogamArt {

    @DrawableRes
    fun plant(plant: Plant): Int = when (plant) {
        Plant.SPROUT -> R.drawable.dogam_plant_sprout
        Plant.ROSEMARY -> R.drawable.dogam_plant_rosemary
        Plant.FORGET_ME_NOT -> R.drawable.dogam_plant_forget_me_not
        Plant.VIOLET -> R.drawable.dogam_plant_violet
        Plant.COSMOS -> R.drawable.dogam_plant_cosmos
        Plant.EDELWEISS -> R.drawable.dogam_plant_edelweiss
        Plant.DAISY -> R.drawable.dogam_plant_daisy
        Plant.SUNFLOWER -> R.drawable.dogam_plant_sunflower
        Plant.LAUREL -> R.drawable.dogam_plant_laurel
        Plant.CLOVER -> R.drawable.dogam_plant_clover
        Plant.PLUM -> R.drawable.dogam_plant_plum
        Plant.BAMBOO -> R.drawable.dogam_plant_bamboo
        Plant.CHAMOMILE -> R.drawable.dogam_plant_chamomile
        Plant.SNOWDROP -> R.drawable.dogam_plant_snowdrop
        Plant.HIBISCUS -> R.drawable.dogam_plant_hibiscus
        Plant.LAVENDER -> R.drawable.dogam_plant_lavender
        Plant.LILY_OF_THE_VALLEY -> R.drawable.dogam_plant_lily_of_the_valley
        Plant.EVENING_PRIMROSE -> R.drawable.dogam_plant_evening_primrose
        Plant.MARIGOLD -> R.drawable.dogam_plant_marigold
        Plant.OLIVE -> R.drawable.dogam_plant_olive
        Plant.MONEY_TREE -> R.drawable.dogam_plant_money_tree
        Plant.MINT -> R.drawable.dogam_plant_mint
        Plant.MONSTERA -> R.drawable.dogam_plant_monstera
        Plant.BABYS_BREATH -> R.drawable.dogam_plant_babys_breath
    }

    /** 선반마다 무늬가 같고, 단계가 오를수록 무늬가 차오르다 마지막엔 금테다. */
    @DrawableRes
    fun pot(plant: Plant): Int = when (plant) {
        Plant.SPROUT -> R.drawable.dogam_pot_note1
        Plant.ROSEMARY -> R.drawable.dogam_pot_note2
        Plant.FORGET_ME_NOT -> R.drawable.dogam_pot_note3
        Plant.VIOLET -> R.drawable.dogam_pot_brick1
        Plant.COSMOS -> R.drawable.dogam_pot_brick2
        Plant.EDELWEISS -> R.drawable.dogam_pot_brick3
        Plant.DAISY -> R.drawable.dogam_pot_star1
        Plant.SUNFLOWER -> R.drawable.dogam_pot_star2
        Plant.LAUREL -> R.drawable.dogam_pot_star3
        Plant.CLOVER -> R.drawable.dogam_pot_cal1
        Plant.PLUM -> R.drawable.dogam_pot_cal2
        Plant.BAMBOO -> R.drawable.dogam_pot_cal3
        Plant.CHAMOMILE -> R.drawable.dogam_pot_rain1
        Plant.SNOWDROP -> R.drawable.dogam_pot_rain2
        Plant.HIBISCUS -> R.drawable.dogam_pot_rain3
        Plant.LAVENDER -> R.drawable.dogam_pot_moon1
        Plant.LILY_OF_THE_VALLEY -> R.drawable.dogam_pot_moon2
        Plant.EVENING_PRIMROSE -> R.drawable.dogam_pot_moon3
        Plant.MARIGOLD -> R.drawable.dogam_pot_week1
        Plant.OLIVE -> R.drawable.dogam_pot_week2
        Plant.MONEY_TREE -> R.drawable.dogam_pot_coin
        Plant.MINT -> R.drawable.dogam_pot_jar
        Plant.MONSTERA -> R.drawable.dogam_pot_pig
        Plant.BABYS_BREATH -> R.drawable.dogam_pot_jar2
    }
}

/** 화분 하나. 피었으면 꽃을, 아니면 「?」 팻말을 꽂는다. 꽃이 화분 뒤로 들어가게 먼저 그린다. */
@Composable
fun PlantPot(plant: Plant, bloomed: Boolean, size: Dp, modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(size)) {
        Image(
            painter = painterResource(if (bloomed) DogamArt.plant(plant) else R.drawable.dogam_seed),
            contentDescription = if (bloomed) plant.label else tr("아직 피지 않은 ${plant.label}", "${plant.label}, not yet in bloom", "${plant.label}, aún sin florecer"),
            modifier = Modifier.fillMaxSize(),
        )
        Image(
            painter = painterResource(DogamArt.pot(plant)),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
