package org.openscreentime.parent.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import org.openscreentime.shared.nearby.NearbyEnvelope
import org.openscreentime.shared.nearby.NearbyLink

/**
 * Linking a kid's phone to this one, with no account and no server: this phone shows a QR code
 * carrying a freshly made key, the kid's phone scans it once, and from then on the two can find
 * each other on the same Wi-Fi.
 *
 * The screen spends as much space on what this does NOT do as on what it does, because the failure
 * mode is a parent who thinks they can see their child's phone from work and cannot.
 */
@Composable
fun NearbyLinkScreen(
    /** The kid's phone, once one has actually reported in under this code. */
    linkedChildName: String?,
    lastSyncedAtMs: Long,
    /** Called with each code put on screen, so this phone starts listening under it straight away. */
    onOffering: (NearbyLink) -> Unit,
    onUnlink: () -> Unit,
    onBack: () -> Unit
) {
    // Generated once per visit to this screen: a code that has been on display for a while, or
    // photographed, should not stay valid forever.
    var offered by remember { mutableStateOf(NearbyLink(NearbyEnvelope.newKey(), android.os.Build.MODEL ?: "A parent's phone")) }

    // This phone listens under the code the moment it is shown, not when a person says it has been
    // scanned. Waiting for a button meant the kid's phone always called before anyone was
    // listening, and the first sync after pairing was silently lost.
    LaunchedEffect(offered) { onOffering(offered) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Link a kid's phone") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (linkedChildName != null) {
                LinkedCard(
                    peerName = linkedChildName,
                    lastSyncedAtMs = lastSyncedAtMs,
                    onUnlink = onUnlink,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(24.dp))
                Text(
                    "Linking a different phone replaces this one.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(12.dp))
            }

            Text(
                "On your kid's phone, open OpenScreenTime Kid and tap \"Scan a parent's code\" " +
                    "on its main screen. Then point it at this code.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))

            val qr = remember(offered) { qrBitmap(NearbyLink.toPayload(offered)) }
            if (qr != null) {
                Image(
                    bitmap = qr.asImageBitmap(),
                    contentDescription = "Pairing QR code",
                    modifier = Modifier.size(260.dp).testTag("nearby_qr")
                )
            } else {
                Text("Couldn't draw the code. Try again.", style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.height(24.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("What linking gives you", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "While both phones are on the same Wi-Fi and awake, their phone sends you " +
                            "today's usage and picks up the limits you set here.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text("What it can't do", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Nothing while you're apart. Away from home - at school, at a friend's, on " +
                            "mobile data - there's no connection between the phones, so what you see " +
                            "here is whatever arrived last, and a limit you change now reaches their " +
                            "phone when you're both home again. Some guest networks block phones from " +
                            "seeing each other and never work at all.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "Their phone keeps counting and enforcing the whole time either way - that " +
                            "part never needed a connection.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            // No "I've scanned it" button: this phone knows, because their phone tells it.
            Text(
                if (linkedChildName == null) {
                    "Waiting for their phone... this page will say so as soon as it arrives."
                } else {
                    "$linkedChildName is linked. You can leave this page."
                },
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().testTag("nearby_waiting")
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { offered = NearbyLink(NearbyEnvelope.newKey(), android.os.Build.MODEL ?: "A parent's phone") },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Show a new code") }
        }
    }
}

@Composable
private fun LinkedCard(peerName: String, lastSyncedAtMs: Long, onUnlink: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = modifier
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Linked to $peerName", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(lastSyncedDescription(lastSyncedAtMs), style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onUnlink, modifier = Modifier.fillMaxWidth()) { Text("Unlink this phone") }
        }
    }
}

/**
 * How stale the numbers on screen are, in words. Always shown next to synced usage: on a local link
 * the two phones are often apart, and "3 hours ago" is the difference between reading a report and
 * believing you are watching a phone.
 */
fun lastSyncedDescription(lastSyncedAtMs: Long, nowMs: Long = System.currentTimeMillis()): String {
    if (lastSyncedAtMs <= 0) return "Not synced yet - their phone reaches this one when you're both on the same Wi-Fi."
    val minutes = ((nowMs - lastSyncedAtMs) / 60_000L).coerceAtLeast(0)
    return when {
        minutes < 2 -> "Synced just now"
        minutes < 60 -> "Synced $minutes minutes ago"
        minutes < 60 * 24 -> "Synced ${minutes / 60} hour${plural(minutes / 60)} ago"
        else -> "Synced ${minutes / (60 * 24)} day${plural(minutes / (60 * 24))} ago"
    }
}

private fun plural(value: Long) = if (value == 1L) "" else "s"

/** Null rather than a crash if the encoder ever refuses - the screen says so and offers a new code. */
private fun qrBitmap(payload: String, size: Int = 640): Bitmap? = runCatching {
    val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, size, size)
    Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply {
        for (x in 0 until size) {
            for (y in 0 until size) {
                setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
    }
}.getOrNull()
