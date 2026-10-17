package br.com.viafluvial.avaliacoesexperiencia.application.model;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Internal freight.delivered event received from api-fretes-cargas. */
public record FreightDeliveredCommand(UUID eventId, UUID shipmentId, UUID boatmanId, UUID contractorUserId,
        UUID recipientUserId, OffsetDateTime deliveredAt) {
}
