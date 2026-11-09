package com.dopavin.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.cos
import kotlin.math.sin

/** The six hand-drawn "Pick a mood" tiles. Each one gets its own little scene, no stock photos. */
enum class CategoryArtKind { Charts, Punjabi, Bollywood, Romantic, Lofi, English, Regional }

@Composable
fun CategoryArt(kind: CategoryArtKind, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        when (kind) {
            CategoryArtKind.Charts -> drawCharts()
            CategoryArtKind.Punjabi -> drawPunjabi()
            CategoryArtKind.Bollywood -> drawBollywood()
            CategoryArtKind.Romantic -> drawRomantic()
            CategoryArtKind.Lofi -> drawLofi()
            CategoryArtKind.English -> drawEnglish()
            CategoryArtKind.Regional -> drawRegional()
        }
    }
}

/** Rising chart bars with a sparkle — "Charts". */
private fun DrawScope.drawCharts() {
    val w = size.width
    val h = size.height
    drawRect(Color(0xFF151827))
    drawCircle(Color(0xFFF6C453).copy(alpha = 0.12f), radius = h * 0.55f, center = Offset(w * 0.82f, h * 0.38f))
    drawCircle(Color(0xFFFF6A55).copy(alpha = 0.16f), radius = h * 0.28f, center = Offset(w * 0.62f, h * 0.62f))

    val baseY = h * 0.78f
    val barW = w * 0.035f
    val gap = w * 0.045f
    val startX = w * 0.60f
    val heights = listOf(0.20f, 0.33f, 0.47f, 0.61f, 0.35f)
    heights.forEachIndexed { index, fraction ->
        drawRoundRect(
            color = if (index == 3) Color(0xFFFF6A55) else Color(0xFFF6C453),
            topLeft = Offset(startX + index * (barW + gap), baseY - h * fraction),
            size = Size(barW, h * fraction),
            cornerRadius = CornerRadius(barW * 0.5f, barW * 0.5f),
        )
    }

    val spark = Offset(w * 0.90f, h * 0.22f)
    val sparkColor = Color(0xFFFFF3D6)
    drawLine(sparkColor, Offset(spark.x - h * 0.07f, spark.y), Offset(spark.x + h * 0.07f, spark.y), strokeWidth = h * 0.018f, cap = StrokeCap.Round)
    drawLine(sparkColor, Offset(spark.x, spark.y - h * 0.07f), Offset(spark.x, spark.y + h * 0.07f), strokeWidth = h * 0.018f, cap = StrokeCap.Round)
}

/** A tilted dhol with laces and tassels — "Punjabi hits". */
private fun DrawScope.drawPunjabi() {
    val w = size.width
    val h = size.height
    drawRect(Color(0xFF3A1B12))
    drawCircle(Color(0xFFF2A03D).copy(alpha = 0.13f), radius = h * 0.55f, center = Offset(w * 0.80f, h * 0.45f))

    rotate(-7f, pivot = Offset(w * 0.76f, h * 0.50f)) {
        val bodyLeft = w * 0.66f
        val bodyTop = h * 0.31f
        val bodyW = w * 0.25f
        val bodyH = h * 0.38f
        val cream = Color(0xFFF7E4C8)
        val brown = Color(0xFF6D2F18)

        drawRoundRect(
            color = Color(0xFFD98B3A),
            topLeft = Offset(bodyLeft, bodyTop),
            size = Size(bodyW, bodyH),
            cornerRadius = CornerRadius(h * 0.16f, h * 0.16f),
        )
        val headR = h * 0.15f
        val leftHead = Offset(bodyLeft, bodyTop + bodyH / 2)
        val rightHead = Offset(bodyLeft + bodyW, bodyTop + bodyH / 2)
        drawCircle(cream, headR, leftHead)
        drawCircle(cream, headR, rightHead)
        drawCircle(brown, headR * 0.62f, leftHead)
        drawCircle(brown, headR * 0.62f, rightHead)

        val lace = cream.copy(alpha = 0.65f)
        for (i in 0..3) {
            val x = bodyLeft + bodyW * (i + 0.5f) / 4f
            drawLine(lace, Offset(x, bodyTop + h * 0.035f), Offset(x + bodyW / 8, bodyTop + bodyH - h * 0.035f), strokeWidth = h * 0.012f)
            drawLine(lace, Offset(x + bodyW / 8, bodyTop + h * 0.035f), Offset(x, bodyTop + bodyH - h * 0.035f), strokeWidth = h * 0.012f)
        }

        val tassel = Color(0xFFC7203F)
        listOf(0.30f, 0.58f).forEach { offset ->
            val x = bodyLeft + bodyW * offset
            drawLine(tassel, Offset(x, bodyTop + bodyH), Offset(x - w * 0.012f, bodyTop + bodyH + h * 0.12f), strokeWidth = h * 0.014f, cap = StrokeCap.Round)
            drawCircle(tassel, h * 0.024f, Offset(x - w * 0.012f, bodyTop + bodyH + h * 0.13f))
        }
    }
}

