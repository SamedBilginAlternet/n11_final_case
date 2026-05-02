-- Address type pill (Ev / Ofis / Diğer) — surfaces in the storefront's
-- address book and the order detail summary so the user can scan the list
-- by icon without reading every label.  Title still exists as a free-text
-- field for cases like "Annemin evi" or "Şehir dışı yazlık"; type is the
-- coarse-grained category used for icon + filtering.
--
-- Default 'OTHER' for any pre-existing row so the NOT NULL backfill is
-- mechanical; the upcoming DB wipe will reset everything anyway.

ALTER TABLE addresses
    ADD COLUMN address_type VARCHAR(20) NOT NULL DEFAULT 'OTHER';
