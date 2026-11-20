package com.example.automation

import org.json.JSONArray
import org.json.JSONObject

/**
 * Validates and converts Gemini JSON output into strictly allow-listed,
 * parameter-sanitized Android automation plans.
 * Rejects arbitrary code execution, financial transactions, file deletions,
 * security/lock-screen bypasses, and malformed parameters.
 */
object ActionValidator {

    private val forbiddenIntentKeywords = listOf(
        "transfer money", "send money", "bkash", "nagad", "rocket", "bank transfer",
        "upi", "pay ", "payment", "checkout", "delete all files", "wipe device",
        "factory reset", "bypass lock", "unlock screen", "grant permission automatically",
        "root", "shell command", "rm -rf", "adb ", "eval("
    )

    private val allowedSettingsSections = setOf(
        "general", "wifi", "bluetooth", "display", "sound", "accessibility",
        "apps", "location", "security", "battery"
    )

    fun isPromptForbidden(userPrompt: String): Boolean {
        val lower = userPrompt.lowercase()
        return forbiddenIntentKeywords.any { lower.contains(it) }
    }

    fun parseStructuredPlanJson(rawJson: String): ParsedAiPlan {
        return try {
            val root = JSONObject(rawJson)
            val intentType = root.optString("intentType", "CONVERSATION").uppercase()
            val replyText = root.optString("replyText", "").trim()
            val language = root.optString("language", "en").lowercase()

            when (intentType) {
                "CONVERSATION" -> {
                    val text = replyText.ifBlank { "I am ready to help." }
                    ParsedAiPlan.Conversation(replyText = text, language = language)
                }

                "CLARIFICATION" -> {
                    val question = replyText.ifBlank {
                        if (language.startsWith("bn")) "আপনি ঠিক কী করতে চান তা আরেকটু স্পষ্ট করে বলবেন?"
                        else "Could you clarify what you would like me to do?"
                    }
                    ParsedAiPlan.Clarification(question = question, language = language)
                }

                "ACTION_PLAN" -> {
                    val summary = root.optString("summary", replyText).trim()
                    val stepsArray = root.optJSONArray("steps") ?: JSONArray()
                    if (stepsArray.length() == 0) {
                        return ParsedAiPlan.Clarification(
                            question = replyText.ifBlank { "Please specify the action you want to perform." },
                            language = language
                        )
                    }

                    val validatedSteps = mutableListOf<ValidatedAction>()
                    for (i in 0 until stepsArray.length()) {
                        val stepObj = stepsArray.optJSONObject(i) ?: continue
                        val validated = validateSingleStep(stepObj)
                            ?: return ParsedAiPlan.BlockedOrInvalid(
                                reason = "Blocked unsupported or unsafe action in step ${i + 1}. Only allow-listed Android actions are permitted.",
                                language = language
                            )
                        validatedSteps.add(validated)
                    }

                    if (validatedSteps.isEmpty()) {
                        return ParsedAiPlan.BlockedOrInvalid(
                            reason = "No valid allow-listed actions were produced.",
                            language = language
                        )
                    }

                    ParsedAiPlan.StructuredActionPlan(
                        replyText = replyText.ifBlank { summary },
                        language = language,
                        plan = ActionPlan(
                            summary = summary.ifBlank { "Execute ${validatedSteps.size} safe action(s)" },
                            language = language,
                            steps = validatedSteps
                        )
                    )
                }

                "BLOCKED" -> {
                    ParsedAiPlan.BlockedOrInvalid(
                        reason = replyText.ifBlank { "This request is blocked by Sifat Agent safety policy." },
                        language = language
                    )
                }

                else -> ParsedAiPlan.Conversation(
                    replyText = replyText.ifBlank { rawJson },
                    language = language
                )
            }
        } catch (e: Exception) {
            ParsedAiPlan.BlockedOrInvalid(
                reason = "Could not parse structured AI response safely: ${e.localizedMessage ?: "invalid JSON"}",
                language = "en"
            )
        }
    }

