# DocuScan OCR — Setup Guide

This guide covers how to build the app and configure cloud integrations (Google Drive, Dropbox, Cloudflare R2). OneDrive is planned but not yet wired in.

---

## 1. Prerequisites

- **Android Studio** (Ladybug / Hedgehog or newer)
- **JDK 17** (the project uses AGP 9.x and Kotlin 2.2)
- **Android SDK** Platform 36 (compileSdk 36), minSdk 24
- A physical device or emulator with **internet access** (cloud sync needs it)

## 2. Build & run

```bash
git clone https://github.com/Rishav7324/DocuScan-OCR.git
cd DocuScan-OCR
```

Open the project in Android Studio, then:

```bash
# Debug APK
gradle :app:assembleDebug

# Unit tests (Robolectric)
gradle :app:testDebugUnitTest
```

Install on a device:

```bash
gradle :app:installDebug
```

> The app needs no external API keys to run OCR — text recognition is fully on-device via **ML Kit**. Only cloud sync requires provider credentials (below).

---

## 3. Cloud integrations

All cloud providers use a shared redirect URI. Register it once in every console you use:

```
docuscan://oauth
```

OAuth client credentials live in `app/src/main/res/values/oauth.xml`. Fill in the values for the providers you want; leave the others as placeholders.

```xml
<string name="oauth_google_client_id"   translatable="false">YOUR_GOOGLE_WEB_CLIENT_ID</string>
<string name="oauth_google_client_secret" translatable="false">YOUR_GOOGLE_CLIENT_SECRET</string>
<string name="oauth_dropbox_client_id"  translatable="false">YOUR_DROPBOX_APP_KEY</string>
<string name="oauth_dropbox_client_secret" translatable="false">YOUR_DROPBOX_APP_SECRET</string>
```

In the app, open **Cloud Sync** and tap **Connect** on a provider's card. Your browser opens for consent; the returned token is stored locally and used for live uploads.

### 3.1 Google Drive (1-click OAuth)

1. **Google Cloud Console** → create a project.
2. **APIs & Services → Library** → enable **Google Drive API**.
3. **OAuth consent screen** → set up (External or Internal).
4. **Credentials → Create OAuth client ID** → type **Web application**.
5. Add `docuscan://oauth` as an authorized redirect URI.
6. Copy the **Client ID** and **Client Secret** into `oauth.xml` (`oauth_google_client_id` / `oauth_google_client_secret`).
7. In the app, tap **Connect** on the Google Drive card. Scope requested: `drive.file`.

Uploads use the live **Drive v3** multipart API.

### 3.2 Dropbox (1-click OAuth, PKCE)

1. **Dropbox App Console** → **Create app**.
2. Choose **Scoped access** and **App folder** access.
3. **Permissions** → enable `files.metadata.write` and `files.content.write`.
4. **Settings** → add `docuscan://oauth` as a redirect URI.
5. Copy the **App key** and **App secret** into `oauth.xml` (`oauth_dropbox_client_id` / `oauth_dropbox_client_secret`).
6. In the app, tap **Connect** on the Dropbox card. Consent uses a PKCE flow; no secret is embedded in the app.

Uploads use the live **Dropbox `files/upload`** API.

### 3.3 Cloudflare R2 (manual keys)

R2 uses S3-compatible **AWS Signature V4** with static keys (SigV4, not bearer-token OAuth). Enter them directly in the app's R2 card:

1. **Cloudflare dashboard → R2** → create a bucket (e.g. `docuscan-scans`).
2. **Manage R2 API Tokens** → generate read/write credentials.
3. In the app, fill in:
   - **Endpoint URL**: `https://<account_id>.r2.cloudflarestorage.com`
   - **Bucket Name**
   - **Access Key ID** / **Secret Access Key**

Uploads are signed with AWS SigV4 and sent to your bucket.

> R2 does offer OAuth tokens, but the current upload path uses SigV4 key/secret, so the manual-key route is the supported one. A bearer-token R2 flow can be added later if desired.

---

## 4. Project structure (key parts)

