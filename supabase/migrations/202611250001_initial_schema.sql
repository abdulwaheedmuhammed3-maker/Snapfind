-- Snapfind initial schema
-- Apply this migration in the Supabase SQL editor or through the Supabase CLI.
-- Public visibility is opt-in. Never store Snapchat passwords or access tokens here.

create extension if not exists pgcrypto;

create table if not exists public.profiles (
    id uuid primary key references auth.users (id) on delete cascade,
    display_name text not null check (char_length(btrim(display_name)) between 1 and 30),
    snapchat_username text not null
        check (snapchat_username ~ '^[A-Za-z][A-Za-z0-9._-]{1,13}[A-Za-z0-9]$'),
    country text not null check (char_length(btrim(country)) between 2 and 60),
    interests text[] not null default '{}',
    bio text not null default '' check (char_length(bio) <= 140),
    is_public boolean not null default false,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create unique index if not exists profiles_snapchat_username_lower_uidx
    on public.profiles (lower(snapchat_username));

create index if not exists profiles_public_country_idx
    on public.profiles (country)
    where is_public = true;

create index if not exists profiles_public_created_at_idx
    on public.profiles (created_at desc)
    where is_public = true;

create table if not exists public.blocks (
    id uuid primary key default gen_random_uuid(),
    blocker_id uuid not null references auth.users (id) on delete cascade,
    blocked_profile_id uuid not null references public.profiles (id) on delete cascade,
    created_at timestamptz not null default now(),
    constraint blocks_no_self_block check (blocker_id <> blocked_profile_id),
    constraint blocks_unique_pair unique (blocker_id, blocked_profile_id)
);

create index if not exists blocks_blocked_profile_idx
    on public.blocks (blocked_profile_id);

create table if not exists public.reports (
    id uuid primary key default gen_random_uuid(),
    reporter_id uuid not null references auth.users (id) on delete cascade,
    reported_profile_id uuid not null references public.profiles (id) on delete cascade,
    reason text not null check (reason in ('spam', 'harassment', 'impersonation', 'inappropriate', 'other')),
    details text not null default '' check (char_length(details) <= 500),
    status text not null default 'open' check (status in ('open', 'reviewing', 'resolved', 'dismissed')),
    created_at timestamptz not null default now(),
    constraint reports_no_self_report check (reporter_id <> reported_profile_id)
);

create index if not exists reports_status_created_at_idx
    on public.reports (status, created_at desc);

create or replace function public.set_updated_at()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
    new.updated_at = now();
    return new;
end;
$$;

drop trigger if exists profiles_set_updated_at on public.profiles;
create trigger profiles_set_updated_at
before update on public.profiles
for each row execute function public.set_updated_at();

alter table public.profiles enable row level security;
alter table public.blocks enable row level security;
alter table public.reports enable row level security;

-- Users only create and manage the row matching their authenticated user id.
drop policy if exists "Owners can insert their profile" on public.profiles;
create policy "Owners can insert their profile"
on public.profiles for insert to authenticated
with check (id = (select auth.uid()));

-- Discovery SELECT policies are created separately in migration 0004.
-- Do not use an auth.uid() IS NULL exception here: it would expose every row
-- to signed-out users because auth.uid() is null for the anon role.

drop policy if exists "Owners can update their profile" on public.profiles;
create policy "Owners can update their profile"
on public.profiles for update to authenticated
using (id = (select auth.uid()))
with check (id = (select auth.uid()));

drop policy if exists "Owners can delete their profile" on public.profiles;
create policy "Owners can delete their profile"
on public.profiles for delete to authenticated
using (id = (select auth.uid()));

-- A user can see and manage only block records involving their own account.
drop policy if exists "Users can read their blocks" on public.blocks;
create policy "Users can read their blocks"
on public.blocks for select to authenticated
using (blocker_id = (select auth.uid()) or blocked_profile_id = (select auth.uid()));

drop policy if exists "Users can create their blocks" on public.blocks;
create policy "Users can create their blocks"
on public.blocks for insert to authenticated
with check (blocker_id = (select auth.uid()));

drop policy if exists "Users can remove their blocks" on public.blocks;
create policy "Users can remove their blocks"
on public.blocks for delete to authenticated
using (blocker_id = (select auth.uid()));

-- Reports can be submitted by signed-in users; only the reporter can read them.
-- Moderation updates should be performed by a trusted server/service-role workflow.
drop policy if exists "Users can submit reports" on public.reports;
create policy "Users can submit reports"
on public.reports for insert to authenticated
with check (
    reporter_id = (select auth.uid())
    and status = 'open'
    and reporter_id <> reported_profile_id
);

drop policy if exists "Users can read their own reports" on public.reports;
create policy "Users can read their own reports"
on public.reports for select to authenticated
using (reporter_id = (select auth.uid()));

-- A narrow view exposes only fields intended for public discovery.
create or replace view public.public_profiles
with (security_invoker = true)
as
select id, display_name, snapchat_username, country, interests, bio, created_at
from public.profiles
where is_public = true;

grant select on public.public_profiles to anon, authenticated;
grant select, insert, update, delete on public.profiles to authenticated;
grant select, insert, delete on public.blocks to authenticated;
grant select, insert on public.reports to authenticated;
