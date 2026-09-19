package org.openscreentime.kid.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.openscreentime.kid.KidApp
import org.openscreentime.kid.data.PairingStore
import org.openscreentime.kid.monitor.LiveChildState
import org.openscreentime.shared.model.BlockReason
import org.openscreentime.shared.model.blockScreenCopy
import org.openscreentime.shared.model.randomAlternativeActivity
import org.openscreentime.shared.util.PasscodeAttemptStore
import org.openscreentime.shared.util.PasscodeHasher
import org.openscreentime.sharedui.ParentUnlockDialog

/** Full-screen interruption shown when a daily or per-app limit is reached. */
class BlockOverlayActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val reason = BlockReason.fromWireValue(intent.getStringExtra(EXTRA_REASON))
        val copy = blockScreenCopy(
            reason = reason,
            bedtimeEndMinutes = LiveChildState.bedtimeEndMinutes,
            lockMessage = "A parent has paused screen time. Ask them to resume it.",
            defaultMessage = "Ask a parent if you need more time."
        )
        val repository = (application as KidApp).repository
        val pairingStore = PairingStore(this)
        val parentUid = pairingStore.parentUid
        val childId = pairingStore.childId

        setContent {
            OpenScreenTimeTheme {
                val scope = rememberCoroutineScope()
                var requestedExtraMinutes by remember { mutableStateOf<Int?>(null) }
                var showParentUnlock by remember { mutableStateOf(false) }
                var unlockError by remember { mutableStateOf<String?>(null) }
                var verifying by remember { mutableStateOf(false) }

                // The system back gesture/button used to just finish this activity, which
                // (since it's the root of its own task - see the FLAG_ACTIVITY_NEW_TASK
                // launch below) revealed whatever was underneath: the blocked app itself,
                // fully interactive again. Swallowing it here means the only way off this
                // screen is the explicit "OK" button, which - unlike the old back-gesture
                // path - always takes them to the home screen, never back into the app
                // that got them blocked.
                BackHandler {}

                // See #23: a parent granting the request lands here live - dismiss the block
                // screen right away instead of leaving the kid staring at a screen they've
                // already been given more time for.
                DisposableEffect(parentUid, childId) {
                    if (parentUid == null || childId == null) return@DisposableEffect onDispose {}
                    val reg = repository.listenChild(parentUid, childId) { child ->
                        requestedExtraMinutes = child.requestedExtraMinutes
                        val until = child.temporaryUnlockUntilMs
                        if (until != null && until > System.currentTimeMillis()) {
                            finish()
                        }
                    }
                    onDispose { reg.remove() }
                }

                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            copy.title,
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            copy.message,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        // Winding down for bedtime is the point there, not finding something
                        // else active to do - see #16's design-principle note.
                        if (reason != BlockReason.BEDTIME) {
                            Spacer(Modifier.height(20.dp))
                            val suggestion = remember { randomAlternativeActivity() }
                            Text(
                                "In the meantime: $suggestion",
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        // A parent lock is a deliberate, direct action - not something a
                        // time-limit exception should be negotiable against (see #23).
                        if (reason != BlockReason.PARENT_LOCK && parentUid != null && childId != null) {
                            Spacer(Modifier.height(24.dp))
                            RequestMoreTimeSection(
                                requestedExtraMinutes = requestedExtraMinutes,
                                onRequest = { minutes ->
                                    requestedExtraMinutes = minutes
                                    scope.launch { repository.requestExtraTime(parentUid, childId, minutes) }
                                }
                            )
                        }
                        // A parent standing next to the phone can lift a "Lock now" with the family
                        // passcode, without reaching for their own phone. Only offered once a
                        // passcode exists on this device to check against.
                        if (reason == BlockReason.PARENT_LOCK && parentUid != null && childId != null &&
                            LiveChildState.parentPasscodeHash != null
                        ) {
                            Spacer(Modifier.height(24.dp))
                            OutlinedButton(
                                onClick = { unlockError = null; showParentUnlock = true },
                                modifier = Modifier.testTag("block_parent_unlock")
                            ) { Text("Parent unlock") }
                        }
                        Spacer(Modifier.height(32.dp))
                        Button(onClick = {
                            startActivity(
                                Intent(Intent.ACTION_MAIN).apply {
                                    addCategory(Intent.CATEGORY_HOME)
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                            )
                            finish()
                        }) {
                            Text("OK")
                        }
                    }
                }

                if (showParentUnlock && parentUid != null && childId != null) {
                    ParentUnlockDialog(
                        verifying = verifying,
                        error = unlockError,
                        onDismiss = { showParentUnlock = false },
                        onSubmit = { pin ->
                            val attempts = PasscodeAttemptStore(this@BlockOverlayActivity)
                            val hash = LiveChildState.parentPasscodeHash
                            val salt = LiveChildState.parentPasscodeSalt
                            if (attempts.isLocked()) {
                                unlockError = "Too many incorrect attempts. Try again in ${attempts.minutesRemaining()} minutes."
                            } else if (hash == null || salt == null) {
                                unlockError = "No family passcode is set yet."
                            } else {
                                verifying = true
                                scope.launch {
                                    val ok = withContext(Dispatchers.Default) { PasscodeHasher.verify(pin, salt, hash) }
                                    verifying = false
                                    if (ok) {
                                        attempts.recordSuccess()
                                        LiveChildState.clearLock(this@BlockOverlayActivity)
                                        // Best effort: if this can't reach Firestore right now the
                                        // lock is already lifted on this phone, and the write is
                                        // queued and applied when it reconnects.
                                        launch { runCatching { repository.setLocked(parentUid, childId, false) } }
                                        finish()
                                    } else {
                                        unlockError = if (attempts.recordFailure()) {
                                            "Too many incorrect attempts. Try again in ${attempts.minutesRemaining()} minutes."
                                        } else {
                                            "Incorrect passcode."
                                        }
                                    }
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    companion object {
        const val EXTRA_REASON = "reason"
    }
}

@Composable
private fun RequestMoreTimeSection(requestedExtraMinutes: Int?, onRequest: (Int) -> Unit) {
    if (requestedExtraMinutes != null) {
        Text(
            "Asked a parent for $requestedExtraMinutes more minutes - waiting to hear back.",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium
        )
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { onRequest(5) }) { Text("+5 min") }
            OutlinedButton(onClick = { onRequest(15) }) { Text("+15 min") }
        }
    }
}
