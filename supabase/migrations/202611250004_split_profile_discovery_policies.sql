-- Anonymous visitors may discover only profiles explicitly published by their owner.
-- Signed-in direct table reads are limited to the signed-in user's own profile.
-- The public discovery RPC applies block filtering and is hardened in migration 0005.

drop policy if exists "Users can discover public profiles" on public.profiles;
drop policy if exists "Anonymous users can discover public profiles" on public.profiles;
drop policy if exists "Authenticated users can discover public profiles" on public.profiles;
drop policy if exists "Authenticated users can read own profile" on public.profiles;

create policy "Anonymous users can discover public profiles"
on public.profiles for select to anon
using (is_public = true);

create policy "Authenticated users can read own profile"
on public.profiles for select to authenticated
using (id = (select auth.uid()));
