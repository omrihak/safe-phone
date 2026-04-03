package com.safephone.service

import com.safephone.data.CalendarKeywordEntity

/** Abstraction for [CalendarWatcher] to enable fakes in tests. */
fun interface CalendarKeywordChecker {
    fun isFocusKeywordActiveNow(keywords: List<CalendarKeywordEntity>): Boolean
}
