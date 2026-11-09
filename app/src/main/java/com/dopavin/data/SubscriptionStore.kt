package com.dopavin.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** One subscribed YouTube channel (artist). Stored only on this device. */
data class Subscription(val url: String, val name: String, val avatar: String?)

/** The merged latest-uploads feed of all subscriptions, as last fetched. */
data class SubscriptionFeed(val savedAt: Long, val songs: List<Song>)

/**
 * Local subscriptions (F-129): the channel list and the last merged feed, as small JSON strings in
 * SharedPreferences. No account, no sync; only display metadata is kept.
 */
class SubscriptionStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("subscriptions", Context.MODE_PRIVATE)

    fun channels(): List<Subscription> = runCatching {
        val arr = JSONArray(prefs.getString(KEY_CHANNELS, "[]"))
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val url = o.optString("u").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            Subscription(url, o.optString("n").ifBlank { url }, o.optString("a").takeIf { it.isNotBlank() })
        }
    }.getOrDefault(emptyList())

    fun saveChannels(list: List<Subscription>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("u", it.url).put("n", it.name).put("a", it.avatar.orEmpty()))
        }
        prefs.edit().putString(KEY_CHANNELS, arr.toString()).apply()
    }

    fun feed(): SubscriptionFeed? = runCatching {
        val o = JSONObject(prefs.getString(KEY_FEED, null) ?: return null)
        SubscriptionFeed(o.optLong("at"), songsFromJson(o.optJSONArray("songs")))
    }.getOrNull()

    fun saveFeed(feed: SubscriptionFeed) {
        val o = JSONObject().put("at", feed.savedAt).put("songs", songsToJson(feed.songs))
        prefs.edit().putString(KEY_FEED, o.toString()).apply()
    }

    private companion object {
        const val KEY_CHANNELS = "channels"
        const val KEY_FEED = "feed"
    }
}
