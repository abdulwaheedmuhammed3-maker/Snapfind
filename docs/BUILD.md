# Android build and APK artifact

The Android client is built with Gradle 8.9, Java 17, Android Gradle Plugin 8.7.3, Kotlin 2.0.21, and Jetpack Compose.

## Local build

Open the repository in Android Studio with Android SDK 35 installed, or run this command in an environment with Gradle 8.9 and Java 17:

```bash
gradle assembleDebug
```

The debug package is written to:

```
app/build/outputs/apk/debug/app-debug.apk
```

## GitHub Actions

The workflow at `.github/workflows/android.yml` builds on pushes to `main` and pull requests targeting `main`. On successful builds it uploads the APK as an artifact named `snapfind-debug-apk`. Open the workflow run in the repository's Actions tab and download that artifact.

The debug APK is for testing. A production release still needs signing configuration, backend deployment, privacy and moderation controls, and device testing.
