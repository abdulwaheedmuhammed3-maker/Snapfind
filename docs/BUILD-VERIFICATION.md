# Build verification

This branch triggers the Android CI workflow against the current Snapfind source. The build should verify Kotlin compilation, Supabase integration classes, and packaging into a debug APK.

The run will use demo-mode configuration unless repository Actions variables/secrets are present.
