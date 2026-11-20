# Signed APK & App Bundle (AAB) Build Guide

## 1. Generate an Upload Keystore (First Time Only)

Run `keytool` outside version control (never commit `.jks` or `.keystore` files to Git):

```bash
keytool -genkey -v \
  -keystore my-upload-key.jks \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias upload
```

## 2. Provide Signing Environment Variables

`app/build.gradle.kts` reads signing credentials from environment variables for release builds:

```bash
export KEYSTORE_PATH="/absolute/path/to/my-upload-key.jks"
export STORE_PASSWORD="your-store-password"
export KEY_PASSWORD="your-key-password"
```

## 3. Configure Release App Check (reCAPTCHA Enterprise)

Ensure `app/src/main/res/values/firebase_applet_config.xml` contains your `recaptcha_site_key` for your package ID (`com.aistudio.sifatagent.xkqrmz`) and register your release SHA-1/SHA-256 fingerprints in the Firebase Console.

## 4. Build Signed Release APK / AAB

```bash
# Signed Release APK
gradle :app:assembleRelease

# Signed Android App Bundle (.aab for Google Play)
gradle :app:bundleRelease
```

Outputs will be generated at:
- APK: `app/build/outputs/apk/release/app-release.apk`
- AAB: `app/build/outputs/bundle/release/app-release.aab`
