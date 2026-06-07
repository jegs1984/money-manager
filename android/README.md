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
│           ├── data/
│           │   ├── db/
│           │   │   ├── AppDatabase.kt          ← Room v1 + seed callback
│           │   │   └── dao/                    ← PeriodDao, CategoryDao,
│           │   │                                  BudgetItemDao, TransactionDao,
│           │   │                                  StagingTransactionDao,
│           │   │                                  StagingCCTransactionDao
│           │   ├── entity/                     ← 6 @Entity data classes
│           │   ├── repository/
│           │   │   └── FinanceRepository.kt    ← single source of truth
│           │   └── parser/
│           │       ├── DatParser.kt            ← Scotiabank .dat (pure Kotlin)
│           │       └── XlsParser.kt            ← Scotiabank CC .xls (Apache POI)
│           ├── di/
│           │   └── DatabaseModule.kt           ← Hilt @Module
│           └── ui/
│               ├── theme/Theme.kt
│               ├── navigation/
│               │   ├── Routes.kt
│               │   └── NavGraph.kt
│               └── screens/
│                   ├── dashboard/              ← DashboardScreen + ViewModel
│                   ├── periods/                ← list + form
│                   ├── categories/             ← list + form
│                   ├── budgetitems/            ← list + form
│                   ├── transactions/           ← list + form
│                   └── importflow/             ← DAT import, DAT staging review,
│                                                  CC import, CC staging review
├── build.gradle.kts
├── settings.gradle.kts
└── gradle/
    ├── libs.versions.toml
    └── wrapper/gradle-wrapper.properties
```

---

## Feature map (web → Android)

| Web screen | Android screen |
|---|---|
| Dashboard (safe-to-spend, burn rate, budget bars) | `DashboardScreen` |
| Periods CRUD | `PeriodsScreen` + `PeriodFormScreen` |
| Categories CRUD | `CategoriesScreen` + `CategoryFormScreen` |
| Budget Items CRUD | `BudgetItemsScreen` + `BudgetItemFormScreen` |
| Transactions list / form | `TransactionsScreen` + `TransactionFormScreen` |
| Import Scotiabank `.dat` | `ImportDatScreen` → `StagingReviewScreen` |
| Import Scotiabank CC `.xls` | `ImportCCScreen` → `StagingCCReviewScreen` |

Pre-seeded categories (64 entries) are inserted on first launch via a `RoomDatabase.Callback`.

---

## Build — development

### Prerequisites

| Tool | Minimum version |
|---|---|
| Android Studio | Hedgehog 2023.1.1 |
| JDK | 17 |
| Android SDK | API 34 (compile), API 26 (min) |

### Steps

```bash
# Open the android/ folder in Android Studio
# File → Open → .../money-manager/android
# Wait for Gradle sync, then Run → Run 'app'  (Shift+F10)
```

---

## Build a release APK

### 1 — Generate a signing keystore (one-time)

```bash
keytool -genkeypair \
  -alias money-manager \
  -keyalg RSA -keysize 2048 \
  -validity 10000 \
  -keystore money-manager-release.jks \
  -storepass YOUR_STORE_PASSWORD \
  -keypass  YOUR_KEY_PASSWORD
```

Store `money-manager-release.jks` safely — it cannot be recovered if lost.

### 2 — Supply signing credentials

Preferred: environment variables (never commit secrets):

```bash
export RELEASE_STORE_PASSWORD=YOUR_STORE_PASSWORD
export RELEASE_KEY_PASSWORD=YOUR_KEY_PASSWORD
```

Then uncomment the `signingConfigs` block in `app/build.gradle.kts`:

```kotlin
signingConfigs {
    create("release") {
        storeFile     = file("money-manager-release.jks")
        storePassword = System.getenv("RELEASE_STORE_PASSWORD") ?: ""
        keyAlias      = "money-manager"
        keyPassword   = System.getenv("RELEASE_KEY_PASSWORD") ?: ""
    }
}
// ...
buildTypes {
    release {
        // ...
        signingConfig = signingConfigs.getByName("release")
    }
}
```

### 3 — Build the signed APK

```bash
cd android
./gradlew assembleRelease
```

Output: `app/build/outputs/apk/release/app-release.apk`

### 4 — (Optional) AAB for Play Store

```bash
./gradlew bundleRelease
# Output: app/build/outputs/bundle/release/app-release.aab
```

### 5 — Install directly on a connected device

```bash
adb install app/build/outputs/apk/release/app-release.apk
# or via Gradle:
./gradlew installRelease
```

---

## Database

Room manages a local SQLite database (`money_manager.db`) stored in the app's private data directory — no setup required, no server needed.

Schema version: **1**. Future migrations go in a `Migrations.kt` file and are registered in `AppDatabase.build()`.

---

## File import

The app uses the Android Storage Access Framework (`ActivityResultContracts.OpenDocument`) — no storage permissions required on API 33+. On API 26–32 `READ_EXTERNAL_STORAGE` is declared with `maxSdkVersion=32`.

- **DatParser** — pure Kotlin, zero dependencies, replicates `services.parse_scotiabank_statement`.
- **XlsParser** — uses Apache POI 3.17 (HSSF). No LibreOffice needed on the device.

---

## License

Private / personal use.
