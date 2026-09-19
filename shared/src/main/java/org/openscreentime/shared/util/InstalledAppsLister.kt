package org.openscreentime.shared.util

import android.content.Context
import android.content.Intent
import org.openscreentime.shared.model.InstalledApp

/**
 * Every app with a launcher icon on this device, except this app itself. Uses a <queries> entry for
 * the launcher intent (declared in each app's manifest) instead of the broad QUERY_ALL_PACKAGES
 * permission, which Google Play restricts. Returns an empty list if the query fails.
 */
fun listLaunchableApps(context: Context): List<InstalledApp> = try {
    val pm = context.packageManager
    val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    pm.queryIntentActivities(launcher, 0)
        .map { InstalledApp(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
        .filter { it.packageName != context.packageName }
        .distinctBy { it.packageName }
} catch (_: Exception) {
    emptyList()
}
