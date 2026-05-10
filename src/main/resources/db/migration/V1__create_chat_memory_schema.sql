CREATE TABLE conversations (
    id          UUID                        PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id  VARCHAR(255)                NOT NULL,
    model       VARCHAR(100)                NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_conversations_session_id ON conversations (session_id);

CREATE TABLE messages (
    id               UUID                        PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id  UUID                        NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    role             VARCHAR(20)                 NOT NULL,
    content          TEXT                        NOT NULL,
    sequence_number  INTEGER                     NOT NULL,
    created_at       TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT now(),

    CONSTRAINT messages_role_check CHECK (role IN ('user', 'assistant', 'system')),
    CONSTRAINT messages_sequence_non_negative CHECK (sequence_number >= 0),
    UNIQUE (conversation_id, sequence_number)
);

CREATE INDEX idx_messages_conversation_id ON messages (conversation_id);