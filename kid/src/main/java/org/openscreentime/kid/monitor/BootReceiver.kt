package org.openscreentime.kid.monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import org.openscreentime.kid.data.PairingStore

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!PairingStore(context).isPaired) return
        ContextCompat.startForegroundService(context, Intent(context, ScreenMonitorService::class.java))
    }
}
