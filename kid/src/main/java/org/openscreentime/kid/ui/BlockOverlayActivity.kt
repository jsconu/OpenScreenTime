package org.openscreentime.kid.ui

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.openscreentime.kid.monitor.AppLimitAccessibilityService
import org.openscreentime.shared.model.formatMinutesOfDay
import org.openscreentime.shared.model.randomAlternativeActivity

/** Full-screen interruption shown when a daily or per-app limit is reached. */
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
                            when (reason) {
                                "parent_lock" -> "A parent has paused screen time. Ask them to resume it."
                                "bedtime" -> {
                                    val end = AppLimitAccessibilityService.bedtimeEndMinutes
                                    if (end != null) "Screen time starts again at ${formatMinutesOfDay(end)}."
                                    else "Screen time starts again in the morning."
                                }
                                else -> "Ask a parent if you need more time."
                            },
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        // Winding down for bedtime is the point there, not finding something
                        // else active to do - see #16's design-principle note.
                        if (reason != "bedtime") {
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
