package com.example.ai

import android.util.Log
import com.example.automation.ActionValidator
import com.example.automation.ParsedAiPlan
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout

/**
 * Default Gemini engine backed by Firebase AI Logic + App Check.
 * Zero API keys are packaged in the APK.
 */
class FirebaseAiEngine : AiEngine {

    private val actionStepParametersSchema = Schema.obj(
        properties = mapOf(
            "appName" to Schema.string(description = "Target installed app name for LAUNCH_APP"),
            "section" to Schema.string(description = "Settings section: general, wifi, bluetooth, display, sound, accessibility, apps, location, security, battery"),
            "targetText" to Schema.string(description = "Visible button or label text for CLICK_ELEMENT"),
            "direction" to Schema.string(description = "Scroll direction: UP or DOWN"),
            "url" to Schema.string(description = "HTTPS or HTTP URL for OPEN_URL"),
            "levelPercent" to Schema.string(description = "Brightness percentage 5 to 100 for ADJUST_BRIGHTNESS"),
            "phoneNumber" to Schema.string(description = "Phone number digits/+ for DIAL_NUMBER or COMPOSE_SMS"),
            "contactLabel" to Schema.string(description = "Optional contact name label for DIAL_NUMBER"),
            "messageBody" to Schema.string(description = "SMS message text for COMPOSE_SMS")
        ),
        optionalProperties = listOf(
            "appName",
            "section",
            "targetText",
            "direction",
            "url",
            "levelPercent",
            "phoneNumber",
            "contactLabel",
            "messageBody"
        )
    )

    private val actionPlanSchema = Schema.obj(
        properties = mapOf(
            "intentType" to Schema.enumeration(
                values = listOf("CONVERSATION", "ACTION_PLAN", "CLARIFICATION", "BLOCKED"),
                description = "Whether the request is normal chat, a phone automation plan, needs clarification, or is blocked"
            ),
            "language" to Schema.enumeration(
                values = listOf("en", "bn"),
                description = "Primary response language code"
            ),
            "replyText" to Schema.string(
                description = "Natural bilingual reply, clarification question, or explanation to display and speak"
            ),
            "summary" to Schema.string(
                description = "Short 1-line summary of the action plan if intentType is ACTION_PLAN"
            ),
            "steps" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "action" to Schema.enumeration(
                            values = listOf(
                                "LAUNCH_APP",
                                "OPEN_SETTINGS",
                                "GO_HOME",
                                "GO_BACK",
                                "READ_SCREEN_SUMMARY",
                                "CLICK_ELEMENT",
                                "SCROLL_SCREEN",
                                "OPEN_URL",
                                "ADJUST_BRIGHTNESS",
                                "DIAL_NUMBER",
                                "COMPOSE_SMS"
                            ),
                            description = "Allow-listed action identifier"
                        ),
                        "parameters" to actionStepParametersSchema
                    ),
                    optionalProperties = listOf("parameters")
                ),
                description = "Ordered allow-listed action steps when intentType is ACTION_PLAN"
            )
        ),
        optionalProperties = listOf("summary", "steps")
    )

    private val plannerModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                temperature = 0.2f
                responseMimeType = "application/json"
                responseSchema = actionPlanSchema
            }
        )
    }

    private val chatStreamModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                temperature = 0.6f
            }
        )
    }

    override suspend fun processPrompt(request: AiRequestContext): ParsedAiPlan {
        if (ActionValidator.isPromptForbidden(request.userPrompt)) {
            return ParsedAiPlan.BlockedOrInvalid(
                reason = "Blocked by Sifat Agent safety policy: financial transactions, file wiping, and security/lock-screen bypasses are prohibited.",
                language = if (request.userPrompt.any { it in '\u0980'..'\u09FF' }) "bn" else "en"
            )
        }

        val systemInstruction = SifatSystemPrompts.buildPlannerInstruction(
            language = request.preferredLanguage,
            screenSummary = request.screenSummary
        )

        val promptContent = buildString {
            appendLine(systemInstruction)
            if (request.conversationHistory.isNotEmpty()) {
                appendLine("\nRecent conversation:")
                request.conversationHistory.takeLast(6).forEach { (role, msg) ->
                    appendLine("$role: $msg")
                }
            }
            appendLine("\nCurrent user input: ${request.userPrompt}")
        }

        val response = withTimeout(60_000L) {
            plannerModel.generateContent(promptContent)
        }
        val rawJson = response.text
            ?: throw IllegalStateException("Gemini returned an empty response.")
        return ActionValidator.parseStructuredPlanJson(rawJson)
    }

    override fun streamConversation(request: AiRequestContext): Flow<AiStreamUpdate> = flow {
        emit(AiStreamUpdate.Thinking)
        try {
            val parsed = processPrompt(request)
            if (parsed is ParsedAiPlan.Conversation) {
                // Stream rich conversational output for fast TTFT when no device action is needed
                val accumulated = StringBuilder()
                val chatPrompt = buildString {
                    appendLine(
                        "You are Sifat Agent, a helpful bilingual (Bangla + English) AI assistant. " +
                            "Answer clearly in the user's language (${parsed.language})."
                    )
                    if (request.conversationHistory.isNotEmpty()) {
                        request.conversationHistory.takeLast(6).forEach { (role, msg) ->
                            appendLine("$role: $msg")
                        }
                    }
                    appendLine("User: ${request.userPrompt}")
                }
                withTimeout(60_000L) {
                    chatStreamModel.generateContentStream(content { text(chatPrompt) })
                        .collect { chunk ->
                            val delta = chunk.text.orEmpty()
                            if (delta.isNotEmpty()) {
                                accumulated.append(delta)
                                emit(AiStreamUpdate.StreamingReply(accumulated.toString()))
                            }
                        }
                }
                val finalReply = accumulated.toString().trim().ifBlank { parsed.replyText }
                emit(AiStreamUpdate.FinalPlan(ParsedAiPlan.Conversation(finalReply, parsed.language)))
            } else {
                emit(AiStreamUpdate.FinalPlan(parsed))
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Firebase AI request timed out", e)
            emit(AiStreamUpdate.Error("Request timed out after 60 seconds. Please check your connection and try again."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Firebase AI call failed", e)
            emit(AiStreamUpdate.Error(e.localizedMessage ?: "Gemini request failed."))
        }
    }

    private companion object {
        const val TAG = "FirebaseAiEngine"
    }
}
