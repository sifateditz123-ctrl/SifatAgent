package com.example.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.AiOrchestratorRepository
import com.example.ai.AiRequestContext
import com.example.ai.AiStreamUpdate
import com.example.ai.CustomBackendAiEngine
import com.example.ai.FirebaseAiEngine
import com.example.automation.ActionExecutionResult
import com.example.automation.ActionPlan
import com.example.automation.ActionType
import com.example.automation.ActionValidator
import com.example.automation.InstalledAppSummary
import com.example.automation.ParsedAiPlan
import com.example.automation.PermissionStatusSnapshot
import com.example.automation.SafeAutomationExecutor
import com.example.automation.ScreenSnapshot
import com.example.automation.SifatAccessibilityService
import com.example.automation.ValidatedAction
import com.example.data.local.AiProviderMode
import com.example.data.local.AssistantLanguage
import com.example.data.local.ChatMessageEntity
import com.example.data.local.CommandHistoryEntity
import com.example.data.local.HistoryRepository
import com.example.data.local.SifatDatabase
import com.example.data.local.ThemeMode
import com.example.data.local.UserPreferences
import com.example.data.local.UserPreferencesRepository
import com.example.security.SecureCredentialStore
import com.example.voice.VoiceRecognitionState
import com.example.voice.VoiceRecognizerManager
import com.example.voice.VoiceTtsManager
import com.example.voice.WakeWordEventBus
import com.example.voice.WakeWordForegroundService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface AssistantUiState {
    data object Idle : AssistantUiState
    data class Processing(val statusLabel: String = "Thinking with Gemini...") : AssistantUiState
    data class Streaming(val partialText: String) : AssistantUiState
    data class ExecutingStep(val stepIndex: Int, val totalSteps: Int, val label: String) : AssistantUiState
    data class Error(val message: String) : AssistantUiState
}

class SifatViewModel(application: Application) : AndroidViewModel(application) {

    private val credentialStore = SecureCredentialStore(application)
    private val preferencesRepository = UserPreferencesRepository(application, credentialStore)
    private val database = SifatDatabase.getInstance(application)
    private val historyRepository = HistoryRepository(
        database.chatDao(),
        database.commandHistoryDao()
    )

    private val aiOrchestrator = AiOrchestratorRepository(
        preferencesRepository = preferencesRepository,
        firebaseAiEngine = FirebaseAiEngine(),
        customBackendAiEngine = CustomBackendAiEngine(preferencesRepository)
    )

    val automationExecutor = SafeAutomationExecutor(application)
    private val voiceRecognizer = VoiceRecognizerManager(application)
    private val voiceTts = VoiceTtsManager(application)

