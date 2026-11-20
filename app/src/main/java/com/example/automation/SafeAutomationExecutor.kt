package com.example.automation

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay

data class InstalledAppSummary(
    val label: String,
    val packageName: String
)

data class PermissionStatusSnapshot(
    val microphoneGranted: Boolean,
    val notificationsGranted: Boolean,
    val accessibilityConnected: Boolean,
    val writeSettingsGranted: Boolean,
    val automationMasterEnabled: Boolean
)

/**
 * Executes ONLY allow-listed and validated actions using real Android Intents,
 * System APIs, and the user-enabled SifatAccessibilityService.
 */
class SafeAutomationExecutor(private val context: Context) {

    fun getPermissionSnapshot(automationMasterEnabled: Boolean): PermissionStatusSnapshot {
        val mic = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        val notif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        val accessibility = SifatAccessibilityService.isConnected.value
        val writeSettings = Settings.System.canWrite(context)

        return PermissionStatusSnapshot(
            microphoneGranted = mic,
            notificationsGranted = notif,
            accessibilityConnected = accessibility,
            writeSettingsGranted = writeSettings,
            automationMasterEnabled = automationMasterEnabled
        )
    }

    fun queryLaunchableApps(): List<InstalledAppSummary> {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(
                mainIntent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(mainIntent, 0)
        }

        return resolveInfos
            .mapNotNull { info ->
                val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                if (pkg == context.packageName) return@mapNotNull null
                val label = info.loadLabel(pm)?.toString()?.trim().orEmpty()
                if (label.isBlank()) null else InstalledAppSummary(label, pkg)
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    suspend fun executePlan(
        plan: ActionPlan,
        automationMasterEnabled: Boolean,
        onStepProgress: (index: Int, total: Int, step: ValidatedAction) -> Unit = { _, _, _ -> }
    ): MultiStepExecutionReport {
        val results = mutableListOf<Pair<ValidatedAction, ActionExecutionResult>>()
        for ((idx, step) in plan.steps.withIndex()) {
            onStepProgress(idx + 1, plan.steps.size, step)
            val result = executeAction(step, automationMasterEnabled)
            results.add(step to result)
            if (result !is ActionExecutionResult.Success) {
                return MultiStepExecutionReport(
                    stepResults = results,
                    overallSuccess = false,
                    summaryMessage = when (result) {
                        is ActionExecutionResult.PermissionDenied -> result.message
                        is ActionExecutionResult.Cancelled -> result.message
                        is ActionExecutionResult.Failed -> result.message
                        is ActionExecutionResult.Success -> result.message
                    }
                )
            }
            if (idx < plan.steps.lastIndex) {
                delay(450L)
            }
        }
        val combinedExtra = results.mapNotNull { (it.second as? ActionExecutionResult.Success)?.extraOutput }
            .joinToString("\n\n")
        val finalMsg = if (combinedExtra.isNotBlank()) {
            combinedExtra
        } else {
            results.joinToString(" • ") {
                (it.second as ActionExecutionResult.Success).message
            }
        }
        return MultiStepExecutionReport(
            stepResults = results,
            overallSuccess = true,
            summaryMessage = finalMsg
        )
    }

    fun executeAction(
        action: ValidatedAction,
        automationMasterEnabled: Boolean
    ): ActionExecutionResult {
        if (!automationMasterEnabled && action.actionType != ActionType.OPEN_SETTINGS) {
            return ActionExecutionResult.Cancelled(
                "Automation master switch is turned OFF. Re-enable it in Automation or Settings to execute actions."
            )
        }

        return when (action.actionType) {
            ActionType.LAUNCH_APP -> launchApp(action.parameters["appName"].orEmpty())
            ActionType.OPEN_SETTINGS -> openSettings(action.parameters["section"].orEmpty())
            ActionType.GO_HOME -> requireAccessibility { service ->
                if (service.performGoHome()) {
                    ActionExecutionResult.Success("Navigated to Home screen.")
                } else {
                    ActionExecutionResult.Failed("System refused Home action.")
                }
            }
            ActionType.GO_BACK -> requireAccessibility { service ->
                if (service.performGoBack()) {
                    ActionExecutionResult.Success("Navigated Back.")
                } else {
                    ActionExecutionResult.Failed("System refused Back action.")
                }
            }
            ActionType.READ_SCREEN_SUMMARY -> requireAccessibility { service ->
                val snapshot = service.captureVisibleScreenTree()
                if (snapshot.blockedReason != null) {
                    ActionExecutionResult.PermissionDenied(
                        PermissionRequirement.ACCESSIBILITY_SERVICE,
                        snapshot.blockedReason
                    )
                } else {
                    val summary = buildString {
                        appendLine("Active app: ${snapshot.packageName.ifBlank { "Unknown" }}")
                        if (snapshot.visibleTexts.isNotEmpty()) {
                            appendLine("Visible text: ${snapshot.visibleTexts.take(12).joinToString(" | ")}")
                        } else {
                            appendLine("No readable text labels found on screen.")
                        }
                        if (snapshot.clickableLabels.isNotEmpty()) {
                            appendLine("Interactive buttons: ${snapshot.clickableLabels.take(8).joinToString(", ")}")
                        }
                        if (snapshot.suggestedNextActions.isNotEmpty()) {
                            append("Suggested next: ${snapshot.suggestedNextActions.joinToString("; ")}")
                        }
                    }.trim()
                    ActionExecutionResult.Success(
                        message = "Read active screen successfully.",
                        extraOutput = summary
                    )
                }
            }
            ActionType.CLICK_ELEMENT -> requireAccessibility { service ->
                val target = action.parameters["targetText"].orEmpty()
                service.findAndClickByText(target).fold(
                    onSuccess = { ActionExecutionResult.Success(it) },
                    onFailure = { ActionExecutionResult.Failed(it.message ?: "Failed to click \"$target\".") }
                )
            }
            ActionType.SCROLL_SCREEN -> requireAccessibility { service ->
                val scrollUp = action.parameters["direction"].equals("UP", ignoreCase = true)
                service.scrollVisibleContent(scrollUp).fold(
                    onSuccess = { ActionExecutionResult.Success(it) },
                    onFailure = { ActionExecutionResult.Failed(it.message ?: "Failed to scroll.") }
                )
            }
            ActionType.OPEN_URL -> openUrl(action.parameters["url"].orEmpty())
            ActionType.ADJUST_BRIGHTNESS -> adjustBrightness(
                action.parameters["levelPercent"]?.toIntOrNull() ?: 50
            )
            ActionType.DIAL_NUMBER -> openDialer(action.parameters["phoneNumber"].orEmpty())
            ActionType.COMPOSE_SMS -> openSmsComposer(
                number = action.parameters["phoneNumber"].orEmpty(),
                body = action.parameters["messageBody"].orEmpty()
            )
        }
    }

    private fun requireAccessibility(
        block: (SifatAccessibilityService) -> ActionExecutionResult
    ): ActionExecutionResult {
        val service = SifatAccessibilityService.instance
            ?: return ActionExecutionResult.PermissionDenied(
                PermissionRequirement.ACCESSIBILITY_SERVICE,
                "Sifat Accessibility Service is not enabled. Enable it manually in Android Accessibility Settings."
            )
        return block(service)
    }

    private fun launchApp(requestedName: String): ActionExecutionResult {
        val normalizedQuery = normalizeAppAlias(requestedName)
        val apps = queryLaunchableApps()
        val match = apps.firstOrNull { it.label.equals(normalizedQuery, ignoreCase = true) }
            ?: apps.firstOrNull { it.label.contains(normalizedQuery, ignoreCase = true) }
            ?: apps.firstOrNull { it.packageName.contains(normalizedQuery, ignoreCase = true) }

        if (match == null) {
            return ActionExecutionResult.Failed(
                "Could not find an installed launchable app matching \"$requestedName\"."
            )
        }

        val launchIntent = context.packageManager.getLaunchIntentForPackage(match.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ?: return ActionExecutionResult.Failed("${match.label} does not expose a launch activity.")

        return runCatching {
            context.startActivity(launchIntent)
            ActionExecutionResult.Success("Launched ${match.label}.")
        }.getOrElse { e ->
            ActionExecutionResult.Failed("Failed to launch ${match.label}: ${e.localizedMessage}")
        }
    }

    private fun openSettings(section: String): ActionExecutionResult {
        val action = when (section.lowercase()) {
            "wifi" -> Settings.ACTION_WIFI_SETTINGS
            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "display" -> Settings.ACTION_DISPLAY_SETTINGS
            "sound" -> Settings.ACTION_SOUND_SETTINGS
            "accessibility" -> Settings.ACTION_ACCESSIBILITY_SETTINGS
            "apps" -> Settings.ACTION_APPLICATION_SETTINGS
            "location" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
            "security" -> Settings.ACTION_SECURITY_SETTINGS
            "battery" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }
        val intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(intent)
            ActionExecutionResult.Success("Opened Android Settings ($section).")
        }.getOrElse { e ->
            ActionExecutionResult.Failed("Could not open Settings: ${e.localizedMessage}")
        }
    }

    private fun openUrl(url: String): ActionExecutionResult {
        val safeUrl = ActionValidator.normalizeSafeHttpUrl(url)
            ?: return ActionExecutionResult.Failed("Blocked unsafe or invalid URL.")
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(safeUrl))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(intent)
            ActionExecutionResult.Success("Opened $safeUrl in browser.")
        }.getOrElse { e ->
            ActionExecutionResult.Failed("No browser available to open URL: ${e.localizedMessage}")
        }
    }

