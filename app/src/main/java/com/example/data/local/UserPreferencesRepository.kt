package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.security.SecureCredentialStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.sifatDataStore: DataStore<Preferences> by preferencesDataStore(name = "sifat_agent_prefs")

enum class ThemeMode(val label: String) {
    DARK("Dark Futuristic"),
    LIGHT("Light Frost"),
    SYSTEM("Follow System")
}

enum class AssistantLanguage(val label: String, val bcp47: String) {
    AUTO("Bilingual (EN + বাংলা)", "en-US"),
    ENGLISH("English", "en-US"),
    BANGLA("বাংলা (Bangla)", "bn-BD")
}

enum class AiProviderMode(val label: String, val subtitle: String) {
    FIREBASE_AI_LOGIC(
        "Firebase AI Logic (Default)",
        "App Check attested Gemini proxy — zero API keys in APK"
    ),
    CUSTOM_HTTPS_BACKEND(
        "Custom HTTPS Proxy",
        "Connect to your self-hosted /backend Node.js Gemini proxy"
    )
}

data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val language: AssistantLanguage = AssistantLanguage.AUTO,
    val ttsEnabled: Boolean = true,
    val ttsSpeechRate: Float = 1.0f,
    val wakeWordEnabled: Boolean = false,
    val automationMasterEnabled: Boolean = true,
    val aiProviderMode: AiProviderMode = AiProviderMode.FIREBASE_AI_LOGIC,
    val customBackendUrl: String = "",
    val hasCustomBackendToken: Boolean = false,
    val onboardingCompleted: Boolean = false
)

class UserPreferencesRepository(
    context: Context,
    private val credentialStore: SecureCredentialStore
) {
    private val dataStore = context.applicationContext.sifatDataStore

    val preferencesFlow: Flow<UserPreferences> = dataStore.data.map { prefs ->
        UserPreferences(
            themeMode = prefs[KEY_THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.DARK,
            language = prefs[KEY_LANGUAGE]?.let { runCatching { AssistantLanguage.valueOf(it) }.getOrNull() } ?: AssistantLanguage.AUTO,
            ttsEnabled = prefs[KEY_TTS_ENABLED] ?: true,
            ttsSpeechRate = prefs[KEY_TTS_RATE] ?: 1.0f,
            wakeWordEnabled = prefs[KEY_WAKE_WORD] ?: false,
            automationMasterEnabled = prefs[KEY_AUTOMATION_MASTER] ?: true,
            aiProviderMode = prefs[KEY_AI_PROVIDER]?.let { runCatching { AiProviderMode.valueOf(it) }.getOrNull() }
                ?: AiProviderMode.FIREBASE_AI_LOGIC,
            customBackendUrl = prefs[KEY_CUSTOM_BACKEND_URL] ?: "",
            hasCustomBackendToken = credentialStore.readBackendToken().isNotBlank(),
            onboardingCompleted = prefs[KEY_ONBOARDING_DONE] ?: false
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[KEY_THEME] = mode.name }
    }

    suspend fun setLanguage(language: AssistantLanguage) {
        dataStore.edit { it[KEY_LANGUAGE] = language.name }
    }

    suspend fun setTtsEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_TTS_ENABLED] = enabled }
    }

    suspend fun setTtsSpeechRate(rate: Float) {
        dataStore.edit { it[KEY_TTS_RATE] = rate.coerceIn(0.6f, 1.8f) }
    }

    suspend fun setWakeWordEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_WAKE_WORD] = enabled }
    }

    suspend fun setAutomationMasterEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_AUTOMATION_MASTER] = enabled }
    }

    suspend fun setAiProviderMode(mode: AiProviderMode) {
        dataStore.edit { it[KEY_AI_PROVIDER] = mode.name }
    }

    suspend fun setCustomBackendConfig(url: String, token: String?) {
        val trimmedUrl = url.trim()
        dataStore.edit { it[KEY_CUSTOM_BACKEND_URL] = trimmedUrl }
        if (token != null) {
            credentialStore.saveBackendToken(token.trim())
        }
        // Trigger emission even if URL was unchanged when token changed
        dataStore.edit { it[KEY_TOKEN_UPDATED_AT] = System.currentTimeMillis().toString() }
    }

    fun getCustomBackendToken(): String = credentialStore.readBackendToken()

    suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { it[KEY_ONBOARDING_DONE] = completed }
    }

    suspend fun resetAll() {
        credentialStore.clearBackendToken()
        dataStore.edit { it.clear() }
    }

    private companion object {
        val KEY_THEME = stringPreferencesKey("theme_mode")
        val KEY_LANGUAGE = stringPreferencesKey("assistant_language")
        val KEY_TTS_ENABLED = booleanPreferencesKey("tts_enabled")
        val KEY_TTS_RATE = floatPreferencesKey("tts_speech_rate")
        val KEY_WAKE_WORD = booleanPreferencesKey("wake_word_enabled")
        val KEY_AUTOMATION_MASTER = booleanPreferencesKey("automation_master_enabled")
        val KEY_AI_PROVIDER = stringPreferencesKey("ai_provider_mode")
        val KEY_CUSTOM_BACKEND_URL = stringPreferencesKey("custom_backend_url")
        val KEY_ONBOARDING_DONE = booleanPreferencesKey("onboarding_completed")
        val KEY_TOKEN_UPDATED_AT = stringPreferencesKey("token_updated_at")
    }
}
