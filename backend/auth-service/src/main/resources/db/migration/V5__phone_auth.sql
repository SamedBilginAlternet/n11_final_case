-- Phone-number authentication support.  Phone becomes the primary identifier
-- for new registrations (Firebase Phone Auth flow) while existing email/OAuth
-- accounts keep working as-is.  Email and full_name are relaxed to NULL so a
-- phone-only signup doesn't have to lie about either; the checkout flow
-- prompts for email at order time, and full_name defaults to the phone string
-- until the user edits their profile.

ALTER TABLE users
    ALTER COLUMN email     DROP NOT NULL,
    ALTER COLUMN full_name DROP NOT NULL,
    ADD COLUMN phone_number VARCHAR(20);

-- Unique-when-present, same pattern as the OAuth (provider, subject) index.
-- Postgres allows many NULLs in a partial unique index, so legacy email-only
-- users coexist without a number.
CREATE UNIQUE INDEX ux_users_phone_number ON users (phone_number)
    WHERE phone_number IS NOT NULL;
