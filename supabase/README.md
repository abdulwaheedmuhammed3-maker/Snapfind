# Supabase setup

The first database migration is in `migrations/202611250001_initial_schema.sql`.

## Apply the schema

1. Create a Supabase project.
2. Open **SQL Editor** in the Supabase dashboard.
3. Run the migration file.
4. Confirm that `profiles`, `blocks`, `reports`, and the `public_profiles` view exist.

The schema starts with public profile visibility turned off. A profile becomes discoverable only when its signed-in owner explicitly sets `is_public = true`.

## Security notes

- The app must use Supabase Auth and the user's session for profile writes.
- Never put the Supabase service-role key in the Android app.
- Row-level security limits profile writes to the signed-in owner.
- Report records can be submitted by authenticated users, but only the reporter can read their own reports. Moderation must run through a trusted server-side workflow.
- The public view exposes only discovery fields and still runs with the caller's row-level security context.
- Blocking rules are applied to profile discovery in both directions.

The current Android app is still local-only. This schema is the backend foundation; production authentication, Supabase client integration, rate limiting, moderation operations, and account-deletion handling remain to be implemented.