    fun validateSingleStep(stepObj: JSONObject): ValidatedAction? {
        val actionWire = stepObj.optString("action", "").trim()
        val actionType = ActionType.fromWireName(actionWire) ?: return null
        val paramsJson = stepObj.optJSONObject("parameters") ?: JSONObject()

        return when (actionType) {
            ActionType.LAUNCH_APP -> {
                val appName = paramsJson.optString("appName", "").trim()
                if (appName.isBlank() || appName.length > 80) return null
                ValidatedAction(
                    actionType = actionType,
                    parameters = mapOf("appName" to appName),
                    previewSummary = "Launch app: $appName",
                    requiresConfirmation = actionType.alwaysConfirm
                )
            }

            ActionType.OPEN_SETTINGS -> {
                val rawSection = paramsJson.optString("section", "general").trim().lowercase()
                val section = if (rawSection in allowedSettingsSections) rawSection else "general"
                ValidatedAction(
                    actionType = actionType,
                    parameters = mapOf("section" to section),
                    previewSummary = "Open Android Settings ($section)",
                    requiresConfirmation = actionType.alwaysConfirm
                )
            }

            ActionType.GO_HOME -> ValidatedAction(
                actionType = actionType,
                parameters = emptyMap(),
                previewSummary = "Navigate to Home screen",
                requiresConfirmation = actionType.alwaysConfirm
            )

            ActionType.GO_BACK -> ValidatedAction(
                actionType = actionType,
                parameters = emptyMap(),
                previewSummary = "Navigate Back",
                requiresConfirmation = actionType.alwaysConfirm
            )

            ActionType.READ_SCREEN_SUMMARY -> ValidatedAction(
                actionType = actionType,
                parameters = emptyMap(),
                previewSummary = "Read and summarize active visible screen content",
                requiresConfirmation = true
            )

            ActionType.CLICK_ELEMENT -> {
                val targetText = paramsJson.optString("targetText", "").trim()
                if (targetText.isBlank() || targetText.length > 100) return null
                ValidatedAction(
                    actionType = actionType,
                    parameters = mapOf("targetText" to targetText),
                    previewSummary = "Tap visible UI element matching \"$targetText\"",
                    requiresConfirmation = true
                )
            }

            ActionType.SCROLL_SCREEN -> {
                val rawDir = paramsJson.optString("direction", "DOWN").trim().uppercase()
                val dir = if (rawDir == "UP") "UP" else "DOWN"
                ValidatedAction(
                    actionType = actionType,
                    parameters = mapOf("direction" to dir),
                    previewSummary = "Scroll visible screen $dir",
                    requiresConfirmation = actionType.alwaysConfirm
                )
            }

            ActionType.OPEN_URL -> {
                val rawUrl = paramsJson.optString("url", "").trim()
                val normalizedUrl = normalizeSafeHttpUrl(rawUrl) ?: return null
                ValidatedAction(
                    actionType = actionType,
                    parameters = mapOf("url" to normalizedUrl),
                    previewSummary = "Open URL in browser: $normalizedUrl",
                    requiresConfirmation = true
                )
            }

            ActionType.ADJUST_BRIGHTNESS -> {
                val rawLevel = paramsJson.optString("levelPercent", "").trim()
                val level = rawLevel.toIntOrNull() ?: paramsJson.optInt("levelPercent", -1)
                if (level !in 5..100) return null
                ValidatedAction(
                    actionType = actionType,
                    parameters = mapOf("levelPercent" to level.toString()),
                    previewSummary = "Set screen brightness to $level%",
                    requiresConfirmation = true
                )
            }

            ActionType.DIAL_NUMBER -> {
                val rawNumber = paramsJson.optString("phoneNumber", "").trim()
                val sanitized = sanitizePhoneNumber(rawNumber) ?: return null
                val label = paramsJson.optString("contactLabel", "").trim()
                val params = buildMap {
                    put("phoneNumber", sanitized)
                    if (label.isNotBlank()) put("contactLabel", label)
                }
                val displayTarget = if (label.isNotBlank()) "$label ($sanitized)" else sanitized
                ValidatedAction(
                    actionType = actionType,
                    parameters = params,
                    previewSummary = "Preview dialer for: $displayTarget",
                    requiresConfirmation = true
                )
            }

            ActionType.COMPOSE_SMS -> {
                val rawNumber = paramsJson.optString("phoneNumber", "").trim()
                val sanitized = sanitizePhoneNumber(rawNumber) ?: return null
                val body = paramsJson.optString("messageBody", "").trim()
                if (body.isBlank() || body.length > 500) return null
                ValidatedAction(
                    actionType = actionType,
                    parameters = mapOf(
                        "phoneNumber" to sanitized,
                        "messageBody" to body
                    ),
                    previewSummary = "Compose SMS to $sanitized: \"$body\"",
                    requiresConfirmation = true
                )
            }
        }
    }

    fun normalizeSafeHttpUrl(rawUrl: String): String? {
        if (rawUrl.isBlank() || rawUrl.contains(" ")) return null
        val lower = rawUrl.lowercase()
        if (lower.startsWith("javascript:") || lower.startsWith("file:") ||
            lower.startsWith("content:") || lower.startsWith("intent:") ||
            lower.startsWith("data:")
        ) {
            return null
        }
        val withScheme = when {
            lower.startsWith("https://") || lower.startsWith("http://") -> rawUrl
            rawUrl.contains(".") -> "https://$rawUrl"
            else -> return null
        }
        return if (withScheme.length <= 512) withScheme else null
    }

    fun sanitizePhoneNumber(raw: String): String? {
        val cleaned = raw.filter { it.isDigit() || it == '+' }
        val digitCount = cleaned.count { it.isDigit() }
        return if (digitCount in 3..16) cleaned else null
    }

    fun serializePlanToJson(plan: ActionPlan): String {
        val root = JSONObject()
        root.put("summary", plan.summary)
        root.put("language", plan.language)
        val stepsArray = JSONArray()
        plan.steps.forEach { step ->
            val obj = JSONObject()
            obj.put("action", step.actionType.wireName)
            obj.put("previewSummary", step.previewSummary)
            obj.put("requiresConfirmation", step.requiresConfirmation)
            val params = JSONObject()
            step.parameters.forEach { (k, v) -> params.put(k, v) }
            obj.put("parameters", params)
            stepsArray.put(obj)
        }
        root.put("steps", stepsArray)
        return root.toString()
    }

    fun deserializePlanFromJson(raw: String): ActionPlan? {
        return try {
            val root = JSONObject(raw)
            val summary = root.optString("summary", "")
            val language = root.optString("language", "en")
            val arr = root.optJSONArray("steps") ?: return null
            val steps = mutableListOf<ValidatedAction>()
            for (i in 0 until arr.length()) {
                val stepObj = arr.optJSONObject(i) ?: continue
                validateSingleStep(stepObj)?.let { steps.add(it) }
            }
            if (steps.isEmpty()) null else ActionPlan(summary, language, steps)
        } catch (_: Exception) {
            null
        }
    }
}
