package org.openscreentime.parent.monitor

import org.openscreentime.parent.ui.FocusLauncherActivity
import org.openscreentime.shared.util.DeviceProfileState

/**
 * The parent's own phone's live view of their self profile (see #8) - limits, lock, bedtime, blocked sites, tracking
 * choices. Kept up to date from Firestore by [org.openscreentime.parent.ParentApp] and saved on the phone, so limits
 * still apply after a restart before Firestore has answered. See [DeviceProfileState].
 */
object SelfDeviceState : DeviceProfileState("self_device_state", { FocusLauncherActivity.focusMode(it) })
