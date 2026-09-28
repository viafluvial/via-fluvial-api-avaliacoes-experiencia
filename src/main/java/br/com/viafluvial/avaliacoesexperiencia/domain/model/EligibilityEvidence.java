package br.com.viafluvial.avaliacoesexperiencia.domain.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record EligibilityEvidence(
        UUID tripId,
        UUID passengerId,
        UUID bookingId,
        UUID ticketId,
        UUID boatmanId,
        UUID vesselId,
        UUID routeId,
        OffsetDateTime completedAt,
        OffsetDateTime eligibleUntil) {

    public boolean isEligibleAt(OffsetDateTime now) {
        return !now.isAfter(eligibleUntil);
    }
}