# Android build and APK artifact

Snapfind is built with Kotlin, Jetpack Compose, Gradle 8.9, Java 17, and Android Gradle Plugin 8.7.3.

## Configure Supabase locally

Create or open your Supabase project. In **Project Settings → API Keys**, copy the project's publishable key (preferred) or legacy anon key. Copy the Project URL from **Integrations → Data API**.

Add these lines to your local `local.properties` file in the repository root. This file is git-ignored:

```properties
SUPABASE_URL=https://YOUR_PROJECT_REF.supabase.co
SUPABASE_ANON_KEY=sb_publishable_YOUR_PUBLIC_KEY
```

The app accepts a Supabase publishable key (recommended) or legacy anon key. A publishable key is public and can be extracted from the APK, so it is not a security boundary. **Never use a Supabase secret key or service-role key in the Android app.** The app sends this key in the `apikey` header and sends a user's access token separately as `Authorization: Bearer`; the database's grants and RLS policies enforce access. <https://supabase.com/docs/guides/getting-started/api-keys>

## Build locally

Open the project in Android Studio with Android SDK 35 installed, or use Gradle 8.9 and Java 17:

```bash
gradle assembleDebug
```

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

Without the two Supabase values, the app remains in clearly-labelled demo mode and does not connect to a backend.

## Configure GitHub Actions

In the repository's **Settings → Secrets and variables → Actions**:
- Add repository variable `SUPABASE_URL` containing the project URL.
- Add repository secret `SUPABASE_ANON_KEY` containing the publishable or legacy anon key.

The workflow passes those values to Gradle at build time. Do not put a secret/service-role key in either setting.

Successful runs upload `snapfind-debug-apk`. Find it in the repository's Actions tab under the relevant workflow run.

A debug APK is for testing. Production release still requires signed release configuration, deployed backend, full moderation operations, privacy review, and device testing.
