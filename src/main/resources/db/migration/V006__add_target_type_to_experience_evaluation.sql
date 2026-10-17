-- Avaliacao de frete (F3): a mesma tabela passa a registrar avaliacoes de viagem (TRIP, comportamento atual)
-- e de frete entregue (FREIGHT). Linhas existentes recebem target_type = 'TRIP' e continuam obrigadas a ter
-- viagem, reserva, bilhete e embarcacao; FREIGHT exige remessa e as tres notas do frete.
ALTER TABLE "sc-avaliacoes-experiencia".experience_evaluation
    ADD COLUMN target_type VARCHAR(20) NOT NULL DEFAULT 'TRIP',
    ADD COLUMN shipment_id UUID,
    ADD COLUMN score_punctuality SMALLINT,
    ADD COLUMN score_cargo_care SMALLINT,
    ADD COLUMN score_freight_service SMALLINT;

ALTER TABLE "sc-avaliacoes-experiencia".experience_evaluation
    ALTER COLUMN trip_id DROP NOT NULL,
    ALTER COLUMN booking_id DROP NOT NULL,
    ALTER COLUMN ticket_id DROP NOT NULL,
    ALTER COLUMN vessel_id DROP NOT NULL;

ALTER TABLE "sc-avaliacoes-experiencia".experience_evaluation
    ADD CONSTRAINT ck_evaluation_target_type CHECK (target_type IN ('TRIP', 'FREIGHT')),
    ADD CONSTRAINT ck_evaluation_score_punctuality CHECK (score_punctuality BETWEEN 1 AND 5),
    ADD CONSTRAINT ck_evaluation_score_cargo_care CHECK (score_cargo_care BETWEEN 1 AND 5),
    ADD CONSTRAINT ck_evaluation_score_freight_service CHECK (score_freight_service BETWEEN 1 AND 5),
    ADD CONSTRAINT ck_evaluation_target_references CHECK (
        (target_type = 'TRIP'
            AND trip_id IS NOT NULL AND booking_id IS NOT NULL AND ticket_id IS NOT NULL AND vessel_id IS NOT NULL
            AND shipment_id IS NULL
            AND score_punctuality IS NULL AND score_cargo_care IS NULL AND score_freight_service IS NULL)
        OR
        (target_type = 'FREIGHT'
            AND shipment_id IS NOT NULL
            AND trip_id IS NULL AND booking_id IS NULL AND ticket_id IS NULL
            AND score_punctuality IS NOT NULL AND score_cargo_care IS NOT NULL AND score_freight_service IS NOT NULL)
    );

CREATE UNIQUE INDEX uk_evaluation_freight_passenger_shipment
    ON "sc-avaliacoes-experiencia".experience_evaluation (passenger_id, shipment_id)
    WHERE target_type = 'FREIGHT';
CREATE INDEX ix_evaluation_boatman_target_status_created
    ON "sc-avaliacoes-experiencia".experience_evaluation (boatman_id, target_type, status, created_at DESC);
