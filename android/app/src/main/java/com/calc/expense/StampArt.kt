package com.calc.expense

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

/**
 * 도감 스탬프 그림. 업적 하나 = 도장 하나.
 *
 * 같은 선반은 같은 문양·같은 잉크 색이라, 빈 칸만 봐도 무슨 쪽 업적인지 보인다. 선반 안의 단계는
 * 문양 아래 점 개수(1~3)로 가른다. 받은 도장은 손으로 찍은 것처럼 칸마다 조금씩 기울어 있다.
 *
 * 꽃·화분을 쓰던 자리다. 나무를 키우는 기능과 겹치지 않게 식물이 아닌 그림으로 바꿨다.
 * [Plant] 의 key 는 그대로라 이미 받은 것은 그대로 남는다.
 */
object StampArt {

    @DrawableRes
    fun glyph(shelf: Shelf): Int = when (shelf) {
        Shelf.RECORD -> R.drawable.stamp_glyph_record
        Shelf.PILE -> R.drawable.stamp_glyph_pile
        Shelf.GRADE_S -> R.drawable.stamp_glyph_grade_s
        Shelf.KEEP -> R.drawable.stamp_glyph_keep
        Shelf.COMEBACK -> R.drawable.stamp_glyph_comeback
        Shelf.NO_SPEND -> R.drawable.stamp_glyph_no_spend
        Shelf.PERIOD -> R.drawable.stamp_glyph_period
        Shelf.TIDY -> R.drawable.stamp_glyph_tidy
    }

    /** 잉크 색. 선반마다 하나. */
    fun ink(shelf: Shelf): Color = when (shelf) {
        Shelf.RECORD -> Color(0xFF2E8B57)
        Shelf.PILE -> Color(0xFFB0793A)
        Shelf.GRADE_S -> Color(0xFFD98E1F)
        Shelf.KEEP -> Color(0xFF1F9C98)
        Shelf.COMEBACK -> Color(0xFFE0574F)
        Shelf.NO_SPEND -> Color(0xFF7C6BD6)
        Shelf.PERIOD -> Color(0xFF3F6FB5)
        Shelf.TIDY -> Color(0xFF546E9A)
    }

    /** 선반 안에서 몇 번째 단계인가(1부터). 문양 아래 점 개수다. */
    fun tier(plant: Plant): Int = Plant.on(plant.shelf).indexOf(plant) + 1

    /** 칸마다 다른 기울기. 같은 도장은 늘 같은 각도라 화면을 다시 그려도 흔들리지 않는다. */
    fun tilt(plant: Plant): Float = TILTS[plant.ordinal % TILTS.size]

    private val TILTS: FloatArray = floatArrayOf(-7f, 5f, -3f, 8f, -9f, 4f, -5f, 6f)
}

/** 아직 못 받은 칸의 점선·문양 색. */
private val EmptyInk = Color(0xFFD3D9DF)

/**
 * 도장 하나. [got] 이 false 면 점선 동그라미에 흐린 문양만 — 무엇을 받을 자리인지는 보이게 한다.
 * [inkAlpha] 는 찍히는 순간 잉크가 번져 나오는 연출([StampPressDialog])에 쓴다.
 *
 * 받은 도장은 잉크 얼룩 무늬([R.drawable.stamp_ink_mask])로 군데군데 지워 손으로 찍은 자국처럼 보인다.
 * 무늬의 어느 부분을 쓸지는 업적마다 정해져 있어, 칸마다 얼룩이 다르고 다시 그려도 같다.
 */
@Composable
fun Stamp(plant: Plant, got: Boolean, size: Dp, modifier: Modifier = Modifier, inkAlpha: Float = 1f) {
    val ink: Color = if (got) StampArt.ink(plant.shelf) else EmptyInk
    val mask: ImageBitmap = ImageBitmap.imageResource(R.drawable.stamp_ink_mask)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .rotate(if (got) StampArt.tilt(plant) else 0f)
            .alpha(if (got) inkAlpha * 0.93f else 1f)
            .then(if (got) Modifier.inkTexture(mask, plant.ordinal) else Modifier),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val unit: Float = this.size.minDimension / 60f
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            if (got) {
                drawCircle(ink, radius = 25f * unit, center = center, style = Stroke(width = 2.6f * unit))
                drawCircle(ink, radius = 20.5f * unit, center = center, style = Stroke(width = 1f * unit))
            } else {
                drawCircle(
                    EmptyInk,
                    radius = 25f * unit,
                    center = center,
                    style = Stroke(width = 2f * unit, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f * unit, 4f * unit))),
                )
            }
            // 단계 점. 문양 아래에 1~3개.
            val tier: Int = StampArt.tier(plant)
            val gap: Float = 4.6f * unit
            val startX: Float = center.x - gap * (tier - 1) / 2f
            for (i in 0 until tier) {
                drawCircle(ink, radius = 1.4f * unit, center = Offset(startX + gap * i, center.y + 14.5f * unit))
            }
        }
        Icon(
            painter = painterResource(StampArt.glyph(plant.shelf)),
            contentDescription = null,
            tint = ink,
            modifier = Modifier.size(size * 0.42f).offset(y = -size * 0.04f),
        )
    }
}

/**
 * 잉크 얼룩. 그린 도장 위에 얼룩 무늬를 DstIn 으로 얹어, 무늬가 옅은 곳의 잉크를 지운다.
 * 따로 한 장(offscreen)에 그려야 칸 바탕까지 지워지지 않는다.
 */
private fun Modifier.inkTexture(mask: ImageBitmap, seed: Int): Modifier = this
    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    .drawWithContent {
        drawContent()
        val window: Int = minOf(mask.width, mask.height) / 2
        val room: Int = minOf(mask.width, mask.height) - window
        drawImage(
            image = mask,
            srcOffset = IntOffset((seed * 67) % room, (seed * 131) % room),
            srcSize = IntSize(window, window),
            dstSize = IntSize(this.size.width.toInt(), this.size.height.toInt()),
            blendMode = BlendMode.DstIn,
        )
    }