/** A film reel and strip — "Bollywood". */
private fun DrawScope.drawBollywood() {
    val w = size.width
    val h = size.height
    drawRect(Color(0xFF2B0E2E))
    drawCircle(Color(0xFFE84A8A).copy(alpha = 0.15f), radius = h * 0.62f, center = Offset(w * 0.80f, h * 0.40f))

    rotate(-8f, pivot = Offset(w * 0.78f, h * 0.50f)) {
        val stripTop = h * 0.68f
        val stripH = h * 0.17f
        val stripLeft = w * 0.58f
        val stripW = w * 0.39f
        val bg = Color(0xFF2B0E2E)
        drawRoundRect(
            color = Color(0xFFF7C948).copy(alpha = 0.92f),
            topLeft = Offset(stripLeft, stripTop),
            size = Size(stripW, stripH),
            cornerRadius = CornerRadius(h * 0.04f, h * 0.04f),
        )
        for (i in 0..6) {
            val x = stripLeft + w * 0.03f + i * w * 0.052f
            drawRoundRect(bg, Offset(x, stripTop + stripH * 0.16f), Size(w * 0.022f, stripH * 0.24f), CornerRadius(h * 0.02f))
            drawRoundRect(bg, Offset(x, stripTop + stripH * 0.60f), Size(w * 0.022f, stripH * 0.24f), CornerRadius(h * 0.02f))
        }
    }

    val center = Offset(w * 0.79f, h * 0.42f)
    val radius = h * 0.30f
    val hole = Color(0xFF2B0E2E)
    drawCircle(Color(0xFFE84A8A), radius, center)
    drawCircle(hole, radius * 0.20f, center)
    for (i in 0 until 5) {
        val angle = Math.toRadians((i * 72 - 90).toDouble())
        val holeCenter = Offset(
            center.x + cos(angle).toFloat() * radius * 0.60f,
            center.y + sin(angle).toFloat() * radius * 0.60f,
        )
        drawCircle(hole, h * 0.045f, holeCenter)
    }
    drawCircle(Color(0xFFFFF3D6).copy(alpha = 0.55f), radius, center, style = Stroke(width = h * 0.014f))
}

/** Two overlapping hearts and a few petals — "Romantic". */
private fun DrawScope.drawRomantic() {
    val w = size.width
    val h = size.height
    drawRect(Color(0xFF3B0D1E))
    drawCircle(Color(0xFFFF5C8A).copy(alpha = 0.15f), radius = h * 0.60f, center = Offset(w * 0.78f, h * 0.42f))

    drawPath(heartPath(Offset(w * 0.71f, h * 0.48f), h * 0.55f), Color(0xFFFF5C8A))
    drawPath(heartPath(Offset(w * 0.90f, h * 0.30f), h * 0.30f), Color(0xFFFFB3C7))

    val petal = Color(0xFFFFD1DC).copy(alpha = 0.70f)
    drawCircle(petal, h * 0.022f, Offset(w * 0.61f, h * 0.20f))
    drawCircle(petal.copy(alpha = 0.45f), h * 0.016f, Offset(w * 0.81f, h * 0.72f))
    drawCircle(petal, h * 0.013f, Offset(w * 0.94f, h * 0.58f))
}

/** A cassette under a crescent moon — "Lo-fi". */
private fun DrawScope.drawLofi() {
    val w = size.width
    val h = size.height
    drawRect(Color(0xFF171A3A))
    drawCircle(Color(0xFFFFB86B), h * 0.22f, Offset(w * 0.89f, h * 0.25f))
    drawCircle(Color(0xFF171A3A), h * 0.19f, Offset(w * 0.83f, h * 0.16f))

    val star = Color.White.copy(alpha = 0.75f)
    drawCircle(star, h * 0.012f, Offset(w * 0.61f, h * 0.18f))
    drawCircle(star.copy(alpha = 0.55f), h * 0.009f, Offset(w * 0.96f, h * 0.55f))
    drawCircle(star.copy(alpha = 0.45f), h * 0.007f, Offset(w * 0.55f, h * 0.42f))

    val left = w * 0.57f
    val top = h * 0.40f
    val cw = w * 0.34f
    val ch = h * 0.37f
    drawRoundRect(
        color = Color(0xFF8B9DFF),
        topLeft = Offset(left, top),
        size = Size(cw, ch),
        cornerRadius = CornerRadius(h * 0.06f, h * 0.06f),
    )
    drawRoundRect(
        color = Color(0xFFFFE5D0).copy(alpha = 0.92f),
        topLeft = Offset(left + cw * 0.12f, top + ch * 0.18f),
        size = Size(cw * 0.76f, ch * 0.34f),
        cornerRadius = CornerRadius(h * 0.03f, h * 0.03f),
    )

    val reelY = top + ch * 0.64f
    listOf(left + cw * 0.30f, left + cw * 0.70f).forEach { reelX ->
        drawCircle(Color(0xFF2A2D52), h * 0.075f, Offset(reelX, reelY))
        drawCircle(Color(0xFFFFE5D0), h * 0.030f, Offset(reelX, reelY))
        for (i in 0..2) {
            val a = Math.toRadians((i * 120).toDouble())
            drawLine(
                Color(0xFF8B9DFF),
                Offset(reelX, reelY),
                Offset(reelX + cos(a).toFloat() * h * 0.065f, reelY + sin(a).toFloat() * h * 0.065f),
                strokeWidth = h * 0.012f,
                cap = StrokeCap.Round,
            )
        }
    }
    drawRoundRect(
        color = Color(0xFF2A2D52),
        topLeft = Offset(left + cw * 0.35f, top + ch * 0.84f),
        size = Size(cw * 0.30f, h * 0.018f),
        cornerRadius = CornerRadius(h * 0.009f, h * 0.009f),
    )
}

