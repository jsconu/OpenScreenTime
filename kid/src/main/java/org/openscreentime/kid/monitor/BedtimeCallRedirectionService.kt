package org.openscreentime.kid.monitor

import android.net.Uri
import android.telecom.CallRedirectionService
import android.telecom.PhoneAccountHandle
import org.openscreentime.shared.model.isCallAllowedDuringBedtime
import org.openscreentime.shared.model.nowMinutesOfDay

/**
 * See #34 - the outgoing-call half of [BedtimeCallScreeningService]: cancels a call the
 * kid places during bedtime unless the dialed number is in
 * [LiveChildState.alwaysAllowedContacts]. Only active once a parent requests the
 * ROLE_CALL_REDIRECTION role (Android 10+).
 */
class BedtimeCallRedirectionService : CallRedirectionService() {
    override fun onPlaceCall(handle: Uri, initialPhoneAccount: PhoneAccountHandle, allowInteractiveResponse: Boolean) {
        val allowed = isCallAllowedDuringBedtime(
            phoneNumber = handle.schemeSpecificPart,
            nowMinutesOfDay = nowMinutesOfDay(),
            bedtimeStartMinutes = LiveChildState.bedtimeStartMinutes,
            bedtimeEndMinutes = LiveChildState.bedtimeEndMinutes,
            alwaysAllowedContacts = LiveChildState.alwaysAllowedContacts
        )
        if (allowed) placeCallUnmodified() else cancelCall()
    }
}
