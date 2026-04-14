package com.safephone.data

/**
 * Predefined "Social Media" category: a curated list of popular social-media domains and Android
 * package names that can be blocked in bulk with a single toggle.
 */
object SocialMediaCategory {
    val domains: List<String> = listOf(
        "facebook.com",
        "instagram.com",
        "twitter.com",
        "x.com",
        "tiktok.com",
        "youtube.com",
        "snapchat.com",
        "reddit.com",
        "linkedin.com",
        "pinterest.com",
        "tumblr.com",
        "threads.net",
        "bereal.com",
        "twitch.tv",
        "discord.com",
    )

    val packages: List<String> = listOf(
        "com.facebook.katana",          // Facebook
        "com.instagram.android",        // Instagram
        "com.twitter.android",          // Twitter / X (legacy package)
        "com.X.android",                // Twitter / X (new package)
        "com.zhiliaoapp.musically",     // TikTok
        "com.ss.android.ugc.trill",     // TikTok (some regions)
        "com.google.android.youtube",   // YouTube
        "com.snapchat.android",         // Snapchat
        "com.reddit.frontpage",         // Reddit
        "com.linkedin.android",         // LinkedIn
        "com.pinterest",                // Pinterest
        "com.tumblr",                   // Tumblr
        "com.instagram.barcelona",      // Threads (Instagram)
        "tv.twitch.android.app",        // Twitch
        "com.discord",                  // Discord
        "vu.co.bereal",                 // BeReal
    )
}
