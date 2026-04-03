package com.safephone.service

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.safephone.data.CalendarKeywordEntity
import java.util.Calendar
import java.util.TimeZone

class CalendarWatcher(private val context: Context) : CalendarKeywordChecker {

    override fun isFocusKeywordActiveNow(keywords: List<CalendarKeywordEntity>): Boolean {
        if (keywords.isEmpty()) return false
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            return false
        }
        val now = System.currentTimeMillis()
        val projection = arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
        )
        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, now - 60_000L)
        ContentUris.appendId(builder, now + 60_000L)
        val uri = builder.build()
        val kw = keywords.map { it.keyword.lowercase() }
        context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
            val titleIdx = c.getColumnIndex(CalendarContract.Instances.TITLE)
            val beginIdx = c.getColumnIndex(CalendarContract.Instances.BEGIN)
            val endIdx = c.getColumnIndex(CalendarContract.Instances.END)
            while (c.moveToNext()) {
                val title = if (titleIdx >= 0) c.getString(titleIdx) ?: "" else ""
                val begin = if (beginIdx >= 0) c.getLong(beginIdx) else 0L
                val end = if (endIdx >= 0) c.getLong(endIdx) else 0L
                if (now in begin..end) {
                    val t = title.lowercase()
                    if (kw.any { t.contains(it) }) return true
                }
            }
        }
        return false
    }
}