    val preferences: StateFlow<UserPreferences> = preferencesRepository.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserPreferences())

    val chatMessages: StateFlow<List<ChatMessageEntity>> = historyRepository.messages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val commandHistory: StateFlow<List<CommandHistoryEntity>> = historyRepository.commandHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val voiceRecognitionState: StateFlow<VoiceRecognitionState> = voiceRecognizer.state
    val isSpeaking: StateFlow<Boolean> = voiceTts.isSpeaking
    val banglaVoiceSupported: StateFlow<Boolean> = voiceTts.banglaVoiceSupported
    val wakeWordServiceRunning: StateFlow<Boolean> = WakeWordEventBus.isServiceRunning
    val accessibilityConnected: StateFlow<Boolean> = SifatAccessibilityService.isConnected

    private val _assistantState = MutableStateFlow<AssistantUiState>(AssistantUiState.Idle)
    val assistantState: StateFlow<AssistantUiState> = _assistantState.asStateFlow()

    private val _permissionSnapshot = MutableStateFlow(
        automationExecutor.getPermissionSnapshot(preferences.value.automationMasterEnabled)
    )
    val permissionSnapshot: StateFlow<PermissionStatusSnapshot> = _permissionSnapshot.asStateFlow()

    private val _installedApps = MutableStateFlow<List<InstalledAppSummary>>(emptyList())
    val installedApps: StateFlow<List<InstalledAppSummary>> = _installedApps.asStateFlow()

    private val _latestScreenSnapshot = MutableStateFlow<ScreenSnapshot?>(null)
    val latestScreenSnapshot: StateFlow<ScreenSnapshot?> = _latestScreenSnapshot.asStateFlow()

    private val _pendingCatalogAction = MutableStateFlow<ValidatedAction?>(null)
    val pendingCatalogAction: StateFlow<ValidatedAction?> = _pendingCatalogAction.asStateFlow()

    private val _lastVoiceReply = MutableStateFlow<String?>(null)
    val lastVoiceReply: StateFlow<String?> = _lastVoiceReply.asStateFlow()

    init {
        viewModelScope.launch {
            preferences.collect { prefs ->
                SifatAccessibilityService.masterAutomationEnabled = prefs.automationMasterEnabled
                refreshPermissions()
            }
        }
        viewModelScope.launch {
            WakeWordEventBus.wakeEvents.collect { trailingCommand ->
                if (trailingCommand.isNotBlank()) {
                    sendUserPrompt(trailingCommand)
                } else {
                    startVoiceCapture()
                }
            }
        }
        refreshInstalledApps()
    }

    fun refreshPermissions() {
        _permissionSnapshot.value = automationExecutor.getPermissionSnapshot(
            preferences.value.automationMasterEnabled
        )
    }

    fun refreshInstalledApps() {
        viewModelScope.launch {
            val apps = withContext(Dispatchers.IO) {
                automationExecutor.queryLaunchableApps()
            }
            _installedApps.value = apps
        }
    }

    fun isSpeechRecognitionAvailable(): Boolean = voiceRecognizer.isAvailable()

    fun startVoiceCapture() {
        voiceTts.stop()
        val lang = preferences.value.language
        voiceRecognizer.startListening(lang) { recognizedText ->
            sendUserPrompt(recognizedText)
        }
    }

    fun stopVoiceCapture() {
        voiceRecognizer.stopListening()
    }

    fun cancelVoiceCapture() {
        voiceRecognizer.cancel()
        _assistantState.value = AssistantUiState.Idle
    }

    fun stopSpeaking() {
        voiceTts.stop()
    }

    fun speakText(text: String, languageHint: String? = null) {
        val prefs = preferences.value
        if (prefs.ttsEnabled) {
            voiceTts.speak(text, prefs.ttsSpeechRate, languageHint)
        }
    }

    fun sendUserPrompt(rawPrompt: String, includeAuthorizedScreenContext: Boolean = false) {
        val prompt = rawPrompt.trim()
        if (prompt.isBlank()) return

        viewModelScope.launch {
            _assistantState.value = AssistantUiState.Processing("Understanding request...")
            try {
                historyRepository.addMessage(
                    ChatMessageEntity(
                        sender = "USER",
                        content = prompt,
                        language = if (prompt.any { it in '\u0980'..'\u09FF' }) "bn" else "en"
                    )
                )

                val screenSummary = if (includeAuthorizedScreenContext && SifatAccessibilityService.isConnected.value) {
                    val snap = SifatAccessibilityService.instance?.captureVisibleScreenTree()
                    if (snap != null && snap.blockedReason == null) {
                        "App: ${snap.packageName}; Text: ${snap.visibleTexts.take(10).joinToString(" | ")}; Buttons: ${snap.clickableLabels.take(8).joinToString(", ")}"
                    } else null
                } else null

                val recentHistory = chatMessages.value
                    .takeLast(8)
                    .map { (if (it.sender == "USER") "user" else "model") to it.content }

                val request = AiRequestContext(
                    userPrompt = prompt,
                    preferredLanguage = preferences.value.language,
                    conversationHistory = recentHistory,
                    screenSummary = screenSummary
                )

                aiOrchestrator.streamRequest(request).collect { update ->
                    when (update) {
                        is AiStreamUpdate.Thinking -> {
                            _assistantState.value = AssistantUiState.Processing("Sifat Agent is thinking...")
                        }

                        is AiStreamUpdate.StreamingReply -> {
                            _assistantState.value = AssistantUiState.Streaming(update.partialText)
                        }

                        is AiStreamUpdate.FinalPlan -> {
                            handleParsedAiPlan(prompt, update.parsedPlan)
                        }

                        is AiStreamUpdate.Error -> {
                            voiceRecognizer.resetToIdle()
                            _assistantState.value = AssistantUiState.Error(update.message)
                            historyRepository.addMessage(
                                ChatMessageEntity(
                                    sender = "SYSTEM",
                                    content = "Error: ${update.message}"
                                )
                            )
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error handling prompt", e)
                voiceRecognizer.resetToIdle()
                _assistantState.value = AssistantUiState.Error(
                    e.localizedMessage ?: "Unexpected error processing your request."
                )
            }
        }
    }

    private suspend fun handleParsedAiPlan(userPrompt: String, parsed: ParsedAiPlan) {
        voiceRecognizer.resetToIdle()
        when (parsed) {
            is ParsedAiPlan.Conversation -> {
                _lastVoiceReply.value = parsed.replyText
                historyRepository.addMessage(
                    ChatMessageEntity(
                        sender = "ASSISTANT",
                        content = parsed.replyText,
                        language = parsed.language
                    )
                )
                _assistantState.value = AssistantUiState.Idle
                speakText(parsed.replyText, parsed.language)
            }

            is ParsedAiPlan.Clarification -> {
                _lastVoiceReply.value = parsed.question
                historyRepository.addMessage(
                    ChatMessageEntity(
                        sender = "ASSISTANT",
                        content = parsed.question,
                        language = parsed.language
                    )
                )
                _assistantState.value = AssistantUiState.Idle
                speakText(parsed.question, parsed.language)
            }

            is ParsedAiPlan.BlockedOrInvalid -> {
                _lastVoiceReply.value = parsed.reason
                historyRepository.addMessage(
                    ChatMessageEntity(
                        sender = "ASSISTANT",
                        content = parsed.reason,
                        language = parsed.language
                    )
                )
                historyRepository.logCommand(
                    CommandHistoryEntity(
                        userPrompt = userPrompt,
                        actionName = "POLICY_BLOCKED",
                        parametersSummary = "-",
                        riskLevel = "HIGH",
                        status = "DENIED",
                        resultMessage = parsed.reason
                    )
                )
                _assistantState.value = AssistantUiState.Idle
                speakText(parsed.reason, parsed.language)
            }

            is ParsedAiPlan.StructuredActionPlan -> {
                val serialized = ActionValidator.serializePlanToJson(parsed.plan)
                if (parsed.plan.requiresConfirmation) {
                    val promptReply = "${parsed.replyText}\n\nPlease review and confirm this action plan below."
                    _lastVoiceReply.value = parsed.replyText
                    historyRepository.addMessage(
                        ChatMessageEntity(
                            sender = "ASSISTANT",
                            content = promptReply,
                            language = parsed.language,
                            actionPlanJson = serialized,
                            actionStatus = "PENDING_CONFIRMATION"
                        )
                    )
                    _assistantState.value = AssistantUiState.Idle
                    speakText(parsed.replyText, parsed.language)
                } else {
                    val msgId = historyRepository.addMessage(
                        ChatMessageEntity(
                            sender = "ASSISTANT",
                            content = parsed.replyText,
                            language = parsed.language,
                            actionPlanJson = serialized,
                            actionStatus = "EXECUTING"
                        )
                    )
                    executePlanInternal(msgId, userPrompt, parsed.plan)
                }
            }
        }
    }

    fun confirmActionPlan(messageId: Long, plan: ActionPlan) {
        viewModelScope.launch {
            executePlanInternal(messageId, plan.summary, plan)
        }
    }

    fun cancelActionPlan(messageId: Long, plan: ActionPlan) {
        viewModelScope.launch {
            historyRepository.updateMessageActionState(
                id = messageId,
                status = "CANCELLED",
                updatedContent = "Action cancelled by user: ${plan.summary}"
            )
            plan.steps.forEach { step ->
                historyRepository.logCommand(
                    CommandHistoryEntity(
                        userPrompt = plan.summary,
                        actionName = step.actionType.wireName,
                        parametersSummary = step.previewSummary,
                        riskLevel = step.riskLevel.label,
                        status = "CANCELLED",
                        resultMessage = "User cancelled before execution."
                    )
                )
            }
        }
    }

    private suspend fun executePlanInternal(
        messageId: Long,
        userPrompt: String,
        plan: ActionPlan
    ) {
        val masterEnabled = preferences.value.automationMasterEnabled
        val report = automationExecutor.executePlan(
            plan = plan,
            automationMasterEnabled = masterEnabled
        ) { index, total, step ->
            _assistantState.value = AssistantUiState.ExecutingStep(
                stepIndex = index,
                totalSteps = total,
                label = step.previewSummary
            )
        }

        val finalStatus = if (report.overallSuccess) "COMPLETED" else "FAILED"
        val updatedText = if (report.overallSuccess) {
            "${plan.summary}\n\nResult: ${report.summaryMessage}"
        } else {
            "${plan.summary}\n\nStopped: ${report.summaryMessage}"
        }

        historyRepository.updateMessageActionState(
            id = messageId,
            status = finalStatus,
            updatedContent = updatedText
        )

        report.stepResults.forEach { (step, result) ->
            val statusStr = when (result) {
                is ActionExecutionResult.Success -> "SUCCESS"
                is ActionExecutionResult.PermissionDenied -> "DENIED"
                is ActionExecutionResult.Cancelled -> "CANCELLED"
                is ActionExecutionResult.Failed -> "FAILED"
            }
            val msgStr = when (result) {
                is ActionExecutionResult.Success -> result.extraOutput ?: result.message
                is ActionExecutionResult.PermissionDenied -> result.message
                is ActionExecutionResult.Cancelled -> result.message
                is ActionExecutionResult.Failed -> result.message
            }
            historyRepository.logCommand(
                CommandHistoryEntity(
                    userPrompt = userPrompt,
                    actionName = step.actionType.wireName,
                    parametersSummary = step.previewSummary,
                    riskLevel = step.riskLevel.label,
                    status = statusStr,
                    resultMessage = msgStr
                )
            )
        }

        _lastVoiceReply.value = report.summaryMessage
        _assistantState.value = AssistantUiState.Idle
        speakText(report.summaryMessage, plan.language)
    }

    fun inspectActiveScreenSafely() {
        val service = SifatAccessibilityService.instance
        if (service == null) {
            _latestScreenSnapshot.value = ScreenSnapshot(
                packageName = "",
                visibleTexts = emptyList(),
                clickableLabels = emptyList(),
                editableFields = emptyList(),
                suggestedNextActions = emptyList(),
                blockedReason = "Enable Sifat Accessibility Service in Permission Setup to read visible screen labels."
            )
            return
        }
        _latestScreenSnapshot.value = service.captureVisibleScreenTree()
    }

    fun requestCatalogAction(action: ValidatedAction) {
        if (action.requiresConfirmation) {
            _pendingCatalogAction.value = action
        } else {
            executeCatalogActionConfirmed(action)
        }
    }

    fun dismissPendingCatalogAction() {
        _pendingCatalogAction.value = null
    }

    fun executeCatalogActionConfirmed(action: ValidatedAction) {
        _pendingCatalogAction.value = null
        viewModelScope.launch {
            val result = automationExecutor.executeAction(
                action = action,
                automationMasterEnabled = preferences.value.automationMasterEnabled
            )
            val statusStr = when (result) {
                is ActionExecutionResult.Success -> "SUCCESS"
                is ActionExecutionResult.PermissionDenied -> "DENIED"
                is ActionExecutionResult.Cancelled -> "CANCELLED"
                is ActionExecutionResult.Failed -> "FAILED"
            }
            val msgStr = when (result) {
                is ActionExecutionResult.Success -> result.extraOutput ?: result.message
                is ActionExecutionResult.PermissionDenied -> result.message
                is ActionExecutionResult.Cancelled -> result.message
                is ActionExecutionResult.Failed -> result.message
            }
            historyRepository.logCommand(
                CommandHistoryEntity(
                    userPrompt = "Automation Panel",
                    actionName = action.actionType.wireName,
                    parametersSummary = action.previewSummary,
                    riskLevel = action.riskLevel.label,
                    status = statusStr,
                    resultMessage = msgStr
                )
            )
            _lastVoiceReply.value = msgStr
            if (result !is ActionExecutionResult.Success) {
                _assistantState.value = AssistantUiState.Error(msgStr)
            }
        }
    }

    fun toggleWakeWord(enable: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setWakeWordEnabled(enable)
            val ctx = getApplication<Application>()
            if (enable) {
                WakeWordForegroundService.start(ctx)
            } else {
                WakeWordForegroundService.stop(ctx)
            }
        }
    }

    fun toggleMasterAutomation(enable: Boolean) {
        viewModelScope.launch {
            SifatAccessibilityService.masterAutomationEnabled = enable
            preferencesRepository.setAutomationMasterEnabled(enable)
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferencesRepository.setThemeMode(mode) }
    }

    fun setLanguage(language: AssistantLanguage) {
        viewModelScope.launch { preferencesRepository.setLanguage(language) }
    }

    fun setTtsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            if (!enabled) voiceTts.stop()
            preferencesRepository.setTtsEnabled(enabled)
        }
    }

    fun setTtsSpeechRate(rate: Float) {
        viewModelScope.launch { preferencesRepository.setTtsSpeechRate(rate) }
    }

    fun setAiProviderMode(mode: AiProviderMode) {
        viewModelScope.launch { preferencesRepository.setAiProviderMode(mode) }
    }

    fun saveCustomBackendConfig(url: String, token: String?) {
        viewModelScope.launch {
            preferencesRepository.setCustomBackendConfig(url, token)
        }
    }

    fun getCustomBackendToken(): String = preferencesRepository.getCustomBackendToken()

    fun deleteChatMessage(id: Long) {
        viewModelScope.launch { historyRepository.deleteMessage(id) }
    }

    fun clearChatHistory() {
        viewModelScope.launch { historyRepository.clearChatHistory() }
    }

    fun deleteCommandHistoryItem(id: Long) {
        viewModelScope.launch { historyRepository.deleteCommandHistoryItem(id) }
    }

    fun clearCommandHistory() {
        viewModelScope.launch { historyRepository.clearCommandHistory() }
    }

    fun resetAllData() {
        viewModelScope.launch {
            WakeWordForegroundService.stop(getApplication())
            historyRepository.clearChatHistory()
            historyRepository.clearCommandHistory()
            preferencesRepository.resetAll()
        }
    }

    fun clearErrorState() {
        if (_assistantState.value is AssistantUiState.Error) {
            _assistantState.value = AssistantUiState.Idle
        }
        if (voiceRecognitionState.value is VoiceRecognitionState.Error) {
            voiceRecognizer.resetToIdle()
        }
    }

    override fun onCleared() {
        voiceRecognizer.cancel()
        voiceTts.shutdown()
        super.onCleared()
    }

    private companion object {
        const val TAG = "SifatViewModel"
    }
}
