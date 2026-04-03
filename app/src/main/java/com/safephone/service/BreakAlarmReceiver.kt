package com.safephone.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.safephone.data.FocusPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.time.LocalDate

class BreakAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != BreakManager.ACTION_END_BREAK) return
        val prefs = FocusPreferences(context.applicationContext)
        runBlocking {
            prefs.clearBreakTimer()
            prefs.setBreakState(
                endEpochMs = null,
                breaksUsed = prefs.breaksUsedToday.first(),
                dayEpochDay = LocalDate.now().toEpochDay(),
                lastBreakEnd = System.currentTimeMillis(),
            )
        }
    }
}
