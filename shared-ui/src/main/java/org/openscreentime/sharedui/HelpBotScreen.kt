package org.openscreentime.sharedui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.openscreentime.shared.model.HelpAudience
import org.openscreentime.shared.model.HelpBot
import org.openscreentime.shared.model.HelpReply

/** The prompt the bot opens with - also the copy the design calls for verbatim. */
const val HELP_BOT_PROMPT =
    "Ask me anything about how to use this app or what are reasonable limits for screen time."

private sealed interface HelpMessage {
    data class FromUser(val text: String) : HelpMessage
    data class FromBot(val reply: HelpReply) : HelpMessage
}

/**
 * See #36 - the "?" help screen, shared by the parent and kid apps. A chat-shaped front for
 * [HelpBot]: a small, offline, cited retriever, deliberately not a language model, so nothing typed
 * here leaves the phone and health answers only ever say what a public source says.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpBotScreen(audience: HelpAudience, onBack: () -> Unit) {
    val bot = remember { HelpBot.default }
    val messages = remember {
        mutableStateListOf<HelpMessage>(
            HelpMessage.FromBot(HelpReply(text = HELP_BOT_PROMPT, relatedQuestions = bot.suggestions(audience)))
        )
    }
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    fun ask(question: String) {
        val trimmed = question.trim()
        if (trimmed.isEmpty()) return
        messages += HelpMessage.FromUser(trimmed)
        messages += HelpMessage.FromBot(bot.reply(trimmed, audience))
        draft = ""
    }

    LaunchedEffect(messages.size) {
        listState.animateScrollToItem(messages.lastIndex)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Help") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }
            )
        },
        bottomBar = {
            Column(Modifier.imePadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        placeholder = { Text("Ask a question") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("help_input")
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        enabled = draft.isNotBlank(),
                        onClick = { ask(draft) },
                        modifier = Modifier.testTag("help_send")
                    ) { Text("Send") }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Answers come from a fixed, cited knowledge base - not AI - and nothing you " +
                        "type leaves this phone. General guidance, not medical advice.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(messages.size) { index ->
                when (val message = messages[index]) {
                    is HelpMessage.FromUser -> UserBubble(message.text)
                    is HelpMessage.FromBot -> BotBubble(message.reply, onAsk = ::ask)
                }
            }
        }
    }
}

@Composable
private fun UserBubble(text: String) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            modifier = Modifier.fillMaxWidth(0.85f)
        ) {
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}

@Composable
private fun BotBubble(reply: HelpReply, onAsk: (String) -> Unit) {
    val uriHandler = LocalUriHandler.current
    Column(Modifier.fillMaxWidth(0.95f).testTag("help_bot_message")) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(12.dp)) {
                Text(reply.text, style = MaterialTheme.typography.bodyMedium)
                if (reply.sources.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("Sources", style = MaterialTheme.typography.labelMedium)
                    reply.sources.forEach { source ->
                        Text(
                            source.title,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(vertical = 2.dp)
                                .clickable { uriHandler.openUri(source.url) }
                        )
                    }
                }
            }
        }
        if (reply.relatedQuestions.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(reply.relatedQuestions) { question ->
                    AssistChip(onClick = { onAsk(question) }, label = { Text(question) })
                }
            }
        }
    }
}
