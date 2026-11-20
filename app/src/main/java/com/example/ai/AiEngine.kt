package com.example.ai

import com.example.automation.ParsedAiPlan
import com.example.data.local.AiProviderMode
import com.example.data.local.AssistantLanguage
import kotlinx.coroutines.flow.Flow

data class AiRequestContext(
    val userPrompt: String,
    val preferredLanguage: AssistantLanguage,
    val conversationHistory: List<Pair<String, String>>, // ("user"|"model", text)
    val screenSummary: String? = null
)

sealed interface AiStreamUpdate {
    data object Thinking : AiStreamUpdate
    data class StreamingReply(val partialText: String) : AiStreamUpdate
    data class FinalPlan(val parsedPlan: ParsedAiPlan) : AiStreamUpdate
    data class Error(val message: String) : AiStreamUpdate
}

interface AiEngine {
    suspend fun processPrompt(request: AiRequestContext): ParsedAiPlan
    fun streamConversation(request: AiRequestContext): Flow<AiStreamUpdate>
}

object SifatSystemPrompts {
    fun buildPlannerInstruction(language: AssistantLanguage, screenSummary: String?): String {
        val langRule = when (language) {
            AssistantLanguage.BANGLA -> "Respond primarily in natural, polite Bangla (বাংলা), keeping action wire names in English."
            AssistantLanguage.ENGLISH -> "Respond in clear, concise English."
            AssistantLanguage.AUTO -> "Detect the user's language (Bangla or English) and reply in the exact same language."
        }
        val screenBlock = if (!screenSummary.isNullOrBlank()) {
            "\nActive screen accessibility summary (user-authorized):\n$screenSummary\n"
        } else {
            ""
        }

        return """
            You are Sifat Agent, a futuristic bilingual (Bangla + English) Android AI personal assistant.
            $langRule
            $screenBlock
            RULES:
            1. Classify every request into one of:
               - CONVERSATION: general Q&A, knowledge, coding, translation, advice, greetings, or explanations.
               - ACTION_PLAN: user asks to control the phone using ONLY these allow-listed actions:
                 LAUNCH_APP(appName), OPEN_SETTINGS(section: general|wifi|bluetooth|display|sound|accessibility|apps|location|security|battery),
                 GO_HOME(), GO_BACK(), READ_SCREEN_SUMMARY(), CLICK_ELEMENT(targetText), SCROLL_SCREEN(direction: UP|DOWN),
                 OPEN_URL(url), ADJUST_BRIGHTNESS(levelPercent: 5..100), DIAL_NUMBER(phoneNumber, contactLabel), COMPOSE_SMS(phoneNumber, messageBody).
               - CLARIFICATION: the request is ambiguous (e.g., missing phone number, unclear app/element name, or uncertain intent). Ask a short helpful question.
               - BLOCKED: user asks to bypass lock screens, bypass permissions/security, transfer money/payments, delete files, or run arbitrary code/shell commands.
            2. NEVER invent successful actions. You only propose validated action steps for the Android executor.
            3. Keep normal conversation separate from ACTION_PLAN. If the user asks a question, use CONVERSATION with empty steps.
            4. For multi-step tasks (e.g., "Open YouTube and scroll down"), include ordered steps in "steps".
        """.trimIndent()
    }
}
