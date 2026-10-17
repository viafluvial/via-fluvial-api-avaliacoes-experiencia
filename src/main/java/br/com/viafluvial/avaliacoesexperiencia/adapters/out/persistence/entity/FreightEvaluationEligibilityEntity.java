package br.com.viafluvial.avaliacoesexperiencia.adapters.out.persistence.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "freight_evaluation_eligibility", schema = "sc-avaliacoes-experiencia")
public class FreightEvaluationEligibilityEntity {
    @Id
    private UUID shipmentId;
    private UUID boatmanId;
    private UUID contractorUserId;
    private UUID recipientUserId;
    private OffsetDateTime deliveredAt;
    private OffsetDateTime expiresAt;
    private UUID eventId;
    private OffsetDateTime createdAt;

    public UUID getShipmentId() { return shipmentId; }
    public void setShipmentId(UUID shipmentId) { this.shipmentId = shipmentId; }
    public UUID getBoatmanId() { return boatmanId; }
    public void setBoatmanId(UUID boatmanId) { this.boatmanId = boatmanId; }
    public UUID getContractorUserId() { return contractorUserId; }
    public void setContractorUserId(UUID contractorUserId) { this.contractorUserId = contractorUserId; }
    public UUID getRecipientUserId() { return recipientUserId; }
    public void setRecipientUserId(UUID recipientUserId) { this.recipientUserId = recipientUserId; }
    public OffsetDateTime getDeliveredAt() { return deliveredAt; }
    public void setDeliveredAt(OffsetDateTime deliveredAt) { this.deliveredAt = deliveredAt; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(OffsetDateTime expiresAt) { this.expiresAt = expiresAt; }
    public UUID getEventId() { return eventId; }
    public void setEventId(UUID eventId) { this.eventId = eventId; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
