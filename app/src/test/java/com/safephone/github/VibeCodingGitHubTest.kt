package com.safephone.github

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class VibeCodingGitHubTest {

    @Test
    fun newIssueUri_null_when_owner_blank() {
        assertNull(VibeCodingGitHub.newIssueUri("", "repo", "text"))
    }

    @Test
    fun newIssueUri_null_when_description_blank() {
        assertNull(VibeCodingGitHub.newIssueUri("o", "r", "   "))
    }

    @Test
    fun newIssueUri_contains_label_title_and_body_prefix() {
        val uri = VibeCodingGitHub.newIssueUri("myOrg", "safe-phone", "Add dark mode")
        assertNotNull(uri)
        assertEquals("https", uri!!.scheme)
        assertEquals("github.com", uri.authority)
        assertEquals("/myOrg/safe-phone/issues/new", uri.path)
        assertEquals("vibe-coding", uri.getQueryParameter("labels"))
        assertEquals("SafePhone feature request", uri.getQueryParameter("title"))
        val body = uri.getQueryParameter("body")
        assertNotNull(body)
        assertEquals(true, body!!.startsWith(VibeCodingGitHub.ISSUE_BODY_PREFIX))
        assertEquals(true, body.contains("Add dark mode"))
    }
}
