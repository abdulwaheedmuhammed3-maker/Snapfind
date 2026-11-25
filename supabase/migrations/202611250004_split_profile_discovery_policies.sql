-- Separate anonymous discovery from signed-in block filtering.
-- This prevents the anon role from needing any privilege on the private blocks table.

drop policy if exists "Users can discover public profiles" on public.profiles;
drop policy if exists "Anonymous users can discover public profiles" on public.profiles;
drop policy if exists "Authenticated users can discover public profiles" on public.profiles;

create policy "Anonymous users can discover public profiles"
on public.profiles for select to anon
using (is_public = true);

create policy "Authenticated users can discover public profiles"
on public.profiles for select to authenticated
using (
    (is_public = true or id = (select auth.uid()))
    and not exists (
        select 1
        from public.blocks b
        where b.blocker_id = (select auth.uid())
          and b.blocked_profile_id = profiles.id
    )
    and not exists (
        select 1
        from public.blocks b
        where b.blocked_profile_id = (select auth.uid())
          and b.blocker_id = profiles.id
    )
);
