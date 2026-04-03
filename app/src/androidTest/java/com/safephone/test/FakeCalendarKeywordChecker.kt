package com.safephone.test

import com.safephone.data.CalendarKeywordEntity
import com.safephone.service.CalendarKeywordChecker

class FakeCalendarKeywordChecker(
    var focusKeywordsActive: Boolean = false,
) : CalendarKeywordChecker {
    override fun isFocusKeywordActiveNow(keywords: List<CalendarKeywordEntity>): Boolean =
        focusKeywordsActive && keywords.isNotEmpty()
}
