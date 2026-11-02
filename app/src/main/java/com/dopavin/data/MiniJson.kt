package com.dopavin.data
object MiniJson {
    fun parse(text: String): Any? = try {
        val p = Parser(text)
        p.skipWs()
        val v = p.value()
        p.skipWs()
        if (p.atEnd()) v else null
    } catch (e: IllegalStateException) {
        null
    } catch (e: IndexOutOfBoundsException) {
        null
    } catch (e: NumberFormatException) {
        null
    }
    @Suppress("UNCHECKED_CAST")
    fun obj(v: Any?): Map<String, Any?>? = v as? Map<String, Any?>
    fun list(v: Any?): List<Any?>? = v as? List<Any?>
    fun str(v: Any?): String? = v as? String
    private class Parser(private val s: String) {
        private var i = 0
        private var depth = 0
        fun atEnd() = i >= s.length
        fun skipWs() {
            while (i < s.length && s[i].isWhitespace()) i++
        }
        fun value(): Any? {
            skipWs()
            check(i < s.length)
            return when (val c = s[i]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> str()
                't' -> lit("true", true)
                'f' -> lit("false", false)
                'n' -> lit("null", null)
                else -> if (c == '-' || c.isDigit()) num() else error("bad char")
            }
        }
        private fun lit(word: String, v: Any?): Any? {
            check(s.startsWith(word, i))
            i += word.length
            return v
        }
        private fun num(): Double {
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] in "+-.eE")) i++
            return s.substring(start, i).toDouble()
        }
        private fun str(): String {
            check(s[i] == '"')
            i++
            val sb = StringBuilder()
            while (true) {
                check(i < s.length)
                val c = s[i++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        check(i < s.length)
                        when (val e = s[i++]) {
                            'n' -> sb.append('\n')
                            't' -> sb.append('\t')
                            'r' -> sb.append('\r')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            '/', '\\', '"' -> sb.append(e)
                            'u' -> {
                                check(i + 4 <= s.length)
                                sb.append(s.substring(i, i + 4).toInt(16).toChar())
                                i += 4
                            }
                            else -> error("bad escape")
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }
        private fun arr(): List<Any?> {
            check(++depth < MAX_DEPTH)
            i++
            val out = ArrayList<Any?>()
            skipWs()
            if (s[i] == ']') { i++; depth--; return out }
            while (true) {
                out += value()
                skipWs()
                when (s[i++]) {
                    ',' -> continue
                    ']' -> { depth--; return out }
                    else -> error("bad array")
                }
            }
        }
        private fun obj(): Map<String, Any?> {
            check(++depth < MAX_DEPTH)
            i++
            val out = LinkedHashMap<String, Any?>()
            skipWs()
            if (s[i] == '}') { i++; depth--; return out }
            while (true) {
                skipWs()
                val k = str()
                skipWs()
                check(s[i++] == ':')
                out[k] = value()
                skipWs()
                when (s[i++]) {
                    ',' -> continue
                    '}' -> { depth--; return out }
                    else -> error("bad object")
                }
            }
        }
    }
    private const val MAX_DEPTH = 64
}
