package org.openscreentime.parent.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.openscreentime.parent.ParentApp
import org.openscreentime.parent.data.SelfProfileStore
import org.openscreentime.parent.monitor.AppLimitAccessibilityService
import org.openscreentime.shared.model.BlockReason
import org.openscreentime.shared.model.blockScreenCopy
import org.openscreentime.shared.model.randomAlternativeActivity
import org.openscreentime.shared.util.PasscodeAttemptStore
import org.openscreentime.shared.util.PasscodeHasher
import org.openscreentime.sharedui.ParentUnlockDialog

/**
 * Self-tracking equivalent of the kid app's BlockOverlayActivity (see #8) - shown when
 * the parent's own device hits its own daily or per-app limit, or its own "Lock now".
 */
class BlockOverlayActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val reason = BlockReason.fromWireValue(intent.getStringExtra(EXTRA_REASON))
        val copy = blockScreenCopy(
            reason = reason,
            bedtimeEndMinutes = AppLimitAccessibilityService.bedtimeEndMinutes,
            // The parent locked their own device here, not another parent - "ask a
            // parent to resume it" (the kid app's wording) wouldn't fit.
            lockMessage = "You paused your own screen time. Resume it from the dashboard when you're ready.",
            defaultMessage = "This is your own limit, from your own goals."
        )

        val repository = (application as ParentApp).repository
        val parentUid = repository.currentUid
        val selfChildId = SelfProfileStore(this).childId

        setContent {
            OpenScreenTimeTheme {
                val scope = rememberCoroutineScope()
                var showParentUnlock by remember { mutableStateOf(false) }
                var unlockError by remember { mutableStateOf<String?>(null) }
                var verifying by remember { mutableStateOf(false) }

                // See the kid app's BlockOverlayActivity for why: the back gesture/button
                // used to just finish this activity and reveal the blocked app underneath.
                BackHandler {}

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
                        // The parent's own "Lock now": the family passcode lifts it right here.
                        if (reason == BlockReason.PARENT_LOCK && parentUid != null && selfChildId != null) {
                            Spacer(Modifier.height(24.dp))
                            OutlinedButton(
                                onClick = { unlockError = null; showParentUnlock = true },
                                modifier = Modifier.testTag("block_parent_unlock")
                            ) { Text("Unlock with passcode") }
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

                if (showParentUnlock && parentUid != null && selfChildId != null) {
                    ParentUnlockDialog(
                        verifying = verifying,
                        error = unlockError,
                        onDismiss = { showParentUnlock = false },
                        onSubmit = { pin ->
                            val attempts = PasscodeAttemptStore(this@BlockOverlayActivity)
                            if (attempts.isLocked()) {
                                unlockError = "Too many incorrect attempts. Try again in ${attempts.minutesRemaining()} minutes."
                            } else {
                                verifying = true
                                scope.launch {
                                    val info = try {
                                        repository.getParentPasscode(parentUid)
                                    } catch (e: Exception) {
                                        null
                                    }
                                    val ok = info != null &&
                                        withContext(Dispatchers.Default) { PasscodeHasher.verify(pin, info.salt, info.hash) }
                                    verifying = false
                                    when {
                                        info == null -> unlockError = "Couldn't check the passcode. Check your connection and try again."
                                        ok -> {
                                            attempts.recordSuccess()
                                            AppLimitAccessibilityService.lockedCache = false
                                            launch { runCatching { repository.setLocked(parentUid, selfChildId, false) } }
                                            finish()
                                        }
                                        else -> unlockError = if (attempts.recordFailure()) {
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
