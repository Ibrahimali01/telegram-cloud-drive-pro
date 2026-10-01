# ⚡ TeleDrive Pro - Unlimited Cloud Storage over Telegram

<p align="center">
  <img src="docs/images/teledrive_app_hero.jpg" alt="TeleDrive Pro Hero Banner" width="100%" />
</p>

<p align="center">
  <a href="#-features"><img src="https://img.shields.io/badge/Kotlin-1.9.22-purple.svg?style=for-the-badge&logo=kotlin" alt="Kotlin"></a>
  <a href="#-features"><img src="https://img.shields.io/badge/Jetpack%20Compose-M3-4285F4.svg?style=for-the-badge&logo=android" alt="Jetpack Compose"></a>
  <a href="#-architecture"><img src="https://img.shields.io/badge/Telegram-TDLib%201.8.0-26A5E4.svg?style=for-the-badge&logo=telegram" alt="TDLib"></a>
  <a href="#-security--encryption"><img src="https://img.shields.io/badge/Security-AES--256--GCM-E53935.svg?style=for-the-badge&logo=shield" alt="AES-256-GCM"></a>
  <a href="#-license"><img src="https://img.shields.io/badge/License-MIT-4CAF50.svg?style=for-the-badge" alt="MIT License"></a>
</p>

---

## 📖 Overview | نظرة عامة

**TeleDrive Pro** is a modern, privacy-focused, high-performance Android application that turns **Telegram Channels into an unlimited, zero-knowledge cloud drive**. 

Powered by **TDLib (Telegram Database Library)** and native **Bot API integration**, TeleDrive Pro offers client-side **AES-256-GCM encryption**, automatic **file chunking**, **MTProto proxy support**, and **real-time cloud channel synchronization**.

---

## 📸 App Interface | صور ومعاينة التطبيق

<p align="center">
  <img src="docs/images/teledrive_accounts_sync.jpg" alt="Accounts & Channel Sync" width="48%" />
  <img src="docs/images/teledrive_security_vault.jpg" alt="Zero-Knowledge Security Vault" width="48%" />
</p>

---

## ✨ Features | الميزات الرئيسية

| Feature | Description |
| :--- | :--- |
| ☁️ **Unlimited Storage** | Store unlimited files using private Telegram channels as cloud vaults. |
| 🔐 **Zero-Knowledge E2EE** | Client-side **AES-256-GCM** encryption before files leave your device. |
| 🧩 **Smart File Chunking** | Automatically splits large files into 20MB parts with SHA-256 checksum validation. |
| ⚡ **Native TDLib Engine** | Real Telegram C++ engine integration for fast MTProto network protocols. |
| 🔑 **Dual Authentication** | Support for **Phone Numbers (E.164 + OTP + 2FA)** & **Bot Tokens**. |
| 🔄 **Cloud Auto-Sync** | Instantly scan & restore your files from Telegram cloud upon re-login. |
| 🌐 **Built-in MTProto Proxy** | Switch proxies on the fly with live latency (ping) measurement. |
| 🧹 **Strict Privacy Clean** | Instant local data wiping upon account deletion or logout. |

---

## 🔄 System Architecture & Flowcharts | خرائط العمليات والدفق

### 1️⃣ Authentication & Session Flow | عملية تسجيل الدخول والجلسات

```mermaid
flowchart TD
    A[Start TeleDrive App] --> B{Choose Auth Method}
    
    B -->|Phone Number E.164| C[Input Phone e.g. +201xxxxxxxxx]
    C --> D[TDLib Send Phone Number Request]
    D --> E[Receive Telegram OTP Code]
    E --> F{Is 2FA Password Enabled?}
    F -->|Yes| G[Enter 2FA Password]
    F -->|No| H[Authentication Complete]
    G --> H
    
    B -->|Bot Token| I[Input Bot Token from BotFather]
    I --> J[Validate Bot Token Format]
    J --> H
    
    H --> K[Encrypted Storage in Android Keystore]
    K --> L[Save Account in Local Room DB]
```

---

### 2️⃣ File Upload & Encryption Pipeline | خريطة عملية التشفير والرفع السحابي

