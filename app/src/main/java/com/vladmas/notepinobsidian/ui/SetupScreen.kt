package com.vladmas.notepinobsidian.ui

import android.Manifest
import android.app.AlarmManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vladmas.notepinobsidian.Notifier
import com.vladmas.notepinobsidian.Obsidian
import com.vladmas.notepinobsidian.Phone
import com.vladmas.notepinobsidian.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** What the app needs from Android, read fresh each time a screen shows. */
data class SetupChecks(
    val notifications: Boolean,
    val exactAlarms: Boolean,
    val battery: Boolean,
    val obsidian: Boolean,
    /** Null below Android 14, where pins can't be swiped away anyway, or when the system won't say. */
    val exemptFromDismissal: Boolean?,
) {
    /** Settings that stop reminders from arriving on time; the others are recommendations. */
    val attention get() = listOf(notifications, exactAlarms).count { !it }

    companion object {
        fun read(context: Context) = SetupChecks(
            notifications = Notifier.notificationsAllowed(context),
            exactAlarms = Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms(),
            battery = context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName),
            obsidian = Obsidian.isInstalled(context),
            exemptFromDismissal = Notifier.exemptFromDismissal(context),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(refreshKey: Int, firstRun: Boolean, onDone: () -> Unit) {
    val context = LocalContext.current
    var localRefresh by remember { mutableIntStateOf(0) }
    val checks = remember(refreshKey, localRefresh) { SetupChecks.read(context) }
    val testLocked = remember(refreshKey, localRefresh) { Notifier.testNotificationLocked(context) }
    val phone = remember { Phone.detect(context) }
    val autostart = remember(refreshKey, localRefresh) {
        if (phone.maker == Phone.Maker.XIAOMI) Phone.xiaomiAutostart(context) else null
    }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { localRefresh++ }
    val scope = rememberCoroutineScope()
    // Posting a notification is asynchronous; read its state back a moment later.
    val refreshSoon = { scope.launch { delay(800); localRefresh++ } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (firstRun) R.string.welcome else R.string.setup)) },
                navigationIcon = { if (!firstRun) BackButton(onDone) },
            )
        },
    ) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            if (firstRun) {
                Text(stringResource(R.string.setup_intro), style = MaterialTheme.typography.bodyLarge)
            }

            StatusCard(stringResource(R.string.notifications), checks.notifications, stringResource(R.string.notifications_text)) {
                if (!checks.notifications) {
                    ActionRow {
                        if (Build.VERSION.SDK_INT >= 33) {
                            Button(onClick = { askPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                                Text(stringResource(R.string.allow))
                            }
                        }
                        OutlinedButton(onClick = { context.startSafely(Notifier.appSettingsIntent(context)) }) {
                            Text(stringResource(R.string.settings))
                        }
                    }
                }
            }

            StatusCard(
                stringResource(R.string.exact_alarms),
                checks.exactAlarms,
                stringResource(R.string.exact_alarms_text),
            ) {
                if (!checks.exactAlarms && Build.VERSION.SDK_INT >= 31) {
                    ActionRow {
                        Button(onClick = {
                            context.startSafely(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
                        }) { Text(stringResource(R.string.allow)) }
                    }
                }
            }

            // Xiaomi and Samsung keep this switch in the app's own settings, under their own names.
            val (batteryHow, batteryButton) = when (phone.maker) {
                Phone.Maker.XIAOMI -> R.string.battery_how_xiaomi to (R.string.app_info to Phone.appInfoIntent(context))
                Phone.Maker.SAMSUNG -> R.string.battery_how_samsung to (R.string.app_info to Phone.appInfoIntent(context))
                else -> R.string.battery_how_other to
                    (R.string.battery_settings to Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
            StatusCard(
                stringResource(R.string.battery),
                checks.battery,
                if (checks.battery) stringResource(R.string.battery_ok)
                else stringResource(R.string.battery_restricted, stringResource(batteryHow)),
            ) {
                if (!checks.battery) {
                    ActionRow {
                        OutlinedButton(onClick = { context.startSafely(batteryButton.second) }) { Text(stringResource(batteryButton.first)) }
                    }
                }
            }

            PhoneCard(phone, autostart)

            StatusCard(
                "Obsidian",
                checks.obsidian,
                stringResource(if (checks.obsidian) R.string.obsidian_installed else R.string.obsidian_missing),
            )

            if (Build.VERSION.SDK_INT >= 34) {
                val exempt = checks.exemptFromDismissal == true
                StatusCard(
                    stringResource(R.string.undismissable),
                    exempt,
                    stringResource(if (exempt) R.string.undismissable_done else R.string.undismissable_how),
                ) {
                    if (!exempt) {
                        CodeBlock(Notifier.adbCommand(context))
                        ActionRow {
                            OutlinedButton(onClick = { copy(context, Notifier.adbCommand(context)) }) { Text(stringResource(R.string.copy_command)) }
                        }
                    }
                    Text(
                        stringResource(
                            when (testLocked) {
                                null -> R.string.test_unknown
                                true -> R.string.test_locked
                                false -> R.string.test_unlocked
                            },
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    ActionRow {
                        Button(onClick = { Notifier.postTest(context); refreshSoon() }) { Text(stringResource(R.string.post_test)) }
                        OutlinedButton(onClick = { Notifier.removeTest(context); refreshSoon() }) { Text(stringResource(R.string.remove)) }
                    }
                }
            }

            Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text(stringResource(if (firstRun) R.string.setup_continue else R.string.setup_done)) }
            Spacer(Modifier.height(16.dp))
        }
    }
}

private data class PhoneTip(val title: String, val text: String, val actions: List<Pair<String, Intent>> = emptyList())

/** What this phone's maker adds on top of Android, and what to change so pins and reminders keep working. */
@Composable
private fun PhoneCard(phone: Phone, autostart: Boolean?) {
    val context = LocalContext.current
    val tips = phoneTips(context, phone, autostart)
    StatusCard(stringResource(R.string.this_phone), if (phone.maker == Phone.Maker.XIAOMI) autostart else null, phone.summary) {
        if (tips.isEmpty()) {
            Text(
                stringResource(R.string.phone_nothing_to_do),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        tips.forEach { tip ->
            Text(tip.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp))
            Text(tip.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
            if (tip.actions.isNotEmpty()) {
                ActionRow {
                    tip.actions.forEach { (label, intent) ->
                        OutlinedButton(onClick = { context.startSafely(intent) }) { Text(label) }
                    }
                }
            }
        }
    }
}

private fun phoneTips(context: Context, phone: Phone, autostart: Boolean?): List<PhoneTip> {
    fun text(id: Int) = context.getString(id)
    val appInfo = text(R.string.app_info) to Phone.appInfoIntent(context)
    return when (phone.maker) {
        Phone.Maker.XIAOMI -> listOf(
            PhoneTip(
                when (autostart) {
                    true -> text(R.string.autostart_on)
                    false -> text(R.string.autostart_off)
                    null -> text(R.string.autostart)
                },
                text(if (autostart == true) R.string.autostart_on_text else R.string.autostart_off_text),
                listOfNotNull(Phone.xiaomiAutostartIntent(context)?.let { text(R.string.autostart) to it }),
            ),
        )
        Phone.Maker.SAMSUNG -> listOfNotNull(
            PhoneTip(text(R.string.samsung_sleeping), text(R.string.samsung_sleeping_text)),
            PhoneTip(
                text(R.string.samsung_icons),
                text(if (phone.oneUi != null && phone.oneUi >= 7) R.string.samsung_icons_one_ui_7 else R.string.samsung_icons_older),
            ),
            if (phone.oneUi == null || phone.oneUi >= 7) {
                PhoneTip(text(R.string.samsung_clear), text(R.string.samsung_clear_text))
            } else {
                null
            },
        )
        Phone.Maker.HUAWEI, Phone.Maker.HONOR -> listOf(
            PhoneTip(
                text(R.string.huawei_app_launch),
                text(R.string.huawei_app_launch_text),
                listOf(Phone.appLaunchIntent(context)?.let { text(R.string.huawei_app_launch) to it } ?: appInfo),
            ),
        )
        Phone.Maker.OPPO, Phone.Maker.ONEPLUS, Phone.Maker.REALME -> listOf(
            PhoneTip(text(R.string.oppo_background), text(R.string.oppo_background_text), listOf(appInfo)),
        )
        Phone.Maker.VIVO -> listOf(
            PhoneTip(text(R.string.vivo_background), text(R.string.vivo_background_text), listOf(appInfo)),
        )
        Phone.Maker.OTHER -> emptyList()
    }
}

private fun copy(context: Context, text: String) {
    val clip = ClipData.newPlainText(context.getString(R.string.adb_command_label), text)
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
    // Android 13+ confirms copies itself.
    if (Build.VERSION.SDK_INT < 33) Toast.makeText(context, R.string.copied, Toast.LENGTH_SHORT).show()
}