```
app/src/main/
  java/com/example/
    data/api/
      OfflineOcrService.kt     # ML Kit on-device OCR
      CloudSyncIntegrator.kt   # R2 / Drive / Dropbox uploads (SigV4 + live APIs)
      OAuthManager.kt          # browser OAuth2 helper (Google, Dropbox)
    data/encryption/
      EncryptionUtils.kt       # Android Keystore + AES-GCM, PBKDF2
    data/export/
      ExportUtils.kt           # real PDF / DOCX / TXT generation
    ui/screens/
      CloudSyncScreen.kt       # Connect buttons + credential entry
      ...
  res/values/oauth.xml        # OAuth client credentials (fill these in)
```

## 5. Notes & limitations

- **No Firebase / Gemini / remote AI.** OCR is local (ML Kit); encryption uses the Android Keystore.
- **Audit log & encryption are building blocks, not a certified compliance product.** Do not use for real PHI without a proper review.
- **OneDrive** is not yet implemented in the UI; the OAuth manager can be extended with an `ONEDRIVE` provider following the Google/Dropbox pattern.
- Cloud tokens are stored in `SharedPreferences` (plaintext). For production, move them into the Android Keystore / EncryptedSharedPreferences.

## 6. SHA-1 certificate fingerprint (for OAuth / console registration)

Some providers (Google via Credential Manager/Firebase Auth, Azure) ask for your app's **SHA-1 certificate fingerprint**. This app uses the browser-redirect OAuth pattern, so SHA-1 is **not required** for the Google/Dropbox Connect buttons — but you'll need it if you later add native sign-in.

Generate it with the helper script (needs a JDK on your machine):

```bash
bash scripts/get_sha1.sh
```

Or manually:

```bash
# Debug keystore (used by assembleDebug)
keytool -list -v \
  -keystore ~/.android/debug.keystore \
  -alias androiddebugkey -storepass android -keypass android | grep -i "SHA1:"

# All signing configs via Gradle
./gradlew signingReport
```

The debug keystore is created automatically on your first `gradle assembleDebug` build if it doesn't exist yet. The fingerprint is **machine-specific** — generate it on the machine that builds the release APK you publish.

---

## 7. CI/CD (GitHub Actions)

Workflows live in `.github/workflows/`:

| Workflow | Trigger | Purpose |
|----------|---------|---------|
| `ci.yml` | push / PR to `main`,`develop` | Android Lint, unit tests, debug APK build. Uploads reports + APK artifacts. |
| `release.yml` | push tag `v*` (or manual) | Builds **signed** release APK **and** AAB, generates changelog + SHA256SUMS, publishes a GitHub Release. |
| `codeql.yml` | push/PR to `main` + weekly | CodeQL security scanning (`security-extended`). |
| `pr-checks.yml` | PRs | Dependency review (fails on high-severity CVEs) + Conventional-Commit PR title check. |
| `dependabot.yml` | scheduled | Weekly Gradle + GitHub Actions dependency updates. |

### Required repository secrets (for `release.yml`)

Set these under **Settings → Secrets and variables → Actions**:

| Secret | Description |
|--------|-------------|
| `KEYSTORE_BASE64` | Your release keystore (`.p12`/`.jks`), base64-encoded. |
| `STORE_PASSWORD` | Keystore password. |
| `KEY_ALIAS` | Signing key alias (defaults to `upload` if unset). |
| `KEY_PASSWORD` | Key password. |

Encode your keystore for the `KEYSTORE_BASE64` secret:

```bash
base64 -w0 keystore.p12   # Linux
base64 keystore.p12       # macOS (no -w0)
```

Then cut a release:

```bash
git tag v1.0.0
git push origin v1.0.0
```

### Gradle wrapper note

`gradle-wrapper.jar` is a binary and is generated on first run. If your repo doesn't have it committed, run once locally to create + commit it:

```bash
gradle wrapper --gradle-version 9.1.0 --distribution-type bin
git add gradlew gradlew.bat gradle/wrapper/
git commit -m "build: add Gradle wrapper"
```

The CI workflows also regenerate it automatically if missing, but committing it is the recommended, reproducible practice (and `wrapper-validation` verifies its checksum).

