# Snapfind 👻

Snapfind is an independent, opt-in directory for discovering Snapchat usernames that people choose to publish.

## Android app

The Android client is built with Kotlin and Jetpack Compose.

Implemented:
- Demo mode when no Supabase project is configured.
- Email/password account creation and sign-in using Supabase Auth.
- Encrypted session storage using the Android Keystore.
- Live discovery search by display name, username, country, bio, and interests.
- Country filtering and profile result cards.
- Owner profile creation and editing.
- Public visibility toggle and listing deletion.
- Username copying and report submission.

## Backend

The Supabase migrations are in `supabase/migrations/`. Apply the three SQL files in filename order. The schema uses RLS, restricted Data API grants, explicit public-profile opt-in, and an RLS-aware server-side search function.

The Android app is configured with the Supabase Project URL and publishable/anon key at build time. See [docs/BUILD.md](docs/BUILD.md) and [supabase/README.md](supabase/README.md).

**Do not put a Supabase secret or service-role key in the app.** The public key is not an authorization boundary; RLS and database grants must remain correct.

## Current limitations

- No real Supabase project credentials have been added to this repository yet. Until credentials are supplied to the build, the installed app runs in labelled demo mode.
- The Android block-management UI is not connected yet.
- Report submission is enabled, but moderation review/management still needs a trusted server-side workflow.
- Account deletion, abuse rate limits, automated database policy tests, and production release signing remain outstanding.

## Build a debug APK

GitHub Actions builds the Android project on pushes to `main` and pull requests and uploads `snapfind-debug-apk` when successful.

For local builds, use Java 17, Gradle 8.9, Android SDK 35, and `gradle assembleDebug`.

## Privacy and safety

- Only account owners may submit and publish their own handles.
- Listings are private until the owner explicitly enables public visibility.
- Users can hide or delete their own listing.
- No scraping, enumeration, or harvesting of Snapchat accounts.
- Snapfind never asks for Snapchat passwords.
- Snapfind is not affiliated with or endorsed by Snapchat.
