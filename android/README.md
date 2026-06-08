# Money Manager — Android App

Native Android port of the Money Manager web application.  
Built with **Kotlin · Jetpack Compose · Room (SQLite) · Hilt**.

---

## Architecture

```
android/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       └── java/com/moneymanager/
│           ├── MoneyManagerApp.kt              ← @HiltAndroidApp
│           ├── MainActivity.kt
│           ├── data/                           ← Local DB & Repositories
│           ├── domain/                         ← Business logic & UseCases
│           ├── notifications/                  ← Bank notification interception
│           ├── di/                             ← Dependency Injection
│           └── ui/                             ← Jetpack Compose UI
├── build.gradle.kts
├── settings.gradle.kts
└── gradle/
    ├── libs.versions.toml
    └── wrapper/gradle-wrapper.properties
```

---

## Core Feature: Auto-Logging via Notifications

The app includes a `BankNotificationService` that intercepts push notifications from banking apps.
- **Privacy First**: It uses a strict package whitelist (Scotiabank, Santander, BCI, etc.). It ignores your private messages, emails, and social media.
- **Smart Parsing**: A domain-level Use Case uses Regex heuristics to extract amounts and currencies from the notification text, automatically staging them for review.

---

## Building & Deploying

This section guides you through turning the source code into a functional app on your phone.

### Prerequisites

1.  **Android Studio**: Download the latest version (Hedgehog or newer). It includes the compilers and tools needed.
2.  **JDK 17**: The Java Development Kit required to run the build system (Gradle).
3.  **Physical Android Device**: (Recommended) To test notification interception, as emulators don't receive real bank pushes.

---

### Phase 1: Development Build (Debug)

**What is it?** A version of the app optimized for developers. It includes debug symbols and allows you to attach a debugger to see what's happening inside.
**When to use?** Every day while writing code or testing new features.

1.  Open the `android/` folder in Android Studio.
2.  Wait for the "Gradle Sync" to finish (this downloads dependencies).
3.  Connect your phone via USB and enable **USB Debugging** in Developer Options.
4.  Press the **Green Play Button** (Shift+F10) in the top toolbar.

---

### Phase 2: Packaging for Release (Signed APK)

If you want to install the app permanently on your device or share it, you must "Sign" it.

#### 1. Generate a Signing Keystore (One-time)
**Why?** Android requires all apps to be digitally signed before they can be installed. A "Keystore" is a secure file that holds your digital identity. It proves that the app was created by you and hasn't been tampered with.
**What to do?** Run this command in your terminal (replace `YOUR_PASSWORD`):

```bash
keytool -genkeypair \
  -alias money-manager \
  -keyalg RSA -keysize 2048 \
  -validity 10000 \
  -keystore money-manager-release.jks \
  -storepass YOUR_STORE_PASSWORD \
  -keypass  YOUR_KEY_PASSWORD
```
**Warning**: Keep this `.jks` file safe. If you lose it, you cannot update the app on your phone without uninstalling and losing your data.

#### 2. Configure Build Credentials
**Why?** You need to tell the build system (Gradle) where your Keystore is and what the passwords are.
**How?** Set environment variables so you don't accidentally save passwords in the code:

```bash
export RELEASE_STORE_PASSWORD=YOUR_STORE_PASSWORD
export RELEASE_KEY_PASSWORD=YOUR_KEY_PASSWORD
```

Then, ensure the `signingConfigs` in `app/build.gradle.kts` are uncommented to use these variables.

#### 3. Build the APK
**What is an APK?** The "Android Package" file — essentially a `.zip` containing the compiled code and resources.
**How?**
```bash
./gradlew assembleRelease
```
The output will be at: `app/build/outputs/apk/release/app-release.apk`

---

### Phase 3: Installation

#### Using ADB (Command Line)
**What is ADB?** The Android Debug Bridge. It's a tool that lets your computer talk to your phone.
**How?**
```bash
# Ensure your phone is connected and recognized
adb devices

# Install the app
adb install app/build/outputs/apk/release/app-release.apk
```

#### Manual Install
1.  Copy the `app-release.apk` to your phone's storage (via Google Drive, USB, or Telegram).
2.  Open the file on your phone.
3.  If prompted, allow "Install from Unknown Sources".

---

## Database & Storage

The app uses **Room** (a layer over SQLite).
- **Data Location**: Stored locally on your device. No cloud sync is currently implemented for maximum privacy.
- **Migrations**: If you change the database schema, you must increment the version in `AppDatabase.kt`.

---

## License

Private / Personal use.
