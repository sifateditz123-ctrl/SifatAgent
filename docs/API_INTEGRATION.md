# Sifat Agent — Secure Backend & AI Integration Guide

Sifat Agent supports **two** zero-APK-secret AI architectures:

## Option 1: Firebase AI Logic (Default, Active Out of the Box)

- Uses `Firebase.ai.generativeModel("gemini-3.8-flash")` on Android.
- Protected by **Firebase App Check** (`DebugAppCheckProviderFactory` in debug builds, `RecaptchaAppCheckProviderFactory` in release builds).
- No Gemini API key is ever stored in `.env`, `BuildConfig`, or the generated APK.
- `app/google-services.json` and `app/src/main/res/values/firebase_applet_config.xml` are already provisioned.

---

## Option 2: Self-Hosted Node.js HTTPS Backend (`/backend`)

Use this option if you want your own Express proxy sitting between the Android app and Gemini.

### 1. Configure & Run the Backend

```bash
cd backend
npm install
cp .env.example .env
```

Edit `backend/.env`:
```env
PORT=8443
GEMINI_API_KEY=your_server_side_gemini_api_key
CLIENT_AUTH_TOKEN=generate_a_strong_64_char_random_token
ALLOWED_ORIGINS=https://your-domain.example
GEMINI_MODEL=gemini-3.8-flash
```

Build and start:
```bash
npm run build
npm start
```

Deploy behind a TLS reverse proxy (Cloud Run, Nginx, Caddy, or Railway) so the endpoint is served strictly over **HTTPS** (`usesCleartextTraffic="false"` is enforced in `AndroidManifest.xml`).

### 2. Connect the Android App

1. Open **Sifat Agent -> Settings -> AI Provider & Secure Backend**.
2. Select **Custom HTTPS Proxy**.
3. Enter your HTTPS base URL (e.g. `https://sifat-proxy.example.com`) and Bearer Token.
4. Tap **Save Backend Config** — the token is encrypted on-device using **Android Keystore AES-256-GCM**.

### 3. API Contract (`POST /api/v1/assistant/plan`)

Request:
```json
{
  "prompt": "Open YouTube and scroll down",
  "language": "AUTO",
  "screenSummary": "Optional user-authorized screen text",
  "history": [
    { "role": "user", "text": "Hello" },
    { "role": "model", "text": "Hi! How can I help?" }
  ]
}
```

Response (validated by both backend Zod and Android `ActionValidator`):
```json
{
  "intentType": "ACTION_PLAN",
  "language": "en",
  "replyText": "Opening YouTube and scrolling down.",
  "summary": "Launch YouTube and scroll",
  "steps": [
    { "action": "LAUNCH_APP", "parameters": { "appName": "YouTube" } },
    { "action": "SCROLL_SCREEN", "parameters": { "direction": "DOWN" } }
  ]
}
```
