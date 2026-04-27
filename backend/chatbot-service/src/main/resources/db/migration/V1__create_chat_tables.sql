CREATE TABLE chat_sessions (
    id           VARCHAR(36)  PRIMARY KEY,
    user_id      BIGINT,
    guest_token  VARCHAR(64),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE chat_messages (
    id           BIGSERIAL PRIMARY KEY,
    session_id   VARCHAR(36) NOT NULL REFERENCES chat_sessions(id) ON DELETE CASCADE,
    role         VARCHAR(16) NOT NULL,
    content      TEXT        NOT NULL,
    tokens_used  INTEGER,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX ix_chat_messages_session ON chat_messages (session_id, created_at);
