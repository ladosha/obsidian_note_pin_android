package com.vladmas.notepinobsidian.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vladmas.notepinobsidian.Item
import com.vladmas.notepinobsidian.Obsidian
import com.vladmas.notepinobsidian.R
import com.vladmas.notepinobsidian.ReminderType
import com.vladmas.notepinobsidian.Store
import java.time.LocalDateTime

/** The reminders and pins from Obsidian: pinned notes, upcoming reminders, then past ones. Tapping opens the note. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(refreshKey: Int, onOpenSettings: () -> Unit, onOpenSetup: () -> Unit) {
    val context = LocalContext.current
    val now = remember(refreshKey) { LocalDateTime.now() }
    val items = remember(refreshKey) { Store.allItems(context) }
    val attention = remember(refreshKey) { SetupChecks.read(context).attention }
    val showVault = items.map { it.vault }.distinct().size > 1
    val pinned = items.filter { it.type == ReminderType.STATUSBAR }.sortedBy { it.title.lowercase() }.map { it to null }
    val timed = items.filter { it.type != ReminderType.STATUSBAR }.map { it to it.nextFire(now) }
    val upcoming = timed.filter { (_, next) -> next != null && !next.isBefore(now) }.sortedBy { it.second }
    val past = timed.filter { (_, next) -> next == null || next.isBefore(now) }.sortedByDescending { it.second }
    val pinnedTitle = stringResource(R.string.section_pinned)
    val upcomingTitle = stringResource(R.string.section_upcoming)
    val pastTitle = stringResource(R.string.section_past)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(painterResource(R.drawable.ic_settings), contentDescription = stringResource(R.string.settings))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (attention > 0) item { AttentionCard(attention, onOpenSetup) }
            if (items.isEmpty()) item { EmptyState() }
            group(pinnedTitle, pinned, showVault)
            group(upcomingTitle, upcoming, showVault)
            group(pastTitle, past, showVault)
        }
    }
}

private fun LazyListScope.group(title: String, entries: List<Pair<Item, LocalDateTime?>>, showVault: Boolean) {
    if (entries.isEmpty()) return
    item(key = "title:$title") { SectionTitle(title) }
    items(entries, key = { it.first.key }) { (item, next) ->
        val context = LocalContext.current
        NoteCard(item, Format.schedule(context, item, next), showVault) { context.startSafely(Obsidian.openNoteIntent(item)) }
    }
}

@Composable
private fun AttentionCard(count: Int, onOpenSetup: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.attention_title), style = MaterialTheme.typography.titleMedium)
            Text(pluralStringResource(R.plurals.attention_text, count, count), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onOpenSetup, contentPadding = PaddingValues(0.dp)) { Text(stringResource(R.string.open_setup)) }
        }
    }
}

@Composable
private fun EmptyState() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.empty_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.empty_text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
