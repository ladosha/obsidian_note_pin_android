package com.vladmas.notepinobsidian

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.vladmas.notepinobsidian.ui.NotePinTheme
import com.vladmas.notepinobsidian.ui.MainScreen
import com.vladmas.notepinobsidian.ui.SettingsScreen
import com.vladmas.notepinobsidian.ui.SetupScreen

private enum class Screen { Setup, Main, Settings }

class MainActivity : ComponentActivity() {
    /** Bumped on every resume, so screens re-read permissions and data changed while the app was away. */
    private var resumeCount by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Notifier.createChannels(this)
        setContent {
            NotePinTheme {
                Surface(color = MaterialTheme.colorScheme.background) { App(resumeCount) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Brings back pins and alarms if the app was force-stopped.
        Reminders.refresh(this)
        resumeCount++
    }

    /**
     * Pulling down the notification shade over the app doesn't pause it, only takes its focus. Re-reading when the
     * focus comes back shows a test notification swiped or cleared there.
     */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) resumeCount++
    }
}

@Composable
private fun App(refreshKey: Int) {
    val context = LocalContext.current
    var setupDone by remember { mutableStateOf(AppSettings.setupDone(context)) }
    var screen by rememberSaveable { mutableStateOf(if (setupDone) Screen.Main else Screen.Setup) }
    var setupFromSettings by rememberSaveable { mutableStateOf(false) }

    when (screen) {
        Screen.Main -> MainScreen(
            refreshKey,
            onOpenSettings = { screen = Screen.Settings },
            onOpenSetup = {
                setupFromSettings = false
                screen = Screen.Setup
            },
        )
        Screen.Settings -> {
            BackHandler { screen = Screen.Main }
            SettingsScreen(
                refreshKey,
                onBack = { screen = Screen.Main },
                onOpenSetup = {
                    setupFromSettings = true
                    screen = Screen.Setup
                },
            )
        }
        Screen.Setup -> {
            val leave = { screen = if (setupFromSettings) Screen.Settings else Screen.Main }
            BackHandler(enabled = setupDone, onBack = leave)
            SetupScreen(refreshKey, firstRun = !setupDone) {
                if (!setupDone) {
                    AppSettings.setSetupDone(context)
                    setupDone = true
                }
                leave()
            }
        }
    }
}

/** Asks whether a vault that hasn't connected before may send its reminders, then replies to Obsidian. */
class PairActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val message = intent.data?.let(Sync::parse)
        if (message == null) {
            finish()
            return
        }
        setContent {
            NotePinTheme {
                AlertDialog(
                    onDismissRequest = { decide(message, allow = false) },
                    icon = { Icon(painterResource(R.drawable.ic_pin), contentDescription = null) },
                    title = { Text(stringResource(R.string.pair_title)) },
                    text = { Text(stringResource(R.string.pair_text, message.vault)) },
                    confirmButton = { TextButton(onClick = { decide(message, allow = true) }) { Text(stringResource(R.string.allow)) } },
                    dismissButton = { TextButton(onClick = { decide(message, allow = false) }) { Text(stringResource(R.string.pair_deny)) } },
                )
            }
        }
    }

    private fun decide(message: Sync.Message, allow: Boolean) {
        if (isFinishing) return
        val reply = if (allow) {
            Store.allow(this, message.vault, message.token)
            Sync.receive(this, message)
        } else {
            Sync.declined(message)
        }
        Sync.reply(this, message, reply)
        finish()
    }
}
