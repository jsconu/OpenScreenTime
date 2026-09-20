package org.openscreentime.kid.monitor

import org.openscreentime.kid.ui.FocusLauncherActivity
import org.openscreentime.shared.util.DeviceProfileState

/**
 * The kid device's live view of the paired child's Firestore-synced settings - limits, lock state, bedtime window,
 * blocked domains and the rest. Written from one place (KidApp.startLimitsListener()'s Firestore callback) and read by
 * [AppLimitAccessibilityService], [DnsSinkholeVpnService], call screening and sync, so "what does this device
 * currently know about the child" has one name. Everything it does lives in [DeviceProfileState], shared with the
 * parent's own-phone tracking; this only names this phone's saved copy and its Focus home screen.
 */
object LiveChildState : DeviceProfileState("live_child_state", { FocusLauncherActivity.focusMode(it) })
