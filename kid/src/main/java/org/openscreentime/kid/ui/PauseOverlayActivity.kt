package org.openscreentime.kid.ui

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * A brief, calm breath before continuing into an app that's past half its daily limit -
 * not a block. Interrupts the automatic, undecided reach for the app (see #12) without
 * removing the choice to continue; the short countdown is the whole point, so "Continue"
 * is deliberately not tappable the instant this appears.
 */
class PauseOverlayActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appName = intent.getStringExtra(EXTRA_APP_NAME) ?: "This app"

        setContent {
            OpenScreenTimeTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var secondsLeft by remember { mutableIntStateOf(PAUSE_SECONDS) }

                    LaunchedEffect(Unit) {
                        while (secondsLeft > 0) {
                            delay(1000)
                            secondsLeft -= 1
                        }
                    }

                    Column(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            "Just a breath",
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "$appName is over halfway to today's limit. Take a moment - is this " +
                                "still what you meant to do?",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(32.dp))
                        Button(onClick = { finish() }, enabled = secondsLeft == 0) {
                            Text(if (secondsLeft > 0) "Continue ($secondsLeft)" else "Continue")
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_APP_NAME = "app_name"
        private const val PAUSE_SECONDS = 4
    }
}
