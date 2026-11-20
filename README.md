# Sifat Agent — Bilingual AI Personal Assistant & Safe Android Automation

**Sifat Agent** is a native Kotlin + Jetpack Compose Android personal assistant featuring bilingual (**Bangla + English**) voice recognition, Text-to-Speech, Gemini-powered structured action planning, and a strictly allow-listed, user-authorized Android automation layer.

---

## Architecture Overview

- **UI Layer**: Jetpack Compose + Material 3 (`SifatAgentTheme` with Dark Futuristic default & Light Frost theme), adaptive `NavigationBar` / `NavigationRail`, custom animated neural orb & live RMS waveform, and bundled `Space Grotesk`, `Plus Jakarta Sans`, `Noto Sans Bengali`, and `JetBrains Mono` fonts.
- **State & Architecture**: MVVM (`SifatViewModel`) with Kotlin Coroutines, `StateFlow`, Room (`SifatDatabase`), DataStore (`UserPreferencesRepository`), and Android Keystore AES-256-GCM encryption (`SecureCredentialStore`).
- **AI Brain (`com.example.ai`)**:
  - **Default**: **Firebase AI Logic (`Firebase.ai` with `gemini-3.8-flash`)** attested via **Firebase App Check** — zero API keys packaged in the APK.
  - **Optional Self-Hosted Proxy (`/backend`)**: Node.js + Express + TypeScript backend using `@google/genai`, Zod schema validation, rate limiting, and Bearer authentication over HTTPS.
- **Voice Layer (`com.example.voice`)**:
  - Android `SpeechRecognizer` (`en-US` / `bn-BD` / bilingual auto) with live RMS waveform & graceful error handling.
  - Android `TextToSpeech` with automatic Bengali Unicode detection (`\u0980-\u09FF`).
  - Opt-in `WakeWordForegroundService` (`foregroundServiceType="microphone"`) displaying an ongoing system notification with a one-tap Stop button whenever enabled.
- **Safe Automation (`com.example.automation`)**:
  - Strict 11-action allow-list validated by `ActionValidator`.
  - `SifatAccessibilityService` (manual opt-in only) with master kill-switch and automatic exclusion of `isPassword` fields, lock screens, permission controllers, and sensitive banking/wallet apps.
  - Explicit confirmation cards and previews before calls (`ACTION_DIAL`), SMS (`ACTION_SENDTO`), URLs, brightness writes, or UI clicks.

---

## Project Structure

- `app/src/main/java/com/example/MainActivity.kt` — App Check wiring (`configureAppCheck`), edge-to-edge scaffold, adaptive navigation.
- `app/src/main/java/com/example/security/` — `AppCheckConfig.kt`, `SecureCredentialStore.kt`.
- `app/src/main/java/com/example/data/local/` — Room entities/DAOs (`SifatDatabase.kt`) & DataStore preferences.
- `app/src/main/java/com/example/ai/` — `FirebaseAiEngine.kt`, `CustomBackendAiEngine.kt`, `AiOrchestratorRepository.kt`.
- `app/src/main/java/com/example/voice/` — `VoiceRecognizerManager.kt`, `VoiceTtsManager.kt`, `WakeWordForegroundService.kt`.
- `app/src/main/java/com/example/automation/` — `AllowListedAction.kt`, `ActionValidator.kt`, `SifatAccessibilityService.kt`, `SafeAutomationExecutor.kt`.
- `app/src/main/java/com/example/ui/` — `SifatViewModel.kt`, components, and 9 Compose screens.
- `backend/` — Node.js + TypeScript secure Gemini proxy backend.
- `docs/` — `API_INTEGRATION.md` and `SIGNED_APK_GUIDE.md`.

---

## Supported vs. Restricted Capabilities

- **Supported**: Launching installed apps (`<queries>` launcher intent), opening Settings pages, `GO_HOME`, `GO_BACK`, `SCROLL_SCREEN`, user-triggered `READ_SCREEN_SUMMARY`, `CLICK_ELEMENT`, opening validated `https://`/`http://` URLs, brightness adjustment (`WRITE_SETTINGS`), dialer preview (`ACTION_DIAL`), and SMS composer preview (`ACTION_SENDTO`).
- **Intentionally Restricted (Android & Safety Policy)**:
  - Never places phone calls or sends SMS silently without opening the system dialer/SMS preview after user confirmation.
  - Never bypasses lock screens, permission dialogs, biometric prompts, or banking/wallet protections.
  - Never executes arbitrary shell commands, financial transfers, or destructive file deletions.
  - Never auto-enables `AccessibilityService` (Android OS strictly requires manual user enablement in Settings).
  - Bangla TTS audio depends on an installed Bengali TTS voice pack on the device; if missing, Sifat Agent notifies the user in Settings and displays text cleanly.

---

## Build & Run

1. Open the root project in Android Studio (or run `gradle assembleDebug` in the terminal).
2. Run unit and Robolectric tests:
   ```bash
   gradle :app:testDebugUnitTest
   ```
3. See [docs/API_INTEGRATION.md](docs/API_INTEGRATION.md) for backend proxy deployment and [docs/SIGNED_APK_GUIDE.md](docs/SIGNED_APK_GUIDE.md) for signed APK/AAB generation.
