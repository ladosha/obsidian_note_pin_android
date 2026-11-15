package com.vladmas.notepinobsidian

import android.app.AppOpsManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import java.time.LocalDateTime
import java.time.ZoneId

object Notifier {
    const val CHANNEL_PINS = "pins"
    const val CHANNEL_REMINDERS = "reminders"
    private const val TAG_TEST = "test"
    private const val TAG_PIN = "pin:"
    private const val TAG_REMINDER = "reminder:"
    private const val PIN_STATE_PREFS = "pin_state"

    /** Notification.FLAG_NO_DISMISS (hidden API): Android 14+ sets it on an ongoing notification only when it really can't be swiped away. */
    private const val FLAG_NO_DISMISS = 0x00002000

    /** AppOpsManager.OPSTR_SYSTEM_EXEMPT_FROM_DISMISSIBLE_NOTIFICATIONS (hidden API), the op the ADB command grants. */
    private const val OP_EXEMPT_FROM_DISMISSAL = "android:system_exempt_from_dismissible_notifications"

    fun adbCommand(context: Context) =
        "adb shell appops set --uid ${context.packageName} SYSTEM_EXEMPT_FROM_DISMISSIBLE_NOTIFICATIONS allow"

    fun createChannels(context: Context) {
        val pins = NotificationChannel(CHANNEL_PINS, context.getString(R.string.channel_pins), NotificationManager.IMPORTANCE_LOW).apply {
            description = context.getString(R.string.channel_pins_description)
            setShowBadge(false)
        }
        val reminders = NotificationChannel(CHANNEL_REMINDERS, context.getString(R.string.channel_reminders), NotificationManager.IMPORTANCE_HIGH).apply {
            description = context.getString(R.string.channel_reminders_description)
        }
        manager(context).createNotificationChannels(listOf(pins, reminders))
    }

    fun notificationsAllowed(context: Context) = manager(context).areNotificationsEnabled()

    fun channelEnabled(context: Context, channel: String) =
        manager(context).getNotificationChannel(channel)?.importance != NotificationManager.IMPORTANCE_NONE

    fun channelSettingsIntent(context: Context, channel: String): Intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .putExtra(Settings.EXTRA_CHANNEL_ID, channel)

    fun appSettingsIntent(context: Context): Intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

