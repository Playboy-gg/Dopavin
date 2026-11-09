package com.dopavin.playback

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.app.NotificationManagerCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import com.dopavin.R
import com.google.common.collect.ImmutableList

/**
 * The two extra buttons in the media notification, appended after Media3's
 * previous / play-pause / next row: a repeat-mode cycle and an app kill.
 */
@OptIn(UnstableApi::class)
object PlaybackNotification {

    const val ACTION_CYCLE_REPEAT = "com.dopavin.playback.CYCLE_REPEAT"
    const val ACTION_KILL_APP = "com.dopavin.playback.KILL_APP"

    /** Custom session commands the notification controller needs before it can send these actions. */
    val customCommands: List<SessionCommand> = listOf(
        SessionCommand(ACTION_CYCLE_REPEAT, Bundle.EMPTY),
        SessionCommand(ACTION_KILL_APP, Bundle.EMPTY),
    )

    /** Notification buttons, in the order they appear after skip-to-next. */
    fun layout(context: Context, repeatMode: Int): List<CommandButton> = listOf(
        CommandButton.Builder()
            .setSessionCommand(SessionCommand(ACTION_CYCLE_REPEAT, Bundle.EMPTY))
            .setIconResId(
                when (repeatMode) {
                    Player.REPEAT_MODE_ONE -> R.drawable.ic_repeat_one
                    Player.REPEAT_MODE_ALL -> R.drawable.ic_repeat
                    else -> R.drawable.ic_repeat_off
                },
            )
            .setDisplayName(
                context.getString(
                    when (repeatMode) {
                        Player.REPEAT_MODE_ONE -> R.string.cd_repeat_one
                        Player.REPEAT_MODE_ALL -> R.string.cd_repeat_all
                        else -> R.string.cd_repeat_off
                    },
                ),
            )
            .build(),
        CommandButton.Builder()
            .setSessionCommand(SessionCommand(ACTION_KILL_APP, Bundle.EMPTY))
            .setIconResId(R.drawable.ic_close)
            .setDisplayName(context.getString(R.string.cd_kill_app))
            .build(),
    )
}

/**
 * Media3's default notification provider, with one guard.
 *
 * Android 12+ refuses to start a foreground service while the app is in the background
 * (ForegroundServiceStartNotAllowedException). Media3 catches that on its direct path, but NOT when the
 * notification is updated later, after the cover image has loaded, and that is what crashed the app: the
 * player was started in the background (for example "resume when headphones reconnect") and the cover
 * finished loading afterwards. Here every later update goes through [onNotificationChangedCallback]
 * inside a try: when the system says no, the playback notification is shown as an ordinary notification
 * instead, and playback simply carries on.
 */
@OptIn(UnstableApi::class)
class SafeNotificationProvider(
    private val context: Context,
    private val delegate: MediaNotification.Provider = DefaultMediaNotificationProvider(context),
) : MediaNotification.Provider {

    override fun createNotification(
        mediaSession: MediaSession,
        customLayout: ImmutableList<CommandButton>,
        actionFactory: MediaNotification.ActionFactory,
        onNotificationChangedCallback: MediaNotification.Provider.Callback,
    ): MediaNotification =
        delegate.createNotification(mediaSession, customLayout, actionFactory) { notification ->
            try {
                onNotificationChangedCallback.onNotificationChanged(notification)
            } catch (e: IllegalStateException) {
                if (!isForegroundStartDenied(e)) throw e
                Log.w(TAG, "Foreground start not allowed right now; showing a plain notification", e)
                postPlain(notification)
            }
        }

    override fun handleCustomCommand(session: MediaSession, action: String, extras: Bundle): Boolean =
        delegate.handleCustomCommand(session, action, extras)

    // By name: the class only exists from Android 12, and a name check needs no API-level guard on the class itself.
    private fun isForegroundStartDenied(e: Exception): Boolean =
        Build.VERSION.SDK_INT >= 31 && e.javaClass.name == "android.app.ForegroundServiceStartNotAllowedException"

    // Notifications of a media session need no POST_NOTIFICATIONS permission.
    @SuppressLint("MissingPermission")
    private fun postPlain(notification: MediaNotification) {
        try {
            NotificationManagerCompat.from(context).notify(notification.notificationId, notification.notification)
        } catch (e: Exception) {
            Log.w(TAG, "Could not show the playback notification", e)
        }
    }

    private companion object {
        const val TAG = "PlaybackNotification"
    }
}
