package com.dopavin.data
import android.content.Context
import org.json.JSONObject
data class ArtistIdentity(val name: String, val channelUrl: String, val avatar: String?)
class ArtistDirectory(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("artist_directory", Context.MODE_PRIVATE)
    private val cache = HashMap<String, ArtistIdentity>()
    init {
        runCatching {
            val o = JSONObject(prefs.getString(KEY, "{}") ?: "{}")
            o.keys().forEach { key ->
                val e = o.optJSONObject(key) ?: return@forEach
                val url = e.optString("u")
                if (url.isNotBlank()) cache[key] = ArtistIdentity(e.optString("n"), url, e.optString("a").takeIf { it.isNotBlank() })
            }
        }
    }
    @Synchronized
    fun get(name: String): ArtistIdentity? = cache[keyOf(name)]
    @Synchronized
    fun put(name: String, identity: ArtistIdentity) {
        cache[keyOf(name)] = identity
        if (cache.size > MAX) cache.keys.take(cache.size - MAX).forEach { cache.remove(it) }
        val o = JSONObject()
        cache.forEach { (k, v) -> o.put(k, JSONObject().put("n", v.name).put("u", v.channelUrl).put("a", v.avatar.orEmpty())) }
        prefs.edit().putString(KEY, o.toString()).apply()
    }
    private fun keyOf(name: String) = name.trim().lowercase()
    private companion object {
        const val KEY = "map"
        const val MAX = 200
    }
}