    private fun adjustBrightness(levelPercent: Int): ActionExecutionResult {
        if (!Settings.System.canWrite(context)) {
            return ActionExecutionResult.PermissionDenied(
                PermissionRequirement.WRITE_SETTINGS,
                "Modify System Settings permission is required to change display brightness."
            )
        }
        val clampedPercent = levelPercent.coerceIn(5, 100)
        val systemValue = ((clampedPercent / 100f) * 255f).toInt().coerceIn(12, 255)
        return runCatching {
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                systemValue
            )
            ActionExecutionResult.Success("Adjusted screen brightness to $clampedPercent%.")
        }.getOrElse { e ->
            ActionExecutionResult.Failed("Failed to adjust brightness: ${e.localizedMessage}")
        }
    }

    private fun openDialer(phoneNumber: String): ActionExecutionResult {
        val sanitized = ActionValidator.sanitizePhoneNumber(phoneNumber)
            ?: return ActionExecutionResult.Failed("Invalid phone number.")
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$sanitized"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(intent)
            ActionExecutionResult.Success("Opened phone dialer with $sanitized.")
        }.getOrElse { e ->
            ActionExecutionResult.Failed("No dialer app available: ${e.localizedMessage}")
        }
    }

    private fun openSmsComposer(number: String, body: String): ActionExecutionResult {
        val sanitized = ActionValidator.sanitizePhoneNumber(number)
            ?: return ActionExecutionResult.Failed("Invalid recipient phone number.")
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$sanitized")).apply {
            putExtra("sms_body", body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(intent)
            ActionExecutionResult.Success("Opened SMS composer for $sanitized.")
        }.getOrElse { e ->
            ActionExecutionResult.Failed("No SMS app available: ${e.localizedMessage}")
        }
    }

    private fun normalizeAppAlias(raw: String): String {
        val trimmed = raw.trim()
        return when (trimmed.lowercase()) {
            "ইউটিউব" -> "YouTube"
            "সেটিংস" -> "Settings"
            "ক্যামেরা" -> "Camera"
            "ক্রোম" -> "Chrome"
            "হোয়াটসঅ্যাপ", "হোয়াটসঅ্যাপ" -> "WhatsApp"
            "ক্যালকুলেটর" -> "Calculator"
            "ম্যাপস", "গুগল ম্যাপস" -> "Maps"
            "জিমেইল" -> "Gmail"
            "প্লে স্টোর" -> "Play Store"
            else -> trimmed
        }
    }

    fun openAccessibilitySettingsIntent(): Intent =
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun openWriteSettingsIntent(): Intent =
        Intent(
            Settings.ACTION_MANAGE_WRITE_SETTINGS,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
