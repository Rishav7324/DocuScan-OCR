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

## 7. CI/CD & release (GitHub Actions) — step by step

There is **one** workflow: `.github/workflows/release.yml`.

| Workflow | Trigger | Purpose |
|----------|---------|---------|
| `release.yml` | push tag `v*` **or** manual dispatch | Builds a **signed release APK**, generates `SHA256SUMS.txt`, and publishes a GitHub Release with both files attached. |

It does **not** build an AAB, run lint, or run tests — it is a pure release pipeline. Do local `gradle test`/`lint` before tagging.

### Step 1 — Generate a release keystore (once, keep it forever)

You sign every release with the **same** keystore. Lose it and you can never update the app under the same signature. Store the file + passwords in a password manager.

```bash
keytool -genkeypair -v \
  -storetype PKCS12 \
  -keystore release-keystore.p12 \
  -alias upload \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass "CHANGE_ME_STORE" \
  -keypass  "CHANGE_ME_KEY" \
  -dname "CN=Rishav Raj, O=DocuScan OCR, C=IN"
```

- `-alias upload` → this is your `KEY_ALIAS` (the gradle default is also `upload`).
- `-storepass` → `STORE_PASSWORD`.
- `-keypass` → `KEY_PASSWORD`.
- `-validity 10000` → ~27 years; must exceed your intended app lifetime.

### Step 2 — Base64-encode the keystore

```bash
base64 -w0 release-keystore.p12 > keystore.b64   # Linux
base64 release-keystore.p12      > keystore.b64   # macOS (no -w0)
```

Copy the entire contents of `keystore.b64` — that string is the `KEYSTORE_BASE64` secret.

### Step 3 — Add the four repository secrets

**Settings → Secrets and variables → Actions → New repository secret**, add all four:

| Secret | Value |
|--------|-------|
| `KEYSTORE_BASE64` | contents of `keystore.b64` from Step 2 |
| `STORE_PASSWORD` | the `-storepass` you chose |
| `KEY_ALIAS` | `upload` (or your `-alias`) |
| `KEY_PASSWORD` | the `-keypass` you chose |

> If any secret is missing the workflow fails fast: the decode step errors with `KEYSTORE_BASE64 secret not set`.

### Step 4 — Commit the Gradle wrapper (recommended, once)

`gradle-wrapper.jar` is a binary. The workflow regenerates it if missing, but committing it is reproducible and faster:

```bash
gradle wrapper --gradle-version 9.1.0 --distribution-type bin
git add gradlew gradlew.bat gradle/wrapper/
git commit -m "build: add Gradle wrapper"
git push
```

### Step 5 — Bump the version before each release

In `app/build.gradle.kts` → `defaultConfig`:

```kotlin
versionCode = 3        // must increase every Play upload
versionName = "2.1.0"  // human-facing
```

Commit and push before tagging.

### Step 6 — Cut a release

Tag names must start with `v`. A `-rc`/`-beta` suffix marks it a pre-release automatically.

```bash
git tag v2.0.0
git push origin v2.0.0
```

Or from the UI: **Actions → Release → Run workflow** → enter the tag (e.g. `v2.0.0`).

### Step 7 — Verify the result

1. **Actions** tab → the `Release` run is green.
2. **Releases** page → new release `DocuScan OCR v2.0.0` with `DocuScan-v2.0.0.apk` + `SHA256SUMS.txt`.
3. Verify the download locally:

   ```bash
   sha256sum -c SHA256SUMS.txt
   ```

### Local dry-run before tagging

Reproduce the CI build on your machine to catch failures early:

```bash
export KEYSTORE_PATH="$PWD/release-keystore.p12"
export STORE_PASSWORD="CHANGE_ME_STORE"
export KEY_ALIAS="upload"
export KEY_PASSWORD="CHANGE_ME_KEY"
gradle :app:assembleRelease --stacktrace
# output: app/build/outputs/apk/release/*.apk
```

If `KEYSTORE_PATH` points to a missing file, gradle falls back to the debug signing config so the build still completes (unsigned-for-release) — useful for compile checks, **not** for publishing.

