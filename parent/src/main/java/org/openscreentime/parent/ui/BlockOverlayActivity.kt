package org.openscreentime.parent.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.openscreentime.parent.monitor.AppLimitAccessibilityService
import org.openscreentime.shared.model.formatMinutesOfDay

/**
 * Self-tracking equivalent of the kid app's BlockOverlayActivity (see #8) - shown when
 * the parent's own device hits its own daily or per-app limit, or its own "Lock now".
 */
class BlockOverlayActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val reason = intent.getStringExtra(EXTRA_REASON) ?: "app_limit"

        setContent {
            OpenScreenTimeTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            when (reason) {
                                "daily_limit" -> "Screen time is up for today"
                                "parent_lock" -> "Screen time has been paused"
                                "bedtime" -> "It's bedtime"
                                else -> "This app's time limit is reached"
                            },
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            if (reason == "bedtime") {
                                val end = AppLimitAccessibilityService.bedtimeEndMinutes
                                if (end != null) "Screen time starts again at ${formatMinutesOfDay(end)}."
                                else "Screen time starts again in the morning."
                            } else {
                                "This is your own limit, from your own goals."
                            },
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium
                        )
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
                            Text("Go to home screen")
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
