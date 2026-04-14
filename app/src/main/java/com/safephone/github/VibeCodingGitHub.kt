package com.safephone.github

import android.net.Uri

/** Builds a GitHub "new issue" URL for feature requests from the app (no tokens). */
object VibeCodingGitHub {
    const val ISSUE_BODY_PREFIX = "Submitted from SafePhone (app):\n\n"

    private const val MAX_BODY_CHARS = 6000

    fun newIssueUri(owner: String, repo: String, userDescription: String): Uri? {
        val o = owner.trim()
        val r = repo.trim()
        if (o.isEmpty() || r.isEmpty()) return null
        var body = userDescription.trim()
        if (body.isEmpty()) return null
        body = ISSUE_BODY_PREFIX + body
        if (body.length > MAX_BODY_CHARS) {
            val headLen = MAX_BODY_CHARS - 120
            body = body.take(headLen.coerceAtLeast(ISSUE_BODY_PREFIX.length + 1)) +
                "\n\n… _(truncated for URL length; edit in GitHub if needed)_"
        }
        val title = "SafePhone feature request"
        return Uri.Builder()
            .scheme("https")
            .authority("github.com")
            .appendPath(o)
            .appendPath(r)
            .appendPath("issues")
            .appendPath("new")
            .appendQueryParameter("labels", "vibe-coding")
            .appendQueryParameter("title", title)
            .appendQueryParameter("body", body)
            .build()
    }
}
