package com.dopavin.data

/**
 * Where the listener is and which languages their phone speaks, read from the phone alone. The app never asks.
 * [country] is an upper-case ISO code ("" when unknown); [languages] keeps the order of the phone's list.
 */
data class RegionProfile(val country: String, val languages: List<String>) {
    /** Changes when the country or the phone's languages change; used to drop a Home cache made for another place. */
    val key: String get() = country + ":" + languages.joinToString(",")
}

/** The six kinds of Home tiles. [Regional] is any other language and is drawn with a generic scene. */
enum class CategoryKind { Charts, Punjabi, Bollywood, Romantic, Lofi, English, Regional }

/** One Home tile: what it is, the search it opens, and (for [CategoryKind.Regional]) the language it is for. */
data class HomeCategorySpec(val kind: CategoryKind, val query: String, val languageCode: String? = null)

object RegionProfiles {

    /**
     * Country: the SIM first (the listener's home network, steady while travelling), then the network they
     * are on, then the phone's region. Languages: the phone's preferred languages, in their order.
     */
    fun build(
        simCountry: String?,
        networkCountry: String?,
        localeCountry: String?,
        phoneLanguages: List<String>,
    ): RegionProfile {
        val country = listOf(simCountry, networkCountry, localeCountry).firstNotNullOfOrNull { cleanCountry(it) }.orEmpty()
        val languages = phoneLanguages.mapNotNull { normaliseLanguage(it) }.distinct()
        return RegionProfile(country, languages)
    }

    fun cleanCountry(code: String?): String? {
        val c = code?.trim()?.uppercase() ?: return null
        return c.takeIf { it.length == 2 && it.all { ch -> ch in 'A'..'Z' } }
    }

    /** "pa-Guru-IN" -> "pa", old codes -> current ones ("iw" -> "he", "in" -> "id"). */
    fun normaliseLanguage(code: String?): String? {
        val c = code?.trim()?.lowercase()?.substringBefore('-')?.substringBefore('_') ?: return null
        val fixed = when (c) {
            "iw" -> "he"
            "in" -> "id"
            "ji" -> "yi"
            "jw" -> "jv"
            else -> c
        }
        return fixed.takeIf { it.length in 2..3 && it.all { ch -> ch in 'a'..'z' } }
    }

    /**
     * What a country usually listens to, used only to fill in what the phone's language list does not say
     * (an English phone in India still gets Hindi and Punjabi tiles, as before).
     */
    fun countryLanguages(country: String): List<String> = when (country) {
        "IN" -> listOf("pa", "hi", "en")
        "PK" -> listOf("ur", "pa", "en")
        "BD" -> listOf("bn", "en")
        "LK" -> listOf("si", "ta", "en")
        "NP" -> listOf("ne", "hi", "en")
        "US", "GB", "CA", "AU", "NZ", "IE", "ZA", "SG" -> listOf("en")
        "AE", "SA", "QA", "KW", "OM", "BH" -> listOf("ar", "en")
        "EG", "MA", "DZ", "TN", "JO", "LB", "IQ" -> listOf("ar")
        "DE", "AT", "CH" -> listOf("de", "en")
        "FR", "BE" -> listOf("fr", "en")
        "ES", "MX", "AR", "CO", "CL", "PE" -> listOf("es", "en")
        "BR", "PT" -> listOf("pt", "en")
        "IT" -> listOf("it", "en")
        "NL" -> listOf("nl", "en")
        "PL" -> listOf("pl", "en")
        "TR" -> listOf("tr", "en")
        "RU", "UA", "BY", "KZ" -> listOf("ru", "en")
        "JP" -> listOf("ja", "en")
        "KR" -> listOf("ko", "en")
        "CN", "TW", "HK" -> listOf("zh", "en")
        "ID" -> listOf("id", "en")
        "MY" -> listOf("ms", "en")
        "PH" -> listOf("tl", "en")
        "TH" -> listOf("th", "en")
        "VN" -> listOf("vi", "en")
        "IR" -> listOf("fa", "en")
        "IL" -> listOf("he", "en")
        else -> emptyList()
    }
}

/** Language names as the music search understands them ("latest tamil songs"). */
object LanguageNames {
    private val names = mapOf(
        "ta" to "tamil", "te" to "telugu", "ml" to "malayalam", "kn" to "kannada", "bn" to "bengali",
        "mr" to "marathi", "gu" to "gujarati", "ur" to "urdu", "ne" to "nepali", "si" to "sinhala",
        "ar" to "arabic", "es" to "spanish", "pt" to "portuguese", "fr" to "french", "de" to "german",
        "it" to "italian", "tr" to "turkish", "ru" to "russian", "ja" to "japanese", "ko" to "korean",
        "zh" to "chinese", "th" to "thai", "vi" to "vietnamese", "id" to "indonesian", "ms" to "malay",
        "tl" to "filipino", "nl" to "dutch", "pl" to "polish", "fa" to "persian", "he" to "hebrew",
        "sv" to "swedish",
    )

