package com.example.automation

enum class RiskLevel(val label: String) {
    LOW("Low Risk"),
    MEDIUM("Medium Risk"),
    HIGH("High Risk")
}

enum class PermissionRequirement(val label: String) {
    NONE("No special permission"),
    ACCESSIBILITY_SERVICE("Accessibility Service"),
    WRITE_SETTINGS("Modify System Settings"),
    USER_CONFIRMATION_PREVIEW("Explicit User Preview & Confirmation")
}

enum class ActionType(
    val wireName: String,
    val displayName: String,
    val description: String,
    val permissionRequirement: PermissionRequirement,
    val riskLevel: RiskLevel,
    val alwaysConfirm: Boolean
) {
    LAUNCH_APP(
        wireName = "LAUNCH_APP",
        displayName = "Launch Installed App",
        description = "Opens an installed application using Android launcher intents.",
        permissionRequirement = PermissionRequirement.NONE,
        riskLevel = RiskLevel.LOW,
        alwaysConfirm = false
    ),
    OPEN_SETTINGS(
        wireName = "OPEN_SETTINGS",
        displayName = "Open Android Settings",
        description = "Opens a specific Android Settings page (Wi-Fi, Bluetooth, Display, Accessibility, etc.).",
        permissionRequirement = PermissionRequirement.NONE,
        riskLevel = RiskLevel.LOW,
        alwaysConfirm = false
    ),
    GO_HOME(
        wireName = "GO_HOME",
        displayName = "Go Home",
        description = "Returns to the Android Home screen via AccessibilityService.",
        permissionRequirement = PermissionRequirement.ACCESSIBILITY_SERVICE,
        riskLevel = RiskLevel.LOW,
        alwaysConfirm = false
    ),
    GO_BACK(
        wireName = "GO_BACK",
        displayName = "Go Back",
        description = "Navigates back one step via AccessibilityService.",
        permissionRequirement = PermissionRequirement.ACCESSIBILITY_SERVICE,
        riskLevel = RiskLevel.LOW,
        alwaysConfirm = false
    ),
    READ_SCREEN_SUMMARY(
        wireName = "READ_SCREEN_SUMMARY",
        displayName = "Read Visible Screen",
        description = "Reads and summarizes non-sensitive visible text and buttons on the active screen.",
        permissionRequirement = PermissionRequirement.ACCESSIBILITY_SERVICE,
        riskLevel = RiskLevel.MEDIUM,
        alwaysConfirm = true
    ),
    CLICK_ELEMENT(
        wireName = "CLICK_ELEMENT",
        displayName = "Click Visible Element",
        description = "Finds and taps a visible UI button or label via AccessibilityService.",
        permissionRequirement = PermissionRequirement.ACCESSIBILITY_SERVICE,
        riskLevel = RiskLevel.MEDIUM,
        alwaysConfirm = true
    ),
    SCROLL_SCREEN(
        wireName = "SCROLL_SCREEN",
        displayName = "Scroll Visible Content",
        description = "Scrolls the active scrollable container up or down.",
        permissionRequirement = PermissionRequirement.ACCESSIBILITY_SERVICE,
        riskLevel = RiskLevel.LOW,
        alwaysConfirm = false
    ),
    OPEN_URL(
        wireName = "OPEN_URL",
        displayName = "Open HTTPS/HTTP URL",
        description = "Opens a validated web link in the default browser.",
        permissionRequirement = PermissionRequirement.USER_CONFIRMATION_PREVIEW,
        riskLevel = RiskLevel.MEDIUM,
        alwaysConfirm = true
    ),
    ADJUST_BRIGHTNESS(
        wireName = "ADJUST_BRIGHTNESS",
        displayName = "Adjust Screen Brightness",
        description = "Adjusts system display brightness when Modify System Settings is granted.",
        permissionRequirement = PermissionRequirement.WRITE_SETTINGS,
        riskLevel = RiskLevel.MEDIUM,
        alwaysConfirm = true
    ),
    DIAL_NUMBER(
        wireName = "DIAL_NUMBER",
        displayName = "Dial Phone Number",
        description = "Opens the phone dialer with a previewed number (never places call silently).",
        permissionRequirement = PermissionRequirement.USER_CONFIRMATION_PREVIEW,
        riskLevel = RiskLevel.HIGH,
        alwaysConfirm = true
    ),
    COMPOSE_SMS(
        wireName = "COMPOSE_SMS",
        displayName = "Compose SMS Message",
        description = "Opens the SMS app with a previewed recipient and message body.",
        permissionRequirement = PermissionRequirement.USER_CONFIRMATION_PREVIEW,
        riskLevel = RiskLevel.HIGH,
        alwaysConfirm = true
    );

    companion object {
        fun fromWireName(raw: String): ActionType? =
            entries.firstOrNull { it.wireName.equals(raw.trim(), ignoreCase = true) }
    }
}

data class ValidatedAction(
    val actionType: ActionType,
    val parameters: Map<String, String>,
    val previewSummary: String,
    val requiresConfirmation: Boolean,
    val riskLevel: RiskLevel = actionType.riskLevel,
    val permissionRequirement: PermissionRequirement = actionType.permissionRequirement
)

data class ActionPlan(
    val summary: String,
    val language: String,
    val steps: List<ValidatedAction>
) {
    val requiresConfirmation: Boolean
        get() = steps.any { it.requiresConfirmation } || steps.size > 1
}

sealed interface ActionExecutionResult {
    data class Success(val message: String, val extraOutput: String? = null) : ActionExecutionResult
    data class PermissionDenied(val requirement: PermissionRequirement, val message: String) : ActionExecutionResult
    data class Cancelled(val message: String) : ActionExecutionResult
    data class Failed(val message: String) : ActionExecutionResult
}

data class MultiStepExecutionReport(
    val stepResults: List<Pair<ValidatedAction, ActionExecutionResult>>,
    val overallSuccess: Boolean,
    val summaryMessage: String
)

sealed interface ParsedAiPlan {
    data class Conversation(
        val replyText: String,
        val language: String
    ) : ParsedAiPlan

    data class Clarification(
        val question: String,
        val language: String
    ) : ParsedAiPlan

    data class StructuredActionPlan(
        val replyText: String,
        val language: String,
        val plan: ActionPlan
    ) : ParsedAiPlan

    data class BlockedOrInvalid(
        val reason: String,
        val language: String
    ) : ParsedAiPlan
}
