# Snapfind 👻

Snapfind is an independent, opt-in directory for discovering Snapchat usernames that people choose to publish.

## Android app

The first Android prototype is built with Kotlin and Jetpack Compose.

Implemented in this version:
- Search preview listings by display name, username, country, or interests.
- Filter preview listings by country.
- Create and edit a public profile with a display name, Snapchat username, country, interests, and optional bio.
- Hide or show your own listing, or delete it.
- Copy a listed username.
- Open a report-preview interaction.

## Current prototype limitations

**This version does not yet provide global discovery.** The discovery screen contains clearly labelled sample profiles. A profile you create is stored locally on your device using Android preferences; it is not uploaded or visible to other users. Authentication, a shared database, real reporting/moderation, and production privacy controls still need to be implemented before public launch.

## Build a debug APK

A GitHub Actions workflow is included at `.github/workflows/android.yml`. It runs on pushes to `main` and pull requests, builds the debug APK, and uploads it as the `snapfind-debug-apk` workflow artifact.

The project uses:
- Android Gradle Plugin 8.7.3
- Kotlin 2.0.21
- Jetpack Compose
- Gradle 8.9
- Java 17

To build locally with Gradle 8.9 installed:

```bash
gradle assembleDebug
```

The APK is generated at `app/build/outputs/apk/debug/app-debug.apk`. Pull requests to `main` run the same build check.

## Privacy and safety principles

- Only account owners may submit their own Snapchat usernames.
- No scraping, enumeration, or harvesting of Snapchat accounts.
- Users must be able to edit, hide, or delete their listings.
- The production service must include reporting, blocking, and moderation.
- Do not ask for or store Snapchat passwords.
- Snapfind is not affiliated with or endorsed by Snapchat.
