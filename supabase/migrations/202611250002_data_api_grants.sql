-- Tighten Data API grants for the opt-in profile directory.
-- The security_invoker discovery view requires the caller to have SELECT on
-- every underlying column referenced by the view, including is_public.

revoke all on table public.profiles from anon, authenticated;
grant select (
    id,
    display_name,
    snapchat_username,
    country,
    interests,
    bio,
    is_public,
    created_at
) on table public.profiles to anon, authenticated;

grant insert (
    id,
    display_name,
    snapchat_username,
    country,
    interests,
    bio,
    is_public
) on table public.profiles to authenticated;

grant update (
    display_name,
    snapchat_username,
    country,
    interests,
    bio,
    is_public
) on table public.profiles to authenticated;

grant delete on table public.profiles to authenticated;

revoke all on table public.blocks from anon, authenticated;
grant select, insert, delete on table public.blocks to authenticated;

revoke all on table public.reports from anon, authenticated;
grant select, insert on table public.reports to authenticated;

revoke all on table public.public_profiles from anon, authenticated;
grant select on table public.public_profiles to anon, authenticated;
