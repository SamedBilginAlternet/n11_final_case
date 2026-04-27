CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(160) NOT NULL,
    password_hash VARCHAR(200) NOT NULL,
    full_name     VARCHAR(160) NOT NULL,
    role          VARCHAR(20)  NOT NULL DEFAULT 'USER',
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX ux_users_email_lower ON users (LOWER(email));
