package com.dopavin.data

enum class ContributorRole { Maintainer, Designer, Contributor }

/** One person behind Dopavin. [githubLogin] is the name exactly as it appears in github.com/<login>. */
data class Contributor(val name: String, val githubLogin: String, val role: ContributorRole)

/**
 * The people shown in Settings > Contributors, each with a link to their GitHub profile.
 * To add somebody, add one line to [people]. The "All contributors on GitHub" row always lists everybody
 * GitHub knows about, so nobody is missed while this list catches up.
 */
object Contributors {
    const val REPO_URL = "https://github.com/playboy-gg/Dopavin"
    const val ALL_CONTRIBUTORS_URL = "$REPO_URL/graphs/contributors"

    val people: List<Contributor> = listOf(
        Contributor(name = "playboy-gg", githubLogin = "playboy-gg", role = ContributorRole.Maintainer),
        // Shown by login; put the real name in [name] if you want it shown instead.
        Contributor(name = "Sandeepbedia", githubLogin = "Sandeepbedia", role = ContributorRole.Designer),
    )

    // GitHub logins: letters, digits and single hyphens, never starting or ending with one, at most 39 long.
    private val login = Regex("^[A-Za-z0-9](?:[A-Za-z0-9]|-(?=[A-Za-z0-9])){0,38}$")

    /** The public profile picture of [githubLogin] at about [sizePx] pixels, or null for an invalid login. */
    fun avatarUrl(githubLogin: String, sizePx: Int): String? =
        profileUrl(githubLogin)?.let { "$it.png?size=${sizePx.coerceIn(32, 460)}" }

    /** The profile address, or null for anything that is not a valid GitHub login (it is never opened then). */
    fun profileUrl(githubLogin: String): String? {
        val clean = githubLogin.trim()
        return if (login.matches(clean)) "https://github.com/$clean" else null
    }
}
