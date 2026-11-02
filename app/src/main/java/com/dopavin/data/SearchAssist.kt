package com.dopavin.data
import java.text.Normalizer
import kotlin.math.abs
object SearchAssist {
    private val marks = Regex("\\p{Mn}+")
    private val nonWord = Regex("[^\\p{L}\\p{N}]+")
    fun norm(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(marks, "").lowercase().replace(nonWord, " ").trim()
    internal fun allowed(len: Int): Int = when {
        len < 4 -> 0
        len < 6 -> 1
        len < 10 -> 2
        else -> 3
    }
    internal fun distance(a: String, b: String, max: Int): Int {
        if (abs(a.length - b.length) > max) return max + 1
        var prev2 = IntArray(b.length + 1)
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1)
            cur[0] = i
            var rowMin = cur[0]
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                var v = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) v = minOf(v, prev2[j - 2] + 1)
                cur[j] = v
                if (v < rowMin) rowMin = v
            }
            if (rowMin > max) return max + 1
            prev2 = prev
            prev = cur
        }
        return prev[b.length]
    }
    fun didYouMean(query: String, vocabulary: Collection<String>): String? {
        val q = norm(query)
        if (q.length < 4) return null
        val entries = vocabulary.mapNotNull { v -> norm(v).takeIf { it.isNotEmpty() }?.let { it to v.trim() } }
        if (entries.any { (n, _) -> n.contains(q) }) return null
        val max = allowed(q.length)
        var best: String? = null
        var bestD = Int.MAX_VALUE
        for ((n, display) in entries) {
            val d = minOf(distance(q, n, max), distance(q, n.take(q.length), max))
            if (d in 1..max && d < bestD) { bestD = d; best = display }
        }
        if (best != null) return best
        val words = HashMap<String, String>()
        for ((n, display) in entries) {
            val shown = display.split(Regex("\\s+")).map { it.trim { c -> !c.isLetterOrDigit() } }
            val normed = n.split(' ')
            if (shown.size == normed.size) normed.forEachIndexed { i, w -> words.putIfAbsent(w, shown[i]) }
            else normed.forEach { words.putIfAbsent(it, it) }
        }
        var changed = false
        val out = q.split(' ').map { w ->
            if (w in words || words.keys.any { it.startsWith(w) } || allowed(w.length) == 0) return@map words[w] ?: w
            val m = allowed(w.length)
            var bw: String? = null
            var bd = Int.MAX_VALUE
            for (cand in words.keys) {
                val d = distance(w, cand, m)
                if (d in 1..m && d < bd) { bd = d; bw = cand }
            }
            if (bw != null) { changed = true; words.getValue(bw) } else w
        }
        if (!changed) return null
        val fixed = out.joinToString(" ")
        val fn = norm(fixed)
        return if (fn.split(' ').all { it in words }) fixed else null
    }
}
