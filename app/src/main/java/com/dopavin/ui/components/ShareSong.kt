package com.dopavin.ui.components
import android.content.Context
import android.content.Intent
import com.dopavin.R
import com.dopavin.data.Song
fun shareTextFor(song: Song): String {
    val head = if (song.artist.isBlank()) song.title else "${song.title} - ${song.artist}"
    val id = song.videoId
    return if (id.isNullOrBlank()) head else "$head\nhttps://music.youtube.com/watch?v=$id"
}
fun shareSong(context: Context, song: Song) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, shareTextFor(song))
    }
    val chooser = Intent.createChooser(send, context.getString(R.string.menu_share)).apply {
        if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(chooser)
    } catch (_: android.content.ActivityNotFoundException) {
    }
}
