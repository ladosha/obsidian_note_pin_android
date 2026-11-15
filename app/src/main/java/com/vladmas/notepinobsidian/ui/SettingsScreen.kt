package com.vladmas.notepinobsidian.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import com.vladmas.notepinobsidian.About
import com.vladmas.notepinobsidian.AppSettings
import com.vladmas.notepinobsidian.Notifier
import com.vladmas.notepinobsidian.R
import com.vladmas.notepinobsidian.Reminders
import com.vladmas.notepinobsidian.Store

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(refreshKey: Int, onBack: () -> Unit, onOpenSetup: () -> Unit) {
    val context = LocalContext.current
    var localRefresh by remember { mutableIntStateOf(0) }
    val checks = remember(refreshKey, localRefresh) { SetupChecks.read(context) }
    val vaults = remember(refreshKey, localRefresh) { Store.connectedVaults(context) }
    val snapshots = remember(refreshKey, localRefresh) { Store.snapshots(context).associateBy { it.vault } }
    var separateNotifications by remember { mutableStateOf(AppSettings.separateNotifications(context)) }
    var disconnecting by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.settings)) }, navigationIcon = { BackButton(onBack) }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            SectionTitle(stringResource(R.string.notifications))
            SwitchRow(
                stringResource(R.string.separate_title),
                stringResource(R.string.separate_text),
                separateNotifications,
            ) {
                separateNotifications = it
                AppSettings.setSeparateNotifications(context, it)
                Notifier.repostPins(context)
            }

            SectionTitle(stringResource(R.string.setup))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenSetup)
                    .padding(vertical = 8.dp),
            ) {
                Text(stringResource(R.string.setup_and_permissions), style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (checks.attention == 0) {
                        stringResource(R.string.setup_all_set)
                    } else {
                        pluralStringResource(R.plurals.setup_attention, checks.attention, checks.attention)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (checks.attention == 0) MaterialTheme.colorScheme.onSurfaceVariant else AttentionOrange,
                )
            }

            SectionTitle(stringResource(R.string.section_vaults))
            if (vaults.isEmpty()) {
                Text(
                    stringResource(R.string.no_vaults),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            vaults.forEach { vault ->
                val snapshot = snapshots[vault]
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(vault, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            snapshot?.let {
                                pluralStringResource(R.plurals.vault_summary, it.items.size, it.items.size, Format.dateTime(it.receivedAt))
                            } ?: stringResource(R.string.vault_nothing),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedButton(onClick = { disconnecting = vault }) { Text(stringResource(R.string.disconnect)) }
                }
            }

            HorizontalDivider(Modifier.padding(top = 24.dp))
            SectionTitle(stringResource(R.string.about))
            AboutSection()
        }
    }

    disconnecting?.let { vault ->
        AlertDialog(
            onDismissRequest = { disconnecting = null },
            title = { Text(stringResource(R.string.disconnect_title, vault)) },
            text = { Text(stringResource(R.string.disconnect_text)) },
            confirmButton = {
                TextButton(onClick = {
                    Store.disconnect(context, vault)
                    Reminders.refresh(context)
                    disconnecting = null
                    localRefresh++
                }) { Text(stringResource(R.string.disconnect)) }
            },
            dismissButton = { TextButton(onClick = { disconnecting = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun AboutSection() {
    val context = LocalContext.current
    val linkStyle = TextLinkStyles(
        style = SpanStyle(
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            textDecoration = TextDecoration.Underline,
        ),
    )
    val template = stringResource(R.string.about_credit)
    val credit = buildAnnotatedString {
        // The name is a link, so it goes in by hand, where the translation has %1$s.
        append(template.substringBefore("%1\$s"))
        withLink(LinkAnnotation.Url(About.DEVELOPER_SITE, linkStyle)) { append(About.DEVELOPER_NAME) }
        append(template.substringAfter("%1\$s", ""))
    }
    val version = remember { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(credit, style = MaterialTheme.typography.bodyLarge)
        Button(
            onClick = { context.startSafely(Intent(Intent.ACTION_VIEW, Uri.parse(About.KOFI_URL))) },
            colors = ButtonDefaults.buttonColors(containerColor = KofiBlue, contentColor = Color.White),
        ) { Text(stringResource(R.string.kofi)) }
        Text(stringResource(R.string.version, version.orEmpty()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Spacer(Modifier.height(32.dp))
}
