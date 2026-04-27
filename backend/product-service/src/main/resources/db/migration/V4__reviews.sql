-- Product reviews — one row per (product, user). The aggregate columns on
-- products (rating_average, rating_count) are kept in sync at the service
-- layer when reviews are inserted/updated/deleted, avoiding a per-request
-- AVG() over the whole reviews table.
--
-- Why a unique constraint on (product_id, user_id):
--   * Prevents review spam from a single user.
--   * Lets PUT-style "update my review" become an UPDATE-or-INSERT trivially.

CREATE TABLE reviews (
    id          BIGSERIAL PRIMARY KEY,
    product_id  BIGINT       NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    user_id     BIGINT       NOT NULL,
    user_name   VARCHAR(160) NOT NULL,
    rating      SMALLINT     NOT NULL CHECK (rating BETWEEN 1 AND 5),
    body        VARCHAR(2000),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_reviews_product_user UNIQUE (product_id, user_id)
);

CREATE INDEX idx_reviews_product_created ON reviews(product_id, created_at DESC);
