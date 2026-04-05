package com.safephone

import java.util.Locale

object BreakTimeFormatter {
    /** Formats remaining milliseconds as `m:ss` (e.g. 9:02, 0:45). */
    fun formatMmSs(remainingMs: Long): String {
        val totalSec = (remainingMs / 1000L).coerceAtLeast(0L)
        val m = totalSec / 60L
        val s = totalSec % 60L
        return String.format(Locale.getDefault(), "%d:%02d", m, s)
    }
}
