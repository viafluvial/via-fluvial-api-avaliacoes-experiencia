package br.com.viafluvial.avaliacoesexperiencia.domain.model;

import br.com.viafluvial.avaliacoesexperiencia.domain.exception.IneligibleEvaluationException;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/** Evidence that a shipment was delivered, registered from the freight.delivered event. */
public record FreightEligibility(
        UUID shipmentId,
        UUID boatmanId,
        UUID contractorUserId,
        UUID recipientUserId,
        OffsetDateTime deliveredAt,
        OffsetDateTime expiresAt,
        UUID eventId) {

    public static final int DEFAULT_WINDOW_DAYS = 30;

    public static final String NOT_DELIVERED = "SHIPMENT_NOT_DELIVERED";
    public static final String WINDOW_EXPIRED = "EVALUATION_WINDOW_EXPIRED";
    public static final String BOATMAN_MISMATCH = "BOATMAN_MISMATCH";
    public static final String NOT_PARTICIPANT = "NOT_PARTICIPANT";

    public FreightEligibility {
        Objects.requireNonNull(shipmentId, "shipmentId is required");
        Objects.requireNonNull(boatmanId, "boatmanId is required");
        Objects.requireNonNull(contractorUserId, "contractorUserId is required");
        Objects.requireNonNull(deliveredAt, "deliveredAt is required");
        Objects.requireNonNull(expiresAt, "expiresAt is required");
        Objects.requireNonNull(eventId, "eventId is required");
    }

    public static FreightEligibility fromDelivery(UUID eventId, UUID shipmentId, UUID boatmanId,
            UUID contractorUserId, UUID recipientUserId, OffsetDateTime deliveredAt, int windowDays) {
        Objects.requireNonNull(deliveredAt, "deliveredAt is required");
        if (windowDays < 1) throw new IllegalArgumentException("Evaluation window must be positive");
        return new FreightEligibility(shipmentId, boatmanId, contractorUserId, recipientUserId, deliveredAt,
                deliveredAt.plusDays(windowDays), eventId);
    }

    public boolean isOpenAt(OffsetDateTime now) {
        return !now.isAfter(expiresAt);
    }

    /** Fails with a stable reason code when the rater cannot evaluate this shipment now. */
    public void verify(UUID expectedBoatmanId, UUID raterUserId, RaterRole raterRole, OffsetDateTime now) {
        if (!isOpenAt(now)) throw new IneligibleEvaluationException(WINDOW_EXPIRED);
        if (!boatmanId.equals(expectedBoatmanId)) throw new IneligibleEvaluationException(BOATMAN_MISMATCH);
        UUID participant = raterRole == RaterRole.CONTRATANTE ? contractorUserId : recipientUserId;
        if (raterUserId == null || !raterUserId.equals(participant)) {
            throw new IneligibleEvaluationException(NOT_PARTICIPANT);
        }
    }
}
