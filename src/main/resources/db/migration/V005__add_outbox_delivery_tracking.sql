ALTER TABLE "sc-avaliacoes-experiencia".outbox_event
    ADD COLUMN published_at TIMESTAMPTZ,
    ADD COLUMN last_error VARCHAR(500);