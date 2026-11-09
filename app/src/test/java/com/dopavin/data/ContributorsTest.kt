package com.dopavin.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContributorsTest {

    @Test fun aValidLoginGivesItsGithubProfile() {
        assertEquals("https://github.com/playboy-gg", Contributors.profileUrl("playboy-gg"))
        assertEquals("https://github.com/a", Contributors.profileUrl(" a "))
        assertEquals("https://github.com/User123", Contributors.profileUrl("User123"))
    }

    @Test fun anythingThatIsNotAGithubLoginIsNeverOpened() {
        assertNull(Contributors.profileUrl(""))
        assertNull(Contributors.profileUrl("-bad"))
        assertNull(Contributors.profileUrl("bad-"))
        assertNull(Contributors.profileUrl("two--hyphens"))
        assertNull(Contributors.profileUrl("has space"))
        assertNull(Contributors.profileUrl("evil/../path"))
        assertNull(Contributors.profileUrl("x?y=1"))
        assertNull(Contributors.profileUrl("a".repeat(40)))
        assertEquals("https://github.com/" + "a".repeat(39), Contributors.profileUrl("a".repeat(39)))
    }

    @Test fun everyListedContributorHasAWorkingProfileLink() {
        assertTrue(Contributors.people.isNotEmpty())
        Contributors.people.forEach { assertTrue(it.githubLogin, Contributors.profileUrl(it.githubLogin) != null) }
        assertEquals(Contributors.people.size, Contributors.people.map { it.githubLogin.lowercase() }.toSet().size)
    }

    @Test fun theProjectLinksPointAtTheRepository() {
        assertTrue(Contributors.ALL_CONTRIBUTORS_URL.startsWith(Contributors.REPO_URL))
        assertTrue(Contributors.REPO_URL.startsWith("https://github.com/"))
    }

    @Test fun theProfilePictureComesFromTheSameLogin() {
        assertEquals("https://github.com/Sandeepbedia.png?size=160", Contributors.avatarUrl("Sandeepbedia", 160))
        assertEquals("https://github.com/playboy-gg.png?size=460", Contributors.avatarUrl("playboy-gg", 5000))
        assertEquals("https://github.com/playboy-gg.png?size=32", Contributors.avatarUrl("playboy-gg", 1))
        assertNull(Contributors.avatarUrl("not a login", 160))
        assertNull(Contributors.avatarUrl("../x", 160))
    }

    @Test fun theDesignerIsListedWithTheDesignerRole() {
        val designer = Contributors.people.first { it.githubLogin == "Sandeepbedia" }
        assertEquals(ContributorRole.Designer, designer.role)
        assertEquals("https://github.com/Sandeepbedia", Contributors.profileUrl(designer.githubLogin))
    }

    @Test fun theMaintainerComesFirst() {
        assertEquals(ContributorRole.Maintainer, Contributors.people.first().role)
    }
}
