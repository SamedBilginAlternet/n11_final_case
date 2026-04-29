-- Full-text search infra for product list/search.
--
-- Why: existing search uses LIKE '%word%' which can't use the btree index
-- and is order-sensitive ("kablosuz kulaklık" doesn't match "kulaklık
-- kablosuz").  PostgreSQL's tsvector + GIN gives us:
--   - relevance ranking via ts_rank
--   - language-aware stemming (Turkish: "telefonlar" → "telefon")
--   - accent-insensitive matching ("şarj" matches "sarj")
--   - O(log n) lookups via GIN
--
-- Trade-offs noted:
-- 'turkish' text-search config ships with PostgreSQL but its stemming is
-- modest — good for plurals/case endings, not for compound words.  Combined
-- with unaccent it's still a step change vs. LIKE.

CREATE EXTENSION IF NOT EXISTS unaccent;

-- IMMUTABILITY: Postgres only accepts IMMUTABLE expressions in STORED
-- generated columns.  Both unaccent() forms ship as STABLE (they read the
-- dictionary file at call time), so we wrap the two-arg form in our own
-- SQL function declared IMMUTABLE.  This is the documented escape hatch
-- — see https://www.postgresql.org/docs/current/unaccent.html "Functions"
-- section, which notes the wrapper pattern explicitly for index/generated
-- column use.  The dictionary is effectively read-only at runtime, so the
-- "lie" is safe: changing the dictionary file requires a server restart
-- and reindex anyway.
CREATE OR REPLACE FUNCTION public.immutable_unaccent(text)
    RETURNS text
    LANGUAGE sql
    IMMUTABLE
    PARALLEL SAFE
    STRICT
AS $$
    SELECT public.unaccent('public.unaccent'::regdictionary, $1)
$$;

-- Generated tsvector column — Postgres maintains it automatically on
-- INSERT/UPDATE, so no application-side denormalisation drift.  Name +
-- description weighted differently: a hit on the title (A) ranks above a
-- hit in the description body (B), so "iphone" returns the iPhone page
-- before pages whose description merely mentions iPhone.
ALTER TABLE products
    ADD COLUMN search_tsv tsvector
    GENERATED ALWAYS AS (
        setweight(
            to_tsvector('turkish'::regconfig,
                public.immutable_unaccent(coalesce(name, ''))),
            'A')
        ||
        setweight(
            to_tsvector('turkish'::regconfig,
                public.immutable_unaccent(coalesce(description, ''))),
            'B')
    ) STORED;

CREATE INDEX ix_products_search_tsv ON products USING GIN (search_tsv);

-- Price + rating filter helpers — partial searches with these filters
-- are common (sidebar facets) and we want them to plan-skip non-matches.
CREATE INDEX ix_products_price          ON products (price);
CREATE INDEX ix_products_rating_average ON products (rating_average DESC);
