package br.com.viafluvial.avaliacoesexperiencia.application.model;

import br.com.viafluvial.avaliacoesexperiencia.domain.model.EligibilityEvidence;
import java.time.OffsetDateTime;
import java.util.UUID;

public record EligibilityDecision(UUID tripId, boolean eligible, String reasonCode, boolean alreadyEvaluated,
        OffsetDateTime eligibleUntil, UUID bookingId, UUID ticketId, EligibilityEvidence evidence) {

    public static EligibilityDecision denied(UUID tripId, String reasonCode, boolean alreadyEvaluated) {
        return new EligibilityDecision(tripId, false, reasonCode, alreadyEvaluated, null, null, null, null);
    }

    public static EligibilityDecision allowed(EligibilityEvidence evidence) {
        return new EligibilityDecision(evidence.tripId(), true, "ELIGIBLE", false, evidence.eligibleUntil(),
                evidence.bookingId(), evidence.ticketId(), evidence);
    }
}