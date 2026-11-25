-- Server-side discovery search. This searches text fields and entries in the
-- interests array while respecting the caller's RLS policies on profiles.
create or replace function public.search_public_profiles(
    search_query text default null,
    country_filter text default null,
    max_results integer default 100
)
returns setof public.public_profiles
language sql
stable
security invoker
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
              from unnest(p.interests) as interest
              where interest ilike '%' || btrim(search_query) || '%'
          )
      )
      and (
          nullif(btrim(country_filter), '') is null
          or lower(p.country) = lower(btrim(country_filter))
      )
    order by p.created_at desc
    limit least(greatest(coalesce(max_results, 100), 1), 100);
$$;

revoke all on function public.search_public_profiles(text, text, integer) from public;
grant execute on function public.search_public_profiles(text, text, integer) to anon, authenticated;
