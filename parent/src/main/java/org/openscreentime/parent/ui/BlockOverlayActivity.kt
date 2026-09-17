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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.openscreentime.parent.monitor.AppLimitAccessibilityService
import org.openscreentime.shared.model.BlockReason
import org.openscreentime.shared.model.blockScreenCopy
import org.openscreentime.shared.model.randomAlternativeActivity

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

        setContent {
            OpenScreenTimeTheme {
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
            }
        }
    }

    companion object {
        const val EXTRA_REASON = "reason"
    }
}
