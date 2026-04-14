package com.safephone.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.safephone.data.FocusPreferences
import com.safephone.update.InternalUpdateScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope.launch(Dispatchers.IO) {
            val appCtx = context.applicationContext
            FocusEnforcementService.start(appCtx)
            InternalUpdateScheduler.scheduleIfNeeded(appCtx)
        }
    }
}
