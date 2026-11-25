# Integration build verification

This branch is a build-verification trigger for the current Supabase-enabled Android client.

The build must compile the Kotlin client, include the generated Supabase BuildConfig fields, and package a debug APK. The APK will remain in demo mode until the repository build is supplied with a project URL and a publishable/legacy anon key.

A successful Android build does not mean the database migrations have been applied or that remote Auth and RLS have been exercised against a live Supabase project.