```mermaid
flowchart LR
    File[Select Local File] --> EncCheck{Enable E2EE Encryption?}
    
    EncCheck -->|Yes| AES[AES-256-GCM Client Encryption]
    EncCheck -->|No| SHA[Calculate SHA-256 Checksum]
    AES --> SHA
    
    SHA --> ChunkCheck{File Size > 20MB?}
    
    ChunkCheck -->|Yes| Split[Split into 20MB Chunks]
    ChunkCheck -->|No| Direct[Single File Stream]
    
    Split --> TDLib[TDLib / Bot API Upload Stream]
    Direct --> TDLib
    
    TDLib --> TG[Telegram Cloud Channel -100...]
    TG --> DB[Save Metadata in Local Room DB]
    DB --> UI[Update Drive Dashboard UI]
```

---

### 3️⃣ Real-Time Cloud Channel Sync & Restoration | خريطة مزامنة القناة واستعادة الملفات

```mermaid
flowchart TD
    User[User Clicks Sync Channel 🔄 / Re-logs In] --> Vault[Get Vault Channel ID]
    Vault --> Resolve[Resolve Channel ID via TDLib]
    Resolve --> History[Fetch Chat History via GetChatHistory]
    
    History --> Loop[Parse Telegram Cloud Messages]
    Loop --> CheckDoc{Contains Document?}
    
    CheckDoc -->|Yes| ParseMeta[Extract Metadata, SHA-256 & E2EE Status]
    CheckDoc -->|No| Skip[Skip Message]
    
    ParseMeta --> CheckExists{Exists in Local DB?}
    CheckExists -->|No| Insert[Insert DriveFileEntity into Room DB]
    CheckExists -->|Yes| Skip
    
    Insert --> Loop
    Skip --> Loop
    
    Loop --> Finish[Update Total Vault Files & Storage Stats]
    Finish --> UI[Display Restored Cloud Files]
```

---

### 4️⃣ Logout & Privacy Purge Flow | خريطة مسح البيانات والخصوصية

```mermaid
flowchart TD
    ClickDelete[Click Delete Account / Logout] --> LogoutTDLib[Log Out TDLib Native Session]
    LogoutTDLib --> RemoveKeys[Remove Encrypted Keys from Android Keystore]
    RemoveKeys --> DeleteAccountFiles[Delete Files associated with Account]
    DeleteAccountFiles --> DeleteVaultFiles[Delete Files associated with Channels]
    DeleteVaultFiles --> PurgeDB[Remove Account & Vault Entities from Room DB]
    PurgeDB --> EmptyUI[Clear HomeScreen & Return 0 Files]
```

---

## 🛠️ Technology Stack | التقنيات المستخدمة

- **Language**: 100% Kotlin
- **UI Framework**: Jetpack Compose + Material Design 3 (M3)
- **Telegram Engine**: TDLib 1.8.0 C++ Native Client (JNI)
- **Local Persistence**: Room Database + KSP
- **Asynchronous Flow**: Kotlin Coroutines & StateFlow
- **Security & Crypto**: Android Keystore + AES-256-GCM + SHA-256
- **Networking**: Retrofit 2 + OkHttp 4 for Bot API & MTProto Proxy pinging

---

## 🚀 Getting Started | كيفية الاستخدام

### 1. Connecting a Telegram Account
1. Open **TeleDrive Pro** and navigate to **Accounts (الحسابات)**.
2. Click **Connect Account (+)**.
3. Choose either **Phone Number** (enter international E.164 format e.g. `+201234567890`) or **Bot Token** (obtained from `@BotFather`).
4. Enter the verification code (OTP) or password when prompted.

### 2. Linking a Storage Vault Channel
1. Create a **Private Channel** in your Telegram app.
2. Add your account/bot as an **Administrator** with **Post Messages** permission.
3. In TeleDrive Pro, click **Add Channel (+)**, enter your channel username (e.g. `@my_vault`) or channel ID, and click Save.

### 3. Uploading & Syncing
- Tap **Upload File (رفع ملف)** from the top header or FAB to select any file.
- Tap **Sync Channel (مزامنة 🔄)** at any time or on any device to instantly pull all your stored cloud files from Telegram's servers.

---

## 📄 License | الترخيص

This project is open-source and released under the [MIT License](LICENSE).

---

<p align="center">
  Crafted with ❤️ for Privacy and Unlimited Cloud Freedom.
</p>
