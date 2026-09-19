package org.openscreentime.sharedui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import org.openscreentime.shared.model.STATUS_CAUTION_MESSAGE
import org.openscreentime.shared.model.STATUS_GOOD_MESSAGE
import org.openscreentime.shared.model.STATUS_ICON_LOCATION_TEXT
import org.openscreentime.shared.model.STATUS_STOP_MESSAGE

/**
 * The one explanation of the calm status icons, shared by the kid and parent apps so both say the
 * same thing: where the icon appears (top-left of the status bar), what each of the three icons
 * (thumbs up, open hand, stop) means, and what pulling the shade down shows. [footnote] is the
 * per-app closing paragraph (why a child sees only this signal, or when a parent sees it).
 */
@Composable
fun StatusIconGuide(
    @DrawableRes goodIcon: Int,
    @DrawableRes cautionIcon: Int,
    @DrawableRes stopIcon: Int,
    footnote: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.testTag("status_icon_legend")) {
        Text("What the status icons mean", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(4.dp))
        Text(STATUS_ICON_LOCATION_TEXT, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            GuideRow(goodIcon, "Thumbs up", STATUS_GOOD_MESSAGE)
            GuideRow(cautionIcon, "Open hand", STATUS_CAUTION_MESSAGE)
            GuideRow(stopIcon, "Stop", STATUS_STOP_MESSAGE)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            footnote,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.testTag("status_icon_legend_why")
        )
    }
}

@Composable
private fun GuideRow(@DrawableRes iconRes: Int, name: String, meaning: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text("$name: $meaning", style = MaterialTheme.typography.bodySmall)
    }
}