    /** Whether the ADB exemption is granted, on Android 14+. Null when the system won't say. */
    fun exemptFromDismissal(context: Context): Boolean? {
        if (Build.VERSION.SDK_INT < 34) return null
        return try {
            val appOps = context.getSystemService(AppOpsManager::class.java)
            appOps.unsafeCheckOpNoThrow(OP_EXEMPT_FROM_DISMISSAL, Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            null
        }
    }

    fun postTest(context: Context) {
        val text = context.getString(R.string.test_notification_text)
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val remove = PendingIntent.getBroadcast(
            context, 0, Intent(context, RemoveTestNotificationReceiver::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = Notification.Builder(context, CHANNEL_PINS)
            .setSmallIcon(R.drawable.ic_pin)
            .setContentTitle(context.getString(R.string.test_notification_title))
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setOngoing(true)
            .setShowWhen(false)
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, context.getString(R.string.remove), remove).build())
        if (AppSettings.separateNotifications(context)) builder.setGroup(TAG_TEST)
        manager(context).notify(TAG_TEST, 0, builder.build())
    }

    fun removeTest(context: Context) = manager(context).cancel(TAG_TEST, 0)

    /** How the system treated the test notification: null if it isn't showing, else whether it can't be swiped away. */
    fun testNotificationLocked(context: Context): Boolean? =
        manager(context).activeNotifications.firstOrNull { it.tag == TAG_TEST }
            ?.let { Build.VERSION.SDK_INT < 34 || it.notification.flags and FLAG_NO_DISMISS != 0 }

    /**
     * Shows a persistent notification for every note pinned to the status bar, and removes the rest. Pins that
     * haven't changed aren't posted again: Android drops updates from an app that posts too quickly.
     */
    fun syncPins(context: Context, force: Boolean = false) {
        val manager = manager(context)
        val separate = AppSettings.separateNotifications(context)
        val wanted = Store.allItems(context).filter { it.type == ReminderType.STATUSBAR }.associateBy { TAG_PIN + it.key }
        val active = manager.activeNotifications.filter { it.tag?.startsWith(TAG_PIN) == true }
        active.filter { it.tag !in wanted }.forEach { manager.cancel(it.tag, it.id) }
        val showing = active.mapNotNull { it.tag }.toSet()
        val state = context.getSharedPreferences(PIN_STATE_PREFS, Context.MODE_PRIVATE)
        val posted = state.edit().clear()
        wanted.forEach { (tag, item) ->
            val fingerprint = "$separate $item".hashCode().toString()
            if (force || tag !in showing || state.getString(tag, null) != fingerprint) {
                manager.notify(tag, 0, pinNotification(context, item, separate))
            }
            posted.putString(tag, fingerprint)
        }
        posted.apply()
    }

    /** Shows the pins again, removed first so Android works out its bundling from scratch. */
    fun repostPins(context: Context) {
        val manager = manager(context)
        manager.activeNotifications.filter { it.tag?.startsWith(TAG_PIN) == true }.forEach { manager.cancel(it.tag, it.id) }
        syncPins(context, force = true)
    }

    fun showReminder(context: Context, item: Item, occurrence: LocalDateTime, missed: Boolean) {
        val lines = listOfNotNull(
            if (missed) context.getString(R.string.missed_reminder) else null,
            item.subtitle,
            item.preview.ifEmpty { null },
        )
        val builder = Notification.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_bell)
            .setContentTitle(item.title)
            .setContentText(lines.joinToString(" · "))
            .setStyle(Notification.BigTextStyle().bigText(lines.joinToString("\n")))
            .setColor(NoteColors.of(item.color) ?: Notification.COLOR_DEFAULT)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setWhen(occurrence.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
            .setShowWhen(true)
            .setAutoCancel(true)
            .setContentIntent(openNote(context, item))
        if (AppSettings.separateNotifications(context)) builder.setGroup(TAG_REMINDER + item.key)
        manager(context).notify(TAG_REMINDER + item.key, 0, builder.build())
    }

    /**
     * An all-day reminder, pinned for the rest of its day (see [AllDayPins]), with a Remove button. Only the first
     * post alerts; putting it back after a restart or an app update goes through the quiet pins channel.
     */
    fun showAllDay(context: Context, item: Item, alert: Boolean) {
        val lines = listOfNotNull(item.subtitle, item.preview.ifEmpty { null })
        val remove = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, AllDayReceiver::class.java)
                .setAction(AllDayPins.ACTION_REMOVE)
                .setData(Uri.parse("vladmas-note-pin-allday:${Uri.encode(item.key)}"))
                .putExtra(Alarms.EXTRA_KEY, item.key),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = Notification.Builder(context, if (alert) CHANNEL_REMINDERS else CHANNEL_PINS)
            .setSmallIcon(R.drawable.ic_bell)
            .setContentTitle(item.title)
            .setContentText(lines.joinToString(" · "))
            .setStyle(Notification.BigTextStyle().bigText(lines.joinToString("\n")))
            .setColor(NoteColors.of(item.color) ?: Notification.COLOR_DEFAULT)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setContentIntent(openNote(context, item))
            .addAction(Notification.Action.Builder(null, context.getString(R.string.remove), remove).build())
        if (AppSettings.separateNotifications(context)) builder.setGroup(TAG_REMINDER + item.key)
        manager(context).notify(TAG_REMINDER + item.key, 0, builder.build())
    }

    fun cancelReminder(context: Context, key: String) = manager(context).cancel(TAG_REMINDER + key, 0)

    /** The item keys of the reminder notifications showing now. */
    fun activeReminderKeys(context: Context): Set<String> =
        manager(context).activeNotifications.mapNotNull { it.tag }
            .filter { it.startsWith(TAG_REMINDER) }
            .map { it.removePrefix(TAG_REMINDER) }
            .toSet()

    private fun pinNotification(context: Context, item: Item, separate: Boolean): Notification {
        val details = listOfNotNull(item.subtitle, item.preview.ifEmpty { null }).joinToString("\n")
        val builder = Notification.Builder(context, CHANNEL_PINS)
            .setSmallIcon(R.drawable.ic_pin)
            .setContentTitle(item.title)
            .setContentText(item.body)
            .setStyle(Notification.BigTextStyle().bigText(details))
            .setColor(NoteColors.of(item.color) ?: Notification.COLOR_DEFAULT)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setContentIntent(openNote(context, item))
        // Android bundles an app's notifications that have no group once there are several. A group of one
        // per note keeps each pin a separate card.
        if (separate) builder.setGroup(TAG_PIN + item.key)
        return builder.build()
    }

    private fun openNote(context: Context, item: Item): PendingIntent = PendingIntent.getActivity(
        context,
        item.key.hashCode(),
        Obsidian.openNoteIntent(item),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun manager(context: Context) = context.getSystemService(NotificationManager::class.java)
}