    fun searchName(code: String): String? = names[code]
}

/**
 * Which tiles Home shows. The set is no longer the same for everybody: it follows the phone (country and
 * languages) and what the listener actually plays ([learned], most played first). Always starts with the
 * charts of the listener's country and ends with a neutral one.
 */
object HomeCategories {
    const val MAX = 6

    val CHARTS = HomeCategorySpec(CategoryKind.Charts, "top songs this week")
    val PUNJABI = HomeCategorySpec(CategoryKind.Punjabi, "new punjabi songs", "pa")
    val BOLLYWOOD = HomeCategorySpec(CategoryKind.Bollywood, "latest bollywood songs", "hi")
    val ROMANTIC = HomeCategorySpec(CategoryKind.Romantic, "romantic hindi songs", "hi")
    val LOFI = HomeCategorySpec(CategoryKind.Lofi, "lofi chill songs")
    val ENGLISH = HomeCategorySpec(CategoryKind.English, "top english hits", "en")

    fun plan(profile: RegionProfile, learned: List<String>): List<HomeCategorySpec> {
        val out = ArrayList<HomeCategorySpec>()
        fun add(spec: HomeCategorySpec) {
            if (out.size < MAX - 1 && out.none { it.query == spec.query }) out += spec
        }
        add(CHARTS)
        // English on a phone says little about taste, so it never counts as an explicit language. When the
        // phone or the listening names another language, that language leads and the country's usual
        // languages are not added on top (a Tamil phone is not given Punjabi tiles). With nothing explicit,
        // the country's usual languages fill in, so an English phone in India keeps its Hindi and Punjabi tiles.
        val explicit = (learned + profile.languages).filter { it != "en" }.distinct()
        val languages = LinkedHashSet<String>()
        if (explicit.isNotEmpty()) {
            languages += explicit
            languages += "en"
        } else {
            languages += RegionProfiles.countryLanguages(profile.country)
            if (languages.isEmpty() && "en" in profile.languages) languages += "en"
        }
        for (language in languages) {
            when (language) {
                "hi" -> { add(BOLLYWOOD); add(ROMANTIC) }
                "pa" -> add(PUNJABI)
                "en" -> add(ENGLISH)
                else -> LanguageNames.searchName(language)?.let {
                    add(HomeCategorySpec(CategoryKind.Regional, "latest $it songs", language))
                }
            }
        }
        // The neutral tile always closes the list, even when the other slots are full.
        if (out.none { it.query == LOFI.query }) out += LOFI
        return out
    }

    /** What Home shows before anything is known: the same six tiles as before, for an Indian listener. */
    val DEFAULT: List<HomeCategorySpec> = plan(RegionProfile("IN", emptyList()), emptyList())
}

/**
 * Reads the language a listener plays from the script of the song names ("तुम ही हो", "ਮੈਨੂੰ", "காதல்").
 * Names written in Latin letters say nothing here; for those the phone's languages and the personal
 * shelves (radios, "more from your artists") do the work.
 */
object ScriptLanguages {

    /** The language a text is written in, by its dominant non-Latin script; null for Latin or mixed-away text. */
    fun detect(text: String): String? {
        val counts = HashMap<String, Int>()
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            i += Character.charCount(cp)
            val language = when (Character.UnicodeScript.of(cp)) {
                Character.UnicodeScript.DEVANAGARI -> "hi"
                Character.UnicodeScript.GURMUKHI -> "pa"
                Character.UnicodeScript.TAMIL -> "ta"
                Character.UnicodeScript.TELUGU -> "te"
                Character.UnicodeScript.MALAYALAM -> "ml"
                Character.UnicodeScript.KANNADA -> "kn"
                Character.UnicodeScript.BENGALI -> "bn"
                Character.UnicodeScript.GUJARATI -> "gu"
                Character.UnicodeScript.ARABIC -> "ar"
                Character.UnicodeScript.HANGUL -> "ko"
                Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA -> "ja"
                Character.UnicodeScript.THAI -> "th"
                Character.UnicodeScript.CYRILLIC -> "ru"
                else -> null
            } ?: continue
            counts[language] = (counts[language] ?: 0) + 1
        }
        return counts.maxByOrNull { it.value }?.key
    }

    /** Languages seen in at least [minSongs] of the given song names, most frequent first. */
    fun learn(songNames: List<String>, minSongs: Int = 3): List<String> {
        val perLanguage = HashMap<String, Int>()
        for (name in songNames) detect(name)?.let { perLanguage[it] = (perLanguage[it] ?: 0) + 1 }
        return perLanguage.filter { it.value >= minSongs }.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { it.key }
    }
}
