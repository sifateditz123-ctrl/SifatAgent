package com.example.automation

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ScreenSnapshot(
    val packageName: String,
    val visibleTexts: List<String>,
    val clickableLabels: List<String>,
    val editableFields: List<String>,
    val suggestedNextActions: List<String>,
    val blockedReason: String? = null
)

/**
 * User-enabled AccessibilityService for Sifat Agent.
 * Never auto-enabled, respects master switch, skips password fields,
 * lock screens, permission dialogs, and sensitive financial/auth packages.
 */
class SifatAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isConnected.value = true
        Log.i(TAG, "SifatAccessibilityService connected by user.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Deliberately passive: never scrapes or uploads screen content in the background.
        // Screen tree is read ONLY when the user explicitly requests READ_SCREEN_SUMMARY or CLICK_ELEMENT.
    }

    override fun onInterrupt() {
        Log.w(TAG, "SifatAccessibilityService interrupted.")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance === this) {
            instance = null
        }
        _isConnected.value = false
    }

    fun performGoHome(): Boolean {
        if (!masterAutomationEnabled) return false
        return performGlobalAction(GLOBAL_ACTION_HOME)
    }

    fun performGoBack(): Boolean {
        if (!masterAutomationEnabled) return false
        return performGlobalAction(GLOBAL_ACTION_BACK)
    }

    fun captureVisibleScreenTree(): ScreenSnapshot {
        if (!masterAutomationEnabled) {
            return ScreenSnapshot(
                packageName = "",
                visibleTexts = emptyList(),
                clickableLabels = emptyList(),
                editableFields = emptyList(),
                suggestedNextActions = emptyList(),
                blockedReason = "Automation master switch is currently disabled in Settings / Automation."
            )
        }

        val root = rootInActiveWindow ?: return ScreenSnapshot(
            packageName = "",
            visibleTexts = emptyList(),
            clickableLabels = emptyList(),
            editableFields = emptyList(),
            suggestedNextActions = emptyList(),
            blockedReason = "No active window is accessible right now."
        )

        val pkg = root.packageName?.toString().orEmpty()
        if (isSensitivePackageOrScreen(pkg, root)) {
            return ScreenSnapshot(
                packageName = pkg,
                visibleTexts = emptyList(),
                clickableLabels = emptyList(),
                editableFields = emptyList(),
                suggestedNextActions = emptyList(),
                blockedReason = "Blocked for privacy & security: Sifat Agent does not inspect lock screens, permission dialogs, password fields, or banking/authentication apps."
            )
        }

        val visibleTexts = linkedSetOf<String>()
        val clickableLabels = linkedSetOf<String>()
        val editableFields = linkedSetOf<String>()

        traverseNode(root, visibleTexts, clickableLabels, editableFields, depth = 0)

        val suggestions = buildList {
            clickableLabels.take(3).forEach { label ->
                add("Tap \"$label\"")
            }
            if (visibleTexts.size > 5) {
                add("Scroll down to see more content")
            }
        }

        return ScreenSnapshot(
            packageName = pkg,
            visibleTexts = visibleTexts.take(30),
            clickableLabels = clickableLabels.take(15),
            editableFields = editableFields.take(10),
            suggestedNextActions = suggestions
        )
    }

    fun findAndClickByText(query: String): Result<String> {
        if (!masterAutomationEnabled) {
            return Result.failure(IllegalStateException("Automation master switch is disabled."))
        }
        val root = rootInActiveWindow
            ?: return Result.failure(IllegalStateException("No active screen window available."))
        val pkg = root.packageName?.toString().orEmpty()
        if (isSensitivePackageOrScreen(pkg, root)) {
            return Result.failure(SecurityException("Interaction blocked on protected security/banking/permission screen."))
        }

        val targetNode = findMatchingClickableNode(root, query.trim())
            ?: return Result.failure(NoSuchElementException("Could not find visible element matching \"$query\"."))

        val clicked = targetNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        return if (clicked) {
            Result.success("Clicked visible element \"$query\".")
        } else {
            Result.failure(IllegalStateException("Element \"$query\" refused accessibility click."))
        }
    }

    fun scrollVisibleContent(scrollUp: Boolean): Result<String> {
        if (!masterAutomationEnabled) {
            return Result.failure(IllegalStateException("Automation master switch is disabled."))
        }
        val root = rootInActiveWindow
            ?: return Result.failure(IllegalStateException("No active screen window available."))
        val pkg = root.packageName?.toString().orEmpty()
        if (isSensitivePackageOrScreen(pkg, root)) {
            return Result.failure(SecurityException("Scrolling blocked on protected security/banking screen."))
        }

        val scrollable = findScrollableNode(root)
            ?: return Result.failure(NoSuchElementException("No scrollable container found on active screen."))

        val action = if (scrollUp) {
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        } else {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        }
        val scrolled = scrollable.performAction(action)
        return if (scrolled) {
            Result.success(if (scrollUp) "Scrolled up." else "Scrolled down.")
        } else {
            Result.failure(IllegalStateException("Reached end of scrollable content."))
        }
    }

    private fun traverseNode(
        node: AccessibilityNodeInfo?,
        visibleTexts: MutableSet<String>,
        clickableLabels: MutableSet<String>,
        editableFields: MutableSet<String>,
        depth: Int
    ) {
        if (node == null || depth > 24 || !node.isVisibleToUser) return

        // Never read password inputs or hidden secure fields
        if (node.isPassword) return

        val label = node.text?.toString()?.trim()?.takeIf { it.isNotBlank() }
            ?: node.contentDescription?.toString()?.trim()?.takeIf { it.isNotBlank() }

        if (label != null && label.length <= 160) {
            if (node.isEditable) {
                editableFields.add(label)
            } else if (node.isClickable) {
                clickableLabels.add(label)
                visibleTexts.add(label)
            } else {
                visibleTexts.add(label)
            }
        }

        for (i in 0 until node.childCount) {
            traverseNode(node.getChild(i), visibleTexts, clickableLabels, editableFields, depth + 1)
        }
    }

    private fun findMatchingClickableNode(node: AccessibilityNodeInfo?, query: String): AccessibilityNodeInfo? {
        if (node == null || !node.isVisibleToUser || node.isPassword) return null

        val text = node.text?.toString().orEmpty()
        val desc = node.contentDescription?.toString().orEmpty()
        val matches = text.contains(query, ignoreCase = true) || desc.contains(query, ignoreCase = true)

        if (matches) {
            if (node.isClickable) return node
            var parent = node.parent
            var hops = 0
            while (parent != null && hops < 4) {
                if (parent.isClickable && !parent.isPassword) return parent
                parent = parent.parent
                hops++
            }
        }

        for (i in 0 until node.childCount) {
            findMatchingClickableNode(node.getChild(i), query)?.let { return it }
        }
        return null
    }

    private fun findScrollableNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null || !node.isVisibleToUser) return null
        if (node.isScrollable) return node
        for (i in 0 until node.childCount) {
            findScrollableNode(node.getChild(i))?.let { return it }
        }
        return null
    }

    private fun isSensitivePackageOrScreen(pkg: String, root: AccessibilityNodeInfo): Boolean {
        val lowerPkg = pkg.lowercase()
        if (SENSITIVE_PACKAGES.any { lowerPkg.contains(it) }) return true
        // Skip systemui lock screen or security dialogs
        if (lowerPkg == "com.android.systemui" && containsPasswordOrKeyguard(root, 0)) return true
        return false
    }

    private fun containsPasswordOrKeyguard(node: AccessibilityNodeInfo?, depth: Int): Boolean {
        if (node == null || depth > 8) return false
        if (node.isPassword) return true
        val viewId = node.viewIdResourceName?.lowercase().orEmpty()
        if (viewId.contains("keyguard") || viewId.contains("lock") || viewId.contains("pin")) return true
        for (i in 0 until node.childCount) {
            if (containsPasswordOrKeyguard(node.getChild(i), depth + 1)) return true
        }
        return false
    }

    companion object {
        private const val TAG = "SifatAccessibility"

        @Volatile
        var instance: SifatAccessibilityService? = null
            private set

        @Volatile
        var masterAutomationEnabled: Boolean = true

        private val _isConnected = MutableStateFlow(false)
        val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

        private val SENSITIVE_PACKAGES = setOf(
            "permissioncontroller",
            "packageinstaller",
            "biometrics",
            "com.bkash",
            "com.konasl.nagad",
            "com.dbbl.rocket",
            "paypal",
            "wallet",
            "authenticator",
            "1password",
            "bitwarden",
            "keepass",
            "banking"
        )
    }
}
