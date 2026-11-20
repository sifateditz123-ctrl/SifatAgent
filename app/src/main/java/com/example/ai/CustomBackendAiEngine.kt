package com.example.ai

import android.util.Log
import com.example.automation.ActionValidator
import com.example.automation.ParsedAiPlan
import com.example.data.local.AiProviderMode
import com.example.data.local.UserPreferencesRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * HTTPS backend proxy client that connects to the self-hosted Node.js Gemini proxy
 * when the user switches AI Provider to Custom HTTPS Proxy in Settings.
 */
class CustomBackendAiEngine(
    private val preferencesRepository: UserPreferencesRepository
) : AiEngine {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    override suspend fun processPrompt(request: AiRequestContext): ParsedAiPlan {
        if (ActionValidator.isPromptForbidden(request.userPrompt)) {
            return ParsedAiPlan.BlockedOrInvalid(
                reason = "Blocked by Sifat Agent safety policy.",
                language = "en"
            )
        }

        val prefs = preferencesRepository.preferencesFlow.first()
        val baseUrl = prefs.customBackendUrl.trim().trimEnd('/')
        if (!baseUrl.startsWith("https://")) {
            throw IllegalStateException(
                "Custom backend URL must use HTTPS (e.g., https://api.example.com)."
            )
        }

        val token = preferencesRepository.getCustomBackendToken()
        val bodyJson = JSONObject().apply {
            put("prompt", request.userPrompt)
            put("language", request.preferredLanguage.name)
            if (!request.screenSummary.isNullOrBlank()) {
                put("screenSummary", request.screenSummary)
            }
            val histArray = JSONArray()
            request.conversationHistory.takeLast(8).forEach { (role, text) ->
                histArray.put(JSONObject().apply {
                    put("role", role)
                    put("text", text)
                })
            }
            put("history", histArray)
        }

        val endpoint = "$baseUrl/api/v1/assistant/plan"
        val httpRequest = Request.Builder()
            .url(endpoint)
            .apply {
                if (token.isNotBlank()) {
                    header("Authorization", "Bearer $token")
                }
            }
            .post(bodyJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        val rawResponseBody = withTimeout(60_000L) {
            withContext(Dispatchers.IO) {
                client.newCall(httpRequest).execute().use { response ->
                    val payload = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        throw IllegalStateException("Backend error HTTP ${response.code}: $payload")
                    }
                    payload
                }
            }
        }

        return ActionValidator.parseStructuredPlanJson(rawResponseBody)
    }

    override fun streamConversation(request: AiRequestContext): Flow<AiStreamUpdate> = flow {
        emit(AiStreamUpdate.Thinking)
        try {
            val plan = processPrompt(request)
            emit(AiStreamUpdate.FinalPlan(plan))
        } catch (e: TimeoutCancellationException) {
            emit(AiStreamUpdate.Error("Custom backend timed out after 60 seconds."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Custom backend request failed", e)
            emit(AiStreamUpdate.Error(e.localizedMessage ?: "Backend request failed."))
        }
    }

    private companion object {
        const val TAG = "CustomBackendAiEngine"
    }
}

class AiOrchestratorRepository(
    private val preferencesRepository: UserPreferencesRepository,
    private val firebaseAiEngine: FirebaseAiEngine,
    private val customBackendAiEngine: CustomBackendAiEngine
) {
    suspend fun streamRequest(request: AiRequestContext): Flow<AiStreamUpdate> {
        val prefs = preferencesRepository.preferencesFlow.first()
        val engine: AiEngine = when (prefs.aiProviderMode) {
            AiProviderMode.FIREBASE_AI_LOGIC -> firebaseAiEngine
            AiProviderMode.CUSTOM_HTTPS_BACKEND -> customBackendAiEngine
        }
        return engine.streamConversation(request)
    }
}
