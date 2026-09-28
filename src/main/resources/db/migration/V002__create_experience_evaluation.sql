CREATE TABLE "sc-avaliacoes-experiencia".experience_evaluation (
    id UUID PRIMARY KEY,
    trip_id UUID NOT NULL,
    passenger_id UUID,
    booking_id UUID NOT NULL,
    ticket_id UUID NOT NULL,
    boatman_id UUID NOT NULL,
    vessel_id UUID NOT NULL,
    route_id UUID,
    overall_score INTEGER NOT NULL CHECK (overall_score BETWEEN 1 AND 5),
    platform_score INTEGER CHECK (platform_score BETWEEN 1 AND 5),
    boatman_score INTEGER CHECK (boatman_score BETWEEN 1 AND 5),
    vessel_score INTEGER CHECK (vessel_score BETWEEN 1 AND 5),
    route_score INTEGER CHECK (route_score BETWEEN 1 AND 5),
    boarding_score INTEGER CHECK (boarding_score BETWEEN 1 AND 5),
    service_score INTEGER CHECK (service_score BETWEEN 1 AND 5),
    nps_score INTEGER CHECK (nps_score BETWEEN 0 AND 10),
    manifestation_type VARCHAR(32) NOT NULL,
    comment VARCHAR(1000),
    status VARCHAR(32) NOT NULL,
    anonymous_public_display BOOLEAN NOT NULL DEFAULT TRUE,
    boatman_response VARCHAR(1000),
    moderation_reason VARCHAR(500),
    idempotency_key VARCHAR(100),
    eligible_until TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_evaluation_active_passenger_trip
    ON "sc-avaliacoes-experiencia".experience_evaluation (passenger_id, trip_id)
    WHERE passenger_id IS NOT NULL AND status NOT IN ('ANONYMIZED', 'DELETED');
CREATE UNIQUE INDEX uk_evaluation_passenger_idempotency
    ON "sc-avaliacoes-experiencia".experience_evaluation (passenger_id, idempotency_key)
    WHERE passenger_id IS NOT NULL AND idempotency_key IS NOT NULL;
CREATE INDEX ix_evaluation_boatman_status_created
    ON "sc-avaliacoes-experiencia".experience_evaluation (boatman_id, status, created_at DESC);
CREATE INDEX ix_evaluation_status_created
    ON "sc-avaliacoes-experiencia".experience_evaluation (status, created_at DESC);