/** A globe with an orbit — "English hits". */
private fun DrawScope.drawEnglish() {
    val w = size.width
    val h = size.height
    drawRect(Color(0xFF0B2B3C))
    drawCircle(Color(0xFF6ED0E8).copy(alpha = 0.14f), radius = h * 0.58f, center = Offset(w * 0.80f, h * 0.42f))

    val center = Offset(w * 0.80f, h * 0.44f)
    val radius = h * 0.30f
    drawCircle(Color(0xFF6ED0E8), radius, center)

    val grid = Color(0xFF0B2B3C).copy(alpha = 0.75f)
    val gridWidth = h * 0.012f
    drawLine(grid, Offset(center.x - radius, center.y), Offset(center.x + radius, center.y), strokeWidth = gridWidth)
    drawLine(grid, Offset(center.x, center.y - radius), Offset(center.x, center.y + radius), strokeWidth = gridWidth)
    listOf(-0.5f, 0f, 0.5f).forEach { latitude ->
        val ry = radius * 0.45f
        val y = center.y + radius * latitude * 0.85f
        drawOval(
            color = grid,
            topLeft = Offset(center.x - radius, y - ry),
            size = Size(radius * 2f, ry * 2f),
            style = Stroke(width = gridWidth),
        )
    }
    listOf(0.35f, 0.70f).forEach { longitude ->
        drawOval(
            color = grid,
            topLeft = Offset(center.x - radius * longitude, center.y - radius),
            size = Size(radius * 2f * longitude, radius * 2f),
            style = Stroke(width = gridWidth),
        )
    }

    rotate(-18f, pivot = center) {
        drawOval(
            color = Color(0xFFFFE5D0).copy(alpha = 0.55f),
            topLeft = Offset(center.x - radius * 1.25f, center.y - radius * 0.38f),
            size = Size(radius * 2.5f, radius * 0.76f),
            style = Stroke(width = gridWidth),
        )
    }

    val spark = Color(0xFFFF6A55)
    drawCircle(spark, h * 0.028f, Offset(w * 0.61f, h * 0.20f))
    drawCircle(Color(0xFFFFE5D0).copy(alpha = 0.65f), h * 0.015f, Offset(w * 0.95f, h * 0.66f))
}

private fun heartPath(center: Offset, size: Float): Path = Path().apply {
    moveTo(center.x, center.y + size * 0.34f)
    cubicTo(
        center.x - size * 0.55f, center.y + size * 0.02f,
        center.x - size * 0.52f, center.y - size * 0.50f,
        center.x, center.y - size * 0.20f,
    )
    cubicTo(
        center.x + size * 0.52f, center.y - size * 0.50f,
        center.x + size * 0.55f, center.y + size * 0.02f,
        center.x, center.y + size * 0.34f,
    )
    close()
}

/** Sound waves spreading from a point: any language that has no scene of its own. */
private fun DrawScope.drawRegional() {
    val w = size.width
    val h = size.height
    drawRect(Color(0xFF2A1B3D))
    drawCircle(Color(0xFFE8A0FF).copy(alpha = 0.12f), radius = h * 0.58f, center = Offset(w * 0.80f, h * 0.42f))
    val center = Offset(w * 0.80f, h * 0.44f)
    listOf(0.12f, 0.22f, 0.32f, 0.42f).forEachIndexed { index, fraction ->
        drawCircle(
            color = Color(0xFFE8A0FF).copy(alpha = 0.85f - index * 0.18f),
            radius = h * fraction,
            center = center,
            style = Stroke(width = h * 0.022f),
        )
    }
    drawCircle(Color(0xFFFFD6F5), radius = h * 0.06f, center = center)
}
