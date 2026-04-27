-- Refresh token store. We use opaque random strings (not JWT) so the auth-service
-- can revoke individual sessions without touching the access-token issuer config.
-- Tokens are SHA-256 hashed at rest: a DB dump never yields usable refresh tokens.
--
-- Rotation contract:
--   * Each /api/auth/refresh call consumes the presented refresh, marks it
--     revoked_at = now(), and issues a brand-new one with replaced_by set on
--     the old row.
--   * If a refresh token is replayed (already revoked but presented again),
--     we treat it as theft and revoke the entire family (every token sharing
--     the same family_id). The user is forced to re-authenticate.

CREATE TABLE refresh_tokens (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    -- SHA-256 hex (64 chars). Indexed unique so refresh lookup is O(log n).
    token_hash      VARCHAR(64)  NOT NULL UNIQUE,
    -- All tokens born from the same login share family_id; lets reuse-detection
    -- nuke the entire chain in one UPDATE.
    family_id       UUID         NOT NULL,
    expires_at      TIMESTAMPTZ  NOT NULL,
    revoked_at      TIMESTAMPTZ,
    replaced_by_id  BIGINT       REFERENCES refresh_tokens(id) ON DELETE SET NULL,
    user_agent      VARCHAR(255),
    ip              VARCHAR(64),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_refresh_tokens_user      ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_family    ON refresh_tokens(family_id);
CREATE INDEX idx_refresh_tokens_expires   ON refresh_tokens(expires_at);
