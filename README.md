# 📊# ThingSpeak Monitor Widget APK

<p align="center">
  <img src="https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Android" />
  <img src="https://img.shields.io/badge/Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin" />
  <img src="https://img.shields.io/badge/Gradle-02303A?style=for-the-badge&logo=gradle&logoColor=white" alt="Gradle" />
  <img src="https://img.shields.io/badge/License-MIT-yellow.svg?style=for-the-badge" alt="License" />
</p>

A professional Android application designed for real-time monitoring of **ThingSpeak** IoT channels. Built with a focus on performance, reactive UI (Jetpack Compose), and deep system integration via advanced Home Screen Widgets.

---

## ✨ Key Features
*   **📱 Dynamic Widgets**: Built with **Jetpack Glance** for a modern, reactive home screen experience.
*   **📈 Rich Analytics**: Smooth data visualization using **MPAndroidChart**.
*   **🌓 Adaptive UI**: Full support for **Dark Mode** and Material 3 design principles.
*   **🔄 Background Sync**: Optimized data fetching using **WorkManager** to minimize battery drain.
*   **💾 Offline First**: Local caching with **Room Database** for seamless access to recent data.

## 🛠️ Tech Stack
| Category | Technology |
| :--- | :--- |
| **Language** | ![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?style=flat&logo=kotlin&logoColor=white) |
| **UI Framework** | ![Jetpack Compose](https://img.shields.io/badge/Compose-4285F4?style=flat&logo=jetpackcompose&logoColor=white) |
| **Architecture** | Clean Architecture (MVI/MVVM), Hilt (DI) |
| **Networking** | Retrofit, OkHttp, Kotlin Serialization |
| **Widgets** | Jetpack Glance |
| **Database** | Room |

## 📁 Project Structure
*   `feature/dashboard`: Main monitoring hub for your channels.
*   `feature/chart`: Advanced analytics and interactive historical charts.
*   `feature/widget`: Glance implementation for high-quality Home Screen Widgets.
*   `core/data`: Repository layer and ThingSpeak API synchronization logic.

## ⚙️ Setup & Configuration
The project is fully integrated with **GitHub Actions (CI/CD)** for automated builds.

### Local Development (`local.properties`)
For security, sensitive keys and passwords must remain outside of version control. Configure your `local.properties` (or `/.bin/local.properties`) as follows:
```properties
releaseStoreFile=../.bin/thingspeak-release.jks
releaseStorePassword=YOUR_PASSWORD
releaseKeyAlias=thingspeak
releaseKeyPassword=YOUR_KEY_PASSWORD
```

### GitHub Actions CI/CD
To enable automated signing on GitHub, add the following to your **Repository Secrets**:
1.  `RELEASE_STORE_FILE_BASE64`: Full Base64-encoded string of your `.jks` file.
2.  `RELEASE_STORE_PASSWORD`
3.  `RELEASE_KEY_ALIAS`: `thingspeak`
4.  `RELEASE_KEY_PASSWORD`

---
*Optimized for low resource usage and high responsiveness.*
