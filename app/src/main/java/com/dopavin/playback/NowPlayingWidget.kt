package com.dopavin.playback
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.view.KeyEvent
import android.widget.RemoteViews
import com.dopavin.MainActivity
import com.dopavin.R
import com.dopavin.data.ArtworkLoader
import com.dopavin.data.ArtworkSize
import kotlin.concurrent.thread
class NowPlayingWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        draw(context, manager, appWidgetIds, loadArt(context))
    }
    companion object {
        private const val PREFS = "now_playing_widget"
        fun push(context: Context, title: String, artist: String, artworkUri: String?, playing: Boolean) {
            val app = context.applicationContext
            app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("title", title).putString("artist", artist)
                .putString("art", artworkUri.orEmpty()).putBoolean("playing", playing).apply()
            val manager = AppWidgetManager.getInstance(app)
            val ids = manager.getAppWidgetIds(ComponentName(app, NowPlayingWidget::class.java))
            if (ids.isEmpty()) return
            draw(app, manager, ids, null)
            thread(name = "widget-art") {
                val bitmap = loadArt(app)
                if (bitmap != null) draw(app, manager, ids, bitmap)
            }
        }
        private fun loadArt(context: Context): android.graphics.Bitmap? {
            val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("art", "").orEmpty()
            if (raw.isBlank()) return null
            return runCatching { ArtworkLoader.loadBlocking(context, android.net.Uri.parse(raw), ArtworkSize.Row) }.getOrNull()
        }
        private fun draw(context: Context, manager: AppWidgetManager, ids: IntArray, art: android.graphics.Bitmap?) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val title = prefs.getString("title", "").orEmpty()
            val artist = prefs.getString("artist", "").orEmpty()
            val playing = prefs.getBoolean("playing", false)
            val views = RemoteViews(context.packageName, R.layout.widget_now_playing)
            val idle = title.isBlank()
            views.setTextViewText(R.id.widget_title, if (idle) context.getString(R.string.widget_idle) else title)
            views.setTextViewText(R.id.widget_artist, if (idle) context.getString(R.string.widget_idle_hint) else artist)
            views.setImageViewBitmap(R.id.widget_bg, WidgetStyle.background(art))
            if (art != null) views.setImageViewBitmap(R.id.widget_art, WidgetStyle.cover(art)) else views.setImageViewResource(R.id.widget_art, R.mipmap.ic_launcher)
            views.setImageViewResource(R.id.widget_play, if (playing) R.drawable.ic_pause else R.drawable.ic_play)
            for (id in intArrayOf(R.id.widget_prev, R.id.widget_play, R.id.widget_next)) {
                views.setInt(id, "setColorFilter", Color.WHITE)
            }
            views.setOnClickPendingIntent(R.id.widget_prev, mediaButton(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS, 1))
            views.setOnClickPendingIntent(R.id.widget_play, mediaButton(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 2))
            views.setOnClickPendingIntent(R.id.widget_next, mediaButton(context, KeyEvent.KEYCODE_MEDIA_NEXT, 3))
            val open = PendingIntent.getActivity(
                context, 4,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            views.setOnClickPendingIntent(R.id.widget_root, open)
            views.setOnClickPendingIntent(R.id.widget_art, open)
            manager.updateAppWidget(ids, views)
        }
        private fun mediaButton(context: Context, keyCode: Int, requestCode: Int): PendingIntent {
            val intent = Intent(Intent.ACTION_MEDIA_BUTTON).setComponent(ComponentName(context, PlaybackService::class.java))
                .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                PendingIntent.getForegroundService(context, requestCode, intent, flags)
            } else {
                PendingIntent.getService(context, requestCode, intent, flags)
            }
        }
    }
}
