CREATE TABLE "sc-avaliacoes-experiencia".outbox_event (
    id UUID PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    aggregate_id UUID NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    next_attempt_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_outbox_dispatch ON "sc-avaliacoes-experiencia".outbox_event (status, next_attempt_at);

CREATE TABLE "sc-avaliacoes-experiencia".processed_event (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    payload_hash VARCHAR(64) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL
);