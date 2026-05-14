# Poor Men's Kindle

An Android e-book reader application built with Jetpack Compose, designed for a seamless reading experience with both online library sync and offline EPUB support.

## 🌟 Features

- **Online Library**: Browse and read books from a remote server.
- **Offline Reading**: Support for reading local EPUB files.
- **Advanced Reader**: Customizable reading experience with scroll progress tracking.
- **Highlights**: Save and manage highlights from your books.
- **Admin Dashboard**: Manage users, books, and requests (for admin users).
- **Synchronization**: Offline sync manager to keep your reading progress and library up to date.
- **Multilingual Support**: Supports English and Hebrew.

## 🛠 Tech Stack

- **Language**: [Kotlin](https://kotlinlang.org/)
- **UI Framework**: [Jetpack Compose](https://developer.android.com/compose)
- **Networking**: [Retrofit](https://square.github.io/retrofit/) & [OkHttp](https://square.github.io/okhttp/)
- **Database**: [Room Persistence Library](https://developer.android.com/training/data-storage/room)
- **Image Loading**: [Coil](https://coil-kt.github.io/coil/)
- **EPUB Parsing**: [epublib](https://github.com/psiegman/epublib)
- **HTML Parsing**: [JSoup](https://jsoup.org/)
- **Architecture**: MVVM (implied by Compose patterns)

## 🚀 Getting Started

### Requirements

- Android Studio (latest version recommended)
- JDK 11
- Android SDK 26+ (Minimum API level)

### Setup & Run

1. **Clone the repository**:
   ```bash
   git clone <repository-url>
   cd PoorMensKindle
   ```

2. **Open in Android Studio**:
   Open the project folder in Android Studio.

3. **Configure Environment**:
   - The API base URL is currently hardcoded in `com.PoorMenKindle.android.network.NetworkManager`.
   - TODO: Move `BASE_URL` to a `BuildConfig` or environment variable.

4. **Build and Run**:
   - Connect an Android device or start an emulator.
   - Click the **Run** button in Android Studio or use Gradle:
     ```bash
     ./gradlew assembleDebug
     ```

## 📜 Scripts

- `./gradlew assembleDebug`: Build the debug APK.
- `./gradlew test`: Run unit tests.
- `./gradlew connectedAndroidTest`: Run instrumentation tests on a device.
- `./gradlew lint`: Run lint checks.

## 📁 Project Structure

```text
app/
├── src/
│   ├── main/
│   │   ├── java/com/PoorMenKindle/android/
│   │   │   ├── data/local/      # Room database, DAOs, and OfflineSyncManager
│   │   │   ├── network/         # Retrofit API services and models
│   │   │   ├── ui/
│   │   │   │   ├── navigation/  # Jetpack Compose Navigation
│   │   │   │   ├── screens/     # UI screens (Login, Library, Reader, Admin, etc.)
│   │   │   │   └── theme/       # Compose theme and styling
│   │   ├── assets/fonts/        # Custom fonts for the reader
│   │   └── res/                 # Android resources (strings, drawables, etc.)
│   └── test/                    # Unit tests
└── build.gradle.kts             # App-level build configuration
```

## 🔑 Environment Variables & Config

- `NetworkManager.BASE_URL`: The endpoint for the backend service, insert the one your server is running on.
- `SharedPreferences ("BookWormHolePrefs")`: Stores JWT tokens and user roles for session management.

## 🧪 Tests

- **Unit Tests**: Located in `app/src/test`. Run with `./gradlew test`.
- **Instrumentation Tests**: Located in `app/src/androidTest`. Run with `./gradlew connectedAndroidTest`.

## 📄 License

TODO: Add license information.

---

*Note: This project is actively developed. See the codebase for the latest changes and features.*
