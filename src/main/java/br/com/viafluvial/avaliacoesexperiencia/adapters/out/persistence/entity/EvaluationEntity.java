package br.com.viafluvial.avaliacoesexperiencia.adapters.out.persistence.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "experience_evaluation", schema = "sc-avaliacoes-experiencia")
public class EvaluationEntity {

    @Id
    private UUID id;
    private UUID tripId;
    private UUID passengerId;
    private UUID bookingId;
    private UUID ticketId;
    private UUID boatmanId;
    private UUID vesselId;
    private UUID routeId;
    private int overallScore;
    private Integer platformScore;
    private Integer boatmanScore;
    private Integer vesselScore;
    private Integer routeScore;
    private Integer boardingScore;
    private Integer serviceScore;
    private Integer npsScore;
    private String manifestationType;
    @Column(length = 1000)
    private String comment;
    @Enumerated(EnumType.STRING)
    private br.com.viafluvial.avaliacoesexperiencia.domain.model.EvaluationStatus status;
    private boolean anonymousPublicDisplay;
    @Column(length = 1000)
    private String boatmanResponse;
    @Column(length = 500)
    private String moderationReason;
    private String idempotencyKey;
    private OffsetDateTime eligibleUntil;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    @Version
    private long version;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "evaluation_tag", schema = "sc-avaliacoes-experiencia",
            joinColumns = @JoinColumn(name = "evaluation_id"))
    @Column(name = "tag_code")
    private List<String> tags = new ArrayList<>();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTripId() { return tripId; }
    public void setTripId(UUID tripId) { this.tripId = tripId; }
    public UUID getPassengerId() { return passengerId; }
    public void setPassengerId(UUID passengerId) { this.passengerId = passengerId; }
    public UUID getBookingId() { return bookingId; }
    public void setBookingId(UUID bookingId) { this.bookingId = bookingId; }
    public UUID getTicketId() { return ticketId; }
    public void setTicketId(UUID ticketId) { this.ticketId = ticketId; }
    public UUID getBoatmanId() { return boatmanId; }
    public void setBoatmanId(UUID boatmanId) { this.boatmanId = boatmanId; }
    public UUID getVesselId() { return vesselId; }
    public void setVesselId(UUID vesselId) { this.vesselId = vesselId; }
    public UUID getRouteId() { return routeId; }
    public void setRouteId(UUID routeId) { this.routeId = routeId; }
    public int getOverallScore() { return overallScore; }
    public void setOverallScore(int overallScore) { this.overallScore = overallScore; }
    public Integer getPlatformScore() { return platformScore; }
    public void setPlatformScore(Integer platformScore) { this.platformScore = platformScore; }
    public Integer getBoatmanScore() { return boatmanScore; }
    public void setBoatmanScore(Integer boatmanScore) { this.boatmanScore = boatmanScore; }
    public Integer getVesselScore() { return vesselScore; }
    public void setVesselScore(Integer vesselScore) { this.vesselScore = vesselScore; }
    public Integer getRouteScore() { return routeScore; }
    public void setRouteScore(Integer routeScore) { this.routeScore = routeScore; }
    public Integer getBoardingScore() { return boardingScore; }
    public void setBoardingScore(Integer boardingScore) { this.boardingScore = boardingScore; }
    public Integer getServiceScore() { return serviceScore; }
    public void setServiceScore(Integer serviceScore) { this.serviceScore = serviceScore; }
    public Integer getNpsScore() { return npsScore; }
    public void setNpsScore(Integer npsScore) { this.npsScore = npsScore; }
    public String getManifestationType() { return manifestationType; }
    public void setManifestationType(String manifestationType) { this.manifestationType = manifestationType; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public br.com.viafluvial.avaliacoesexperiencia.domain.model.EvaluationStatus getStatus() { return status; }
    public void setStatus(br.com.viafluvial.avaliacoesexperiencia.domain.model.EvaluationStatus status) { this.status = status; }
    public boolean isAnonymousPublicDisplay() { return anonymousPublicDisplay; }
    public void setAnonymousPublicDisplay(boolean anonymousPublicDisplay) { this.anonymousPublicDisplay = anonymousPublicDisplay; }
    public String getBoatmanResponse() { return boatmanResponse; }
    public void setBoatmanResponse(String boatmanResponse) { this.boatmanResponse = boatmanResponse; }
    public String getModerationReason() { return moderationReason; }
    public void setModerationReason(String moderationReason) { this.moderationReason = moderationReason; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public OffsetDateTime getEligibleUntil() { return eligibleUntil; }
    public void setEligibleUntil(OffsetDateTime eligibleUntil) { this.eligibleUntil = eligibleUntil; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = new ArrayList<>(tags); }
}