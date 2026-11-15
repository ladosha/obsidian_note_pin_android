package com.vladmas.notepinobsidian

import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings

/**
 * The phone's maker and system. Some makers add their own battery and startup limits on top of Android's,
 * and those decide whether pins come back after a restart or an app update.
 */
data class Phone(val maker: Maker, val name: String, val system: String?, val oneUi: Double? = null) {
    enum class Maker { XIAOMI, SAMSUNG, HUAWEI, HONOR, OPPO, ONEPLUS, REALME, VIVO, OTHER }

    /** The name and system on one line, for example "POCO F7 · HyperOS 2 · Android 15". */
    val summary get() = listOfNotNull(name, system, "Android ${Build.VERSION.RELEASE}").distinct().joinToString(" · ")

    companion object {
        fun detect(context: Context): Phone {
            val maker = when (Build.MANUFACTURER.lowercase()) {
                "xiaomi" -> Maker.XIAOMI
                "samsung" -> Maker.SAMSUNG
                "huawei" -> Maker.HUAWEI
                "honor" -> Maker.HONOR
                "oppo" -> Maker.OPPO
                "oneplus" -> Maker.ONEPLUS
                "realme" -> Maker.REALME
                "vivo" -> Maker.VIVO
                else -> Maker.OTHER
            }
            val oneUi = if (maker == Maker.SAMSUNG) oneUiVersion() else null
            val system = when (maker) {
                Maker.XIAOMI -> xiaomiSystem(Build.VERSION.INCREMENTAL)
                Maker.SAMSUNG -> oneUi?.let { "One UI ${format(it)}" } ?: "One UI"
                Maker.HUAWEI -> if (prop("hw_sc.build.platform.version") != null) "HarmonyOS" else "EMUI"
                Maker.HONOR -> "MagicOS"
                Maker.OPPO -> "ColorOS"
                Maker.ONEPLUS -> "OxygenOS"
                Maker.REALME -> "realme UI"
                Maker.VIVO -> prop("ro.vivo.os.name")
                Maker.OTHER -> null
            }
            return Phone(maker, deviceName(context), system, oneUi)
        }

        /** Xiaomi's Autostart switch, stored as app-op 10008. Null on other phones, or when the system won't say. */
        fun xiaomiAutostart(context: Context): Boolean? = try {
            val check = AppOpsManager::class.java.getMethod(
                "checkOpNoThrow", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, String::class.java,
            )
            val mode = check.invoke(context.getSystemService(AppOpsManager::class.java), 10008, Process.myUid(), context.packageName)
            mode == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            null
        }

        /** Xiaomi's Autostart list, on phones that have it. */
        fun xiaomiAutostartIntent(context: Context): Intent? = componentIntent(
            context, "com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity",
        )

        /** Huawei's and Honor's App launch list, on phones that have it. */
        fun appLaunchIntent(context: Context): Intent? =
            componentIntent(context, "com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")
                ?: componentIntent(context, "com.hihonor.systemmanager", "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity")

        fun appInfoIntent(context: Context): Intent =
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))

        private fun componentIntent(context: Context, pkg: String, activity: String): Intent? {
            val intent = Intent().setComponent(ComponentName(pkg, activity))
            return intent.takeIf { context.packageManager.resolveActivity(it, 0) != null }
        }

        /** The name people know the phone by: "POCO F7", not "25053PC47G". */
        private fun deviceName(context: Context): String = displayName(
            marketName = prop("ro.product.marketname"),
            stockName = Settings.Global.getString(context.contentResolver, "default_device_name"),
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
        )

        /**
         * The marketing name if the phone has one (Xiaomi), else Samsung's stock name ("Galaxy S24 Ultra", from
         * default_device_name), else the maker and model. Settings.Global.DEVICE_NAME isn't used: it's the name the
         * owner can edit, which Samsung fills in as "<owner's first name>'s S24 Ultra" in the setup language.
         */
        internal fun displayName(marketName: String?, stockName: String?, manufacturer: String, model: String): String {
            marketName?.takeIf { it.isNotBlank() }?.let { return it }
            stockName?.takeIf { it.isNotBlank() }?.let { return it }
            val maker = manufacturer.replaceFirstChar { it.uppercase() }
            return if (model.startsWith(maker, ignoreCase = true)) model else "$maker $model"
        }

        /** HyperOS builds are numbered "OS2.0.201.0…", MIUI builds "V14.0.4.0…". */
        internal fun xiaomiSystem(build: String): String {
            Regex("""^OS(\d+)\.(\d+)""").find(build)?.let { return "HyperOS ${version(it)}" }
            Regex("""^V(\d+)\.(\d+)""").find(build)?.let { return "MIUI ${version(it)}" }
            return "MIUI"
        }

        private fun version(match: MatchResult): String {
            val (major, minor) = match.destructured
            return if (minor == "0") major else "$major.$minor"
        }

        /** One UI 6.1 reports itself as 60100, through a system property or, on older phones, a Samsung-only field. */
        private fun oneUiVersion(): Double? {
            val code = prop("ro.build.version.oneui")?.toIntOrNull()
                ?: try {
                    Build.VERSION::class.java.getField("SEM_PLATFORM_INT").getInt(null) - 90000
                } catch (e: Exception) {
                    null
                }
            return code?.let(::oneUi)
        }

        internal fun oneUi(code: Int): Double? = code.takeIf { it > 0 }?.let { it / 10000 + (it / 100 % 100) / 10.0 }

        private fun format(version: Double) = if (version % 1.0 == 0.0) version.toInt().toString() else version.toString()

        @SuppressLint("PrivateApi")
        private fun prop(key: String): String? = try {
            (Class.forName("android.os.SystemProperties").getMethod("get", String::class.java).invoke(null, key) as String)
                .ifBlank { null }
        } catch (e: Exception) {
            null
        }
    }
}
