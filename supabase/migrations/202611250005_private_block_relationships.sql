-- Keep block relationships private while still hiding profiles blocked in either direction.
-- The discovery RPC runs with a fixed search_path and returns only public profile fields.
-- It does not expose the underlying blocks rows.

drop policy if exists "Authenticated users can discover public profiles" on public.profiles;
drop policy if exists "Authenticated users can read own profile" on public.profiles;
create policy "Authenticated users can read own profile"
on public.profiles for select to authenticated
using (id = (select auth.uid()));

drop policy if exists "Users can read their blocks" on public.blocks;
drop policy if exists "Users can read blocks they created" on public.blocks;
create policy "Users can read blocks they created"
on public.blocks for select to authenticated
using (blocker_id = (select auth.uid()));

create or replace function public.search_public_profiles(
    search_query text default null,
    country_filter text default null,
    max_results integer default 100
)
returns setof public.public_profiles
language sql
stable
security definer
set search_path = ''
as $$
    select
        p.id,
        p.display_name,
        p.snapchat_username,
        p.country,
        p.interests,
        p.bio,
        p.created_at
    from public.profiles p
    where p.is_public = true
      and (
          nullif(btrim(search_query), '') is null
          or p.display_name ilike '%' || btrim(search_query) || '%'
          or p.snapchat_username ilike '%' || btrim(search_query) || '%'
          or p.country ilike '%' || btrim(search_query) || '%'
          or p.bio ilike '%' || btrim(search_query) || '%'
          or exists (
              select 1
              from unnest(p.interests) as item(value)
              where item.value ilike '%' || btrim(search_query) || '%'
          )
      )
      and (
          nullif(btrim(country_filter), '') is null
          or lower(p.country) = lower(btrim(country_filter))
      )
      and (
          (select auth.uid()) is null
          or (
              not exists (
                  select 1
                  from public.blocks b
                  where b.blocker_id = (select auth.uid())
                    and b.blocked_profile_id = p.id
              )
              and not exists (
                  select 1
                  from public.blocks b
                  where b.blocked_profile_id = (select auth.uid())
                    and b.blocker_id = p.id
              )
          )
      )
    order by p.created_at desc
    limit least(greatest(coalesce(max_results, 100), 1), 100);
$$;

revoke all on function public.search_public_profiles(text, text, integer) from public;
grant execute on function public.search_public_profiles(text, text, integer) to anon, authenticated;
