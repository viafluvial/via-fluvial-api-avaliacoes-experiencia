-- Elegibilidade de avaliacao de frete registrada a partir do evento freight.delivered (api-fretes-cargas).
-- Janela: expires_at = delivered_at + 30 dias. event_id garante idempotencia por evento.
CREATE TABLE "sc-avaliacoes-experiencia".freight_evaluation_eligibility (
    shipment_id        UUID PRIMARY KEY,
    boatman_id         UUID NOT NULL,
    contractor_user_id UUID NOT NULL,
    recipient_user_id  UUID,
    delivered_at       TIMESTAMPTZ NOT NULL,
    expires_at         TIMESTAMPTZ NOT NULL,
    event_id           UUID NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_freight_evaluation_eligibility_event UNIQUE (event_id),
    CONSTRAINT ck_freight_evaluation_eligibility_window CHECK (expires_at > delivered_at)
);
CREATE INDEX ix_freight_evaluation_eligibility_boatman
    ON "sc-avaliacoes-experiencia".freight_evaluation_eligibility (boatman_id);
