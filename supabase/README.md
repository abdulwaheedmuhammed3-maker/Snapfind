# Supabase setup

For the easiest setup, open `supabase/setup.sql` and run the entire file once in the Supabase SQL Editor. It contains all five migrations in order.

The individual migrations are also available if you prefer to apply them separately:

1. `migrations/202611250001_initial_schema.sql`
2. `migrations/202611250002_data_api_grants.sql`
3. `migrations/202611250003_directory_search.sql`
4. `migrations/202611250004_split_profile_discovery_policies.sql`
5. `migrations/202611250005_private_block_relationships.sql`

## Create and configure the project

1. Create a Supabase project.
2. In **Authentication → Providers**, enable Email.
3. Keep email confirmation enabled for normal user sign-up. Snapfind displays a confirmation message and the user can sign in after confirming.
4. Open the **SQL Editor** and run all three migration files in order.
5. Confirm that `profiles`, `blocks`, `reports`, the `public_profiles` view, and the `search_public_profiles` RPC exist.
6. In **Project Settings → API Keys**, copy the publishable key (or legacy anon key). Copy the Project URL from **Integrations → Data API**.

## Configure the Android build

Add the Project URL and publishable/anon key to the ignored local `local.properties` file:

```properties
SUPABASE_URL=https://YOUR_PROJECT_REF.supabase.co
SUPABASE_ANON_KEY=sb_publishable_YOUR_PUBLIC_KEY
```

For GitHub Actions builds, configure the repository variable `SUPABASE_URL` and repository secret `SUPABASE_ANON_KEY`.

**Never place a Supabase secret key or service-role key in the Android app or GitHub Actions variables.** These keys bypass normal row-level security controls and belong only in trusted server-side services.

## What the current Android integration does

- Email/password sign-up and sign-in through Supabase Auth.
- Refreshes sessions and encrypts stored session tokens with the Android Keystore.
- Reads public listings through the RLS-protected `search_public_profiles` function.
- Searches display name, Snapchat username, country, bio, and interests.
- Creates or updates a profile only for the authenticated owner.
- Publishes only after the profile form's explicit consent.
- Allows the owner to hide, republish, or delete their own listing.
- Submits reports for authenticated users.

## Security model and remaining work

- Profiles start with `is_public = false`; public visibility is opt-in.
- RLS and narrow Data API grants restrict discovery and profile writes. Anonymous discovery and signed-in discovery use separate policies; signed-out users cannot query private block records.
- Report records are private to the reporter after submission. A trusted moderation console/workflow is still needed to review and resolve reports.
- The `blocks` table and RLS are prepared, but the Android block-management interface is not yet connected.
- Add rate limits/abuse controls, account deletion, report moderation, database tests, and full device testing before public launch.

Snapfind is independent and is not affiliated with or endorsed by Snapchat. It never requests Snapchat passwords or scrapes Snapchat accounts.
