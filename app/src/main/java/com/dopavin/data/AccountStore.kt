package com.dopavin.data

import android.content.Context

/** What the Accounts page shows; never contains a token, password or cookie. */
data class AccountsUi(
    val scrobbling: Boolean = false,
    val listenBrainzConnected: Boolean = false,
    val listenBrainzUser: String = "",
    val pendingListens: Int = 0,
    val subsonicConfigured: Boolean = false,
    val subsonicHost: String = "",
    val subsonicUser: String = "",
    val youTubeSignedIn: Boolean = false,
    /** A connection test or an import is running. */
    val busy: Boolean = false,
)

/**
 * F-217: the owner's accounts and servers (ListenBrainz token, Subsonic server, YouTube session cookies).
 *
 * Kept in its own preferences file on purpose: [SettingsStore.exportPrefs] copies the "settings" file into
 * backup files, and secrets must never end up in a file the owner shares. The app has `allowBackup="false"`,
 * so this file also never leaves the phone through system backup. Plain private preferences (no Keystore
 * encryption: that needs another dependency); nothing here is sent anywhere except to the service it belongs to.
 */
class AccountStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("accounts", Context.MODE_PRIVATE)

    var scrobbling: Boolean
        get() = prefs.getBoolean("scrobbling", false)
        set(v) = prefs.edit().putBoolean("scrobbling", v).apply()

    var listenBrainzToken: String
        get() = prefs.getString("lb_token", "").orEmpty()
        set(v) = prefs.edit().putString("lb_token", v.trim()).apply()

    var listenBrainzUser: String
        get() = prefs.getString("lb_user", "").orEmpty()
        set(v) = prefs.edit().putString("lb_user", v).apply()

    var subsonicUrl: String
        get() = prefs.getString("sub_url", "").orEmpty()
        set(v) = prefs.edit().putString("sub_url", v.trim()).apply()

    var subsonicUser: String
        get() = prefs.getString("sub_user", "").orEmpty()
        set(v) = prefs.edit().putString("sub_user", v.trim()).apply()

    var subsonicPassword: String
        get() = prefs.getString("sub_pass", "").orEmpty()
        set(v) = prefs.edit().putString("sub_pass", v).apply()

    /** The "name=value; name=value" cookie header of the signed-in YouTube Music session; empty = signed out. */
    var youTubeCookie: String
        get() = prefs.getString("yt_cookie", "").orEmpty()
        set(v) = prefs.edit().putString("yt_cookie", v).apply()

    var youTubeName: String
        get() = prefs.getString("yt_name", "").orEmpty()
        set(v) = prefs.edit().putString("yt_name", v).apply()

    /** Scrobbles that could not be sent yet (one listen JSON per line), oldest first. */
    var pendingListens: List<String>
        get() = prefs.getString("lb_pending", "").orEmpty().lines().filter { it.isNotBlank() }
        set(v) = prefs.edit().putString("lb_pending", v.takeLast(MAX_PENDING).joinToString("\n")).apply()

    val scrobblingReady: Boolean get() = scrobbling && listenBrainzToken.isNotBlank()
    val subsonicConfigured: Boolean get() = subsonicUrl.isNotBlank() && subsonicUser.isNotBlank() && subsonicPassword.isNotEmpty()
    val youTubeSignedIn: Boolean get() = youTubeCookie.isNotBlank()

    fun snapshot(busy: Boolean = false) = AccountsUi(
        scrobbling = scrobbling,
        listenBrainzConnected = listenBrainzToken.isNotBlank(),
        listenBrainzUser = listenBrainzUser,
        pendingListens = pendingListens.size,
        subsonicConfigured = subsonicConfigured,
        subsonicHost = subsonicUrl.removePrefix("https://").removePrefix("http://").trimEnd('/'),
        subsonicUser = subsonicUser,
        youTubeSignedIn = youTubeSignedIn,
        busy = busy,
    )

    fun signOutYouTube() {
        prefs.edit().remove("yt_cookie").remove("yt_name").apply()
    }

    fun clearSubsonic() {
        prefs.edit().remove("sub_url").remove("sub_user").remove("sub_pass").apply()
    }

    fun clearListenBrainz() {
        prefs.edit().remove("lb_token").remove("lb_user").remove("lb_pending").putBoolean("scrobbling", false).apply()
    }

    private companion object {
        const val MAX_PENDING = 200
    }
}
