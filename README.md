# 📄 DocuScan OCR Sandbox

[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](https://opensource.org/licenses/MIT)
[![Compliance: HIPAA & GDPR](https://img.shields.io/badge/Compliance-HIPAA%20%7C%20GDPR-blue.svg)](#privacy--compliance)
[![Platform: Android](https://img.shields.io/badge/Platform-Android-3DDC84.svg?logo=android)](#technical-stack)

An elegant, **100% offline-first, local-only document scanning, OCR extraction, and cryptographic storage suite** for Android. Designed for professionals in clinical, legal, and enterprise settings where remote cloud AI leaks present compliance hazards.

DocuScan OCR runs entirely on-device with zero external API dependencies, ensuring total data sovereignty under **HIPAA** and **GDPR Article 6** regulations.

---

## ✨ Features

*   **⚡ Local-Only High-Precision OCR:** 100% on-device text character recognition matching complex layouts, tables, and handwritten documents. Zero remote networks or AI servers contacted.
*   **🔒 Secure Folder Lockboxes:** Protect sensitive records with localized military-grade **AES-GCM encryption** keys. Re-keys, locks, and unlocks directories on the fly.
*   **📐 Adaptive Perspective Warp Correction:** Detect doc edges and apply pixel-level correction, rotation, and custom visual density filters (Monochrome, High Contrast, Shadow Removal).
*   **📲 Direct Smart Sharing & Multi-Format Exports:** Instantly export scans to **PDF, DOCX, or Plain Text** with metadata headers. Share decrypted OCR records directly from the main list with one click.
*   **🛡️ Cryptographic Tamper-Proof Audit Logging:** Tracks all data modifications (`CREATE`, `READ`, `UPDATE`, `DELETE`, `EXPORT`, `DECRYPT`) with persistent local journaling for HIPAA audit trails.
*   **☁️ Self-Hosted Multi-Cloud Sync:** Built-in offline configurations for **Cloudflare R2, Google Drive, and Dropbox**. Sync is 100% user-owned; no proprietary backend is used.

---

## 🎨 Visual Preview & Theme

The user interface utilizes a premium **Minimalist Glassmorphic Design System** built on Material Design 3. Custom translucent Glass cards, dark system schemes, and high-contrast color codes allow rapid visual scanning.

*   **Primary color token:** Bright modern Amber/Emerald Accents
*   **Background token:** Matte Frosted Glass Overlay (`GlassCard` and `GlassBackground`)
*   **Canonical support:** Adaptive layouts dynamically resizing across **Compact**, **Medium**, and **Expanded** (tablets/foldables) screens.

---

## 🛠️ Technical Stack & Architecture

Built with modern, production-ready Android components:

*   **Language:** 100% Kotlin
*   **UI Toolkit:** Jetpack Compose (Material 3)
*   **Architecture:** Clean Architecture / MVVM (Model-View-ViewModel)
*   **Database:** Room Database with SQLite for encrypted structured indexing
*   **Key Storage:** Android Keystore System (`MasterKeys`)
*   **Asynchronous Processing:** Kotlin Coroutines and StateFlow
*   **Image Handling:** Custom `DocumentFilterProcessor` with Android `Canvas` & `Paint`

---

## ☁️ Integrations Guide (User-Configured Cloud)

To prevent centralized data aggregation, **DocuScan OCR Sandbox** contains no hardcoded backend. All sync is configured client-side directly by the end-user.

### 1. Cloudflare R2 Storage Setup (S3 API Compliant)
Cloudflare R2 offers high-speed, zero-egress fee object storage.
1.  **Create Bucket:** Create a bucket in your Cloudflare dashboard (e.g. `docuscan-scans`).
2.  **Generate API Tokens:** Navigate to R2 -> Manage R2 API Tokens. Generate read/write credentials.
3.  **Define Configuration:** Inside the app's **Cloud Syncer** panel, input:
    *   `Endpoint URL`: `https://<account_id>.r2.cloudflarestorage.com`
    *   `Bucket Name`: `docuscan-scans`
    *   `Access Key ID`: `[Your Access Key]`
    *   `Secret Access Key`: `[Your Secret Key]`
4.  Scanned documents will sync locally and upload to your R2 bucket using client-side S3 client blocks.

### 2. Google Drive Backup
1.  **Configure API Console:** Set up an OAuth client in your Google Cloud Console.
2.  **Enable Drive API:** Under your project, add the `https://www.googleapis.com/auth/drive.appdata` scope (limits app access to its private folder).
3.  **App Authorization:** Click **Connect Google Drive** in Settings. Authenticate and approve direct backups.

### 3. Dropbox Integration
1.  **App Console:** Register a developer application on the Dropbox App Console.
2.  **Scopes:** Enable `files.metadata.write`, `files.content.write`, and `files.content.read`.
3.  **Access Code:** Provide your short-lived client token or authenticate via Dropbox OAuth inside the app to sync file transcripts seamlessly.

---

## 🛡️ Privacy & Legal Compliance

### HIPAA Standard Compliance (PHI)
DocuScan is engineered for Protected Health Information (PHI):
*   **Local AES-256 Encryption:** All scans saved under private folders are encrypted at rest prior to disk writing.
*   **Audit Logging:** Logs all database decrypt operations with operator metrics. Logs are permanent and saved in Room.
*   **Complete Discretion:** No background telemetry, crash reporters, or external tracking servers are integrated.

### GDPR Article 6 Compliance
*   Data subjects retain total control. Your scans are processed strictly local to your terminal under consent constraints.
*   Includes a **Wipe All Storage** emergency function executing an offline shredding write to delete directories completely.

---

## 🚀 Building the Code

1.  Clone this repository:
    ```bash
    git clone https://github.com/Rishav7324/DocuScan-OCR.git
    cd DocuScan-OCR
    ```
2.  Import the project in **Android Studio (Ladybug or higher)**.
3.  Run compilation and build:
    ```bash
    gradle :app:assembleDebug
    ```
4.  *Note: To run screenshots and Robolectric unit tests locally on your JVM, run:*
    ```bash
    gradle :app:testDebugUnitTest
    ```

---

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details. Created and maintained as an open-source sandbox.
