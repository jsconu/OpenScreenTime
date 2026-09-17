package org.openscreentime.kid.monitor

import android.telecom.Call
import android.telecom.CallScreeningService
import android.telecom.CallScreeningService.CallResponse
import org.openscreentime.shared.model.isCallAllowedDuringBedtime
import org.openscreentime.shared.model.nowMinutesOfDay

/**
 * See #34 - screens incoming calls during bedtime, rejecting any number that isn't in
 * [LiveChildState.alwaysAllowedContacts]. Only active once a parent requests the
 * ROLE_CALL_SCREENING role (Android 10+) from the kid device's Parent controls screen -
 * before that, this service is registered but never invoked, and every call rings through
 * as normal. Outside a bedtime window, [isCallAllowedDuringBedtime] always allows the call.
 */
class BedtimeCallScreeningService : CallScreeningService() {
    override fun onScreenCall(callDetails: Call.Details) {
        val number = callDetails.handle?.schemeSpecificPart
        val allowed = isCallAllowedDuringBedtime(
            phoneNumber = number,
            nowMinutesOfDay = nowMinutesOfDay(),
            bedtimeStartMinutes = LiveChildState.bedtimeStartMinutes,
            bedtimeEndMinutes = LiveChildState.bedtimeEndMinutes,
            alwaysAllowedContacts = LiveChildState.alwaysAllowedContacts
        )
        val response = if (allowed) {
            CallResponse.Builder().build()
        } else {
            CallResponse.Builder()
                .setDisallowCall(true)
                .setRejectCall(true)
                .setSkipNotification(true)
                .build()
        }
        respondToCall(callDetails, response)
    }
}
