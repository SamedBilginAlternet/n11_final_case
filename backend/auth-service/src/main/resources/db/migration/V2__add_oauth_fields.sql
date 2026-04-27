ALTER TABLE users
    ALTER COLUMN password_hash DROP NOT NULL,
    ADD COLUMN oauth_provider VARCHAR(20),
    ADD COLUMN oauth_subject  VARCHAR(160);

CREATE UNIQUE INDEX ux_users_oauth ON users (oauth_provider, oauth_subject)
    WHERE oauth_provider IS NOT NULL AND oauth_subject IS NOT NULL;
