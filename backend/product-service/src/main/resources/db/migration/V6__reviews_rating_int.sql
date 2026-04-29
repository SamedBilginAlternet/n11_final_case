-- Widen reviews.rating from SMALLINT to INTEGER to match the JPA entity
-- (Review.rating is Integer).  Hibernate 6 strict schema validation rejects
-- the int2/int4 mismatch on startup.  SMALLINT → INTEGER is a lossless
-- widening conversion so no row rewrite penalty beyond the ALTER TABLE.

ALTER TABLE reviews
    ALTER COLUMN rating TYPE INTEGER;
