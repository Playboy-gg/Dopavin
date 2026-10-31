package com.dopavin.ui.components

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.dopavin.R
import java.io.File

/** Colour scheme of a lyrics card. */
enum class LyricsCardStyle { Dark, Light, Accent }

const val LYRICS_CARD_MAX_LINES = 6

/**
 * Draws a shareable lyrics card (F-194): the picked lines in large bold text, then the song title and artist,
 * with the cover in a rounded corner when one is available. Pure drawing, no Compose; the size is fixed
 * (1080 wide, height follows the text) so the result is the same on every device.
 */
fun renderLyricsCard(
    lines: List<String>,
    title: String,
    artist: String,
    cover: Bitmap?,
    style: LyricsCardStyle,
    accent: Int,
): Bitmap {
    val width = 1080
    val pad = 84
    val (bg, ink, soft) = when (style) {
        LyricsCardStyle.Dark -> Triple(Color.parseColor("#111114"), Color.WHITE, Color.parseColor("#B3FFFFFF"))
        LyricsCardStyle.Light -> Triple(Color.parseColor("#F6F4F0"), Color.parseColor("#14141A"), Color.parseColor("#99141A1A"))
        LyricsCardStyle.Accent -> Triple(accent, Color.WHITE, Color.parseColor("#CCFFFFFF"))
    }
    val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ink
        textSize = 68f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val body = lines.joinToString("\n") { it.trim() }.ifBlank { " " }
    val textWidth = width - pad * 2
    val layout = StaticLayout.Builder.obtain(body, 0, body.length, textPaint, textWidth)
        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
        .setLineSpacing(0f, 1.18f)
        .build()
    val coverSize = 144
    val headerH = if (cover != null) coverSize + 72 else 0
    val footerH = 190
    val height = pad + headerH + layout.height + 72 + footerH
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(bg)

    var y = pad.toFloat()
    if (cover != null) {
        val clip = Path().apply { addRoundRect(RectF(pad.toFloat(), y, (pad + coverSize).toFloat(), y + coverSize), 28f, 28f, Path.Direction.CW) }
        canvas.save()
        canvas.clipPath(clip)
        canvas.drawBitmap(cover, null, RectF(pad.toFloat(), y, (pad + coverSize).toFloat(), y + coverSize), Paint(Paint.FILTER_BITMAP_FLAG))
        canvas.restore()
        y += coverSize + 72
    }
    canvas.save()
    canvas.translate(pad.toFloat(), y)
    layout.draw(canvas)
    canvas.restore()
    y += layout.height + 72

    val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ink; textSize = 44f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val artistPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = soft; textSize = 38f }
    canvas.drawText(android.text.TextUtils.ellipsize(title, titlePaint, textWidth.toFloat(), android.text.TextUtils.TruncateAt.END).toString(), pad.toFloat(), y + 44f, titlePaint)
    canvas.drawText(android.text.TextUtils.ellipsize(artist, artistPaint, textWidth.toFloat(), android.text.TextUtils.TruncateAt.END).toString(), pad.toFloat(), y + 100f, artistPaint)
    val brand = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = soft; textSize = 30f }
    canvas.drawText("Dopavin", pad.toFloat(), y + 158f, brand)
    return bitmap
}

/** Writes [bitmap] as a PNG into the app cache and opens the system share sheet for it. */
fun shareLyricsCard(context: Context, bitmap: Bitmap): Boolean {
    return try {
        val dir = File(context.cacheDir, "cards").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "lyrics-card.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = android.content.ClipData.newRawUri("", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, context.getString(R.string.lyrics_card_share)).apply {
            if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
        true
    } catch (e: Exception) {
        false
    }
}
