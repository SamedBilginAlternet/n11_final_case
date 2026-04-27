-- Adds shipping address snapshot + lifecycle timestamps for fulfilment tracking.
--
-- Address is denormalized onto the order: editing the user's address book
-- after the fact must NOT mutate past orders.  The truth-source while the
-- order is alive is right here in the orders table.
--
-- Lifecycle clocks (confirmed/processing/shipped/delivered/cancelled_at) let
-- the frontend render a real timeline ("payment confirmed 14:02, dispatched
-- 14:08…") without joining a separate audit table.

ALTER TABLE orders
    ADD COLUMN shipping_recipient   VARCHAR(120),
    ADD COLUMN shipping_phone       VARCHAR(32),
    ADD COLUMN shipping_line1       VARCHAR(255),
    ADD COLUMN shipping_city        VARCHAR(80),
    ADD COLUMN shipping_district    VARCHAR(80),
    ADD COLUMN shipping_postal_code VARCHAR(16),
    ADD COLUMN confirmed_at         TIMESTAMPTZ,
    ADD COLUMN processing_at        TIMESTAMPTZ,
    ADD COLUMN shipped_at           TIMESTAMPTZ,
    ADD COLUMN delivered_at         TIMESTAMPTZ,
    ADD COLUMN cancelled_at         TIMESTAMPTZ,
    ADD COLUMN carrier              VARCHAR(60),
    ADD COLUMN tracking_number      VARCHAR(80);
