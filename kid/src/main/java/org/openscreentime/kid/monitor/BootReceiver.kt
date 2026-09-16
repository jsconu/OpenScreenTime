package org.openscreentime.kid.monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.core.content.ContextCompat
import org.openscreentime.kid.data.PairingStore

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!PairingStore(context).isPaired) return
        ContextCompat.startForegroundService(context, Intent(context, ScreenMonitorService::class.java))
        // VpnService.prepare() returns null once consent was already granted in an earlier
        // session - that grant persists across reboots, so this is safe to call unconditionally
        // and just does nothing if the kid has never granted it.
        if (VpnService.prepare(context) == null) {
            ContextCompat.startForegroundService(context, Intent(context, DnsSinkholeVpnService::class.java))
        }
    }
}
