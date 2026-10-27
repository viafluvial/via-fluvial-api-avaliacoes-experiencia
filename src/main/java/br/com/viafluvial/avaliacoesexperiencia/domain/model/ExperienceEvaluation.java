package br.com.viafluvial.avaliacoesexperiencia.domain.model;

import br.com.viafluvial.avaliacoesexperiencia.domain.exception.EvaluationConflictException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class ExperienceEvaluation {

    private final UUID id;
    private final TargetType targetType;
    private final UUID shipmentId;
    private final UUID tripId;
    private UUID passengerId;
    private final UUID bookingId;
    private final UUID ticketId;
    private final UUID boatmanId;
    private final UUID vesselId;
    private final UUID routeId;
    private EvaluationScores scores;
    private final FreightScores freightScores;
    private NpsScore npsScore;
    private ManifestationType manifestationType;
    private String comment;
    private List<String> tags;
    private EvaluationStatus status;
    private boolean anonymousPublicDisplay;
    private String boatmanResponse;
    private String moderationReason;
    private final String idempotencyKey;
    private final OffsetDateTime eligibleUntil;
    private final OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    private ExperienceEvaluation(UUID id, TargetType targetType, UUID shipmentId, UUID tripId, UUID passengerId,
            UUID bookingId, UUID ticketId, UUID boatmanId, UUID vesselId, UUID routeId, EvaluationScores scores,
            FreightScores freightScores, NpsScore npsScore,
            ManifestationType manifestationType, String comment, List<String> tags, EvaluationStatus status,
            boolean anonymousPublicDisplay, String boatmanResponse, String moderationReason,
            String idempotencyKey, OffsetDateTime eligibleUntil, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        this.id = Objects.requireNonNull(id);
        this.targetType = TargetType.orDefault(targetType);
        if (this.targetType == TargetType.TRIP) {
            this.tripId = Objects.requireNonNull(tripId);
            this.bookingId = Objects.requireNonNull(bookingId);
            this.ticketId = Objects.requireNonNull(ticketId);
            this.vesselId = Objects.requireNonNull(vesselId);
            this.shipmentId = null;
            this.freightScores = null;
        } else {
            this.shipmentId = Objects.requireNonNull(shipmentId);
            this.freightScores = Objects.requireNonNull(freightScores);
            this.tripId = null;
            this.bookingId = null;
            this.ticketId = null;
            this.vesselId = vesselId;
        }
        this.passengerId = passengerId;
        this.boatmanId = Objects.requireNonNull(boatmanId);
        this.routeId = routeId;
        this.scores = Objects.requireNonNull(scores);
        this.npsScore = npsScore;
        this.manifestationType = Objects.requireNonNull(manifestationType);
        this.comment = normalize(comment);
        this.tags = List.copyOf(tags == null ? List.of() : tags);
        this.status = Objects.requireNonNull(status);
        this.anonymousPublicDisplay = anonymousPublicDisplay;
        this.boatmanResponse = normalize(boatmanResponse);
        this.moderationReason = normalize(moderationReason);
        this.idempotencyKey = normalize(idempotencyKey);
        this.eligibleUntil = Objects.requireNonNull(eligibleUntil);
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
    }

    public static ExperienceEvaluation submit(EligibilityEvidence evidence, EvaluationScores scores,
            NpsScore npsScore, ManifestationType manifestationType, String comment, List<String> tags,
            boolean anonymousPublicDisplay, String idempotencyKey, OffsetDateTime now) {
        if (!evidence.isEligibleAt(now)) {
            throw new EvaluationConflictException("Evaluation window has expired");
        }
        EvaluationStatus initialStatus = requiresReview(manifestationType, comment)
                ? EvaluationStatus.UNDER_REVIEW
                : EvaluationStatus.SUBMITTED;
        return new ExperienceEvaluation(UUID.randomUUID(), TargetType.TRIP, null, evidence.tripId(),
                evidence.passengerId(), evidence.bookingId(), evidence.ticketId(), evidence.boatmanId(),
                evidence.vesselId(), evidence.routeId(), scores, null, npsScore, manifestationType, comment, tags,
                initialStatus, anonymousPublicDisplay, null, null, idempotencyKey, evidence.eligibleUntil(), now, now);
    }

    /**
     * Freight evaluation made on behalf of the contractor or recipient of a delivered shipment. The rater is
     * stored as passengerId (owner), the overall score is derived from the three criteria and the public
     * display is always anonymous (no full name is ever exposed).
     */
    public static ExperienceEvaluation submitFreight(FreightEligibility eligibility, UUID raterUserId,
            FreightScores freightScores, String comment, List<String> tags, String idempotencyKey,
            OffsetDateTime now) {
        Objects.requireNonNull(raterUserId, "raterUserId is required");
        if (!eligibility.isOpenAt(now)) {
            throw new EvaluationConflictException("Evaluation window has expired");
        }
        ManifestationType manifestationType = freightScores.manifestationType();
        EvaluationStatus initialStatus = requiresReview(manifestationType, comment)
                ? EvaluationStatus.UNDER_REVIEW
                : EvaluationStatus.SUBMITTED;
        var scores = new EvaluationScores(freightScores.overall(), null, null, null, null, null, null);
        return new ExperienceEvaluation(UUID.randomUUID(), TargetType.FREIGHT, eligibility.shipmentId(), null,
                raterUserId, null, null, eligibility.boatmanId(), null, null, scores, freightScores, null,
                manifestationType, comment, tags, initialStatus, true, null, null, idempotencyKey,
                eligibility.expiresAt(), now, now);
    }

    public static ExperienceEvaluation restore(UUID id, UUID tripId, UUID passengerId, UUID bookingId,
            UUID ticketId, UUID boatmanId, UUID vesselId, UUID routeId, EvaluationScores scores,
            NpsScore npsScore, ManifestationType manifestationType, String comment, List<String> tags,
            EvaluationStatus status, boolean anonymousPublicDisplay, String boatmanResponse,
            String moderationReason, String idempotencyKey, OffsetDateTime eligibleUntil, OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {
        return restore(id, TargetType.TRIP, null, tripId, passengerId, bookingId, ticketId, boatmanId, vesselId,
                routeId, scores, null, npsScore, manifestationType, comment, tags, status, anonymousPublicDisplay,
                boatmanResponse, moderationReason, idempotencyKey, eligibleUntil, createdAt, updatedAt);
    }

    public static ExperienceEvaluation restore(UUID id, TargetType targetType, UUID shipmentId, UUID tripId,
            UUID passengerId, UUID bookingId, UUID ticketId, UUID boatmanId, UUID vesselId, UUID routeId,
            EvaluationScores scores, FreightScores freightScores, NpsScore npsScore,
            ManifestationType manifestationType, String comment, List<String> tags, EvaluationStatus status,
            boolean anonymousPublicDisplay, String boatmanResponse, String moderationReason, String idempotencyKey,
            OffsetDateTime eligibleUntil, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        return new ExperienceEvaluation(id, targetType, shipmentId, tripId, passengerId, bookingId, ticketId,
                boatmanId, vesselId, routeId, scores, freightScores, npsScore, manifestationType, comment, tags,
                status, anonymousPublicDisplay, boatmanResponse, moderationReason, idempotencyKey, eligibleUntil,
                createdAt, updatedAt);
    }

    public void update(EvaluationScores scores, NpsScore npsScore, ManifestationType manifestationType,
            String comment, List<String> tags, Boolean anonymousPublicDisplay, OffsetDateTime now) {
        if (targetType != TargetType.TRIP) {
            throw new EvaluationConflictException("Freight evaluations cannot be edited through this operation");
        }
        if (status != EvaluationStatus.SUBMITTED && status != EvaluationStatus.UNDER_REVIEW) {
            throw new EvaluationConflictException("Evaluation can no longer be edited");
        }
        if (now.isAfter(eligibleUntil)) {
            throw new EvaluationConflictException("Evaluation window has expired");
        }
        if (scores != null) this.scores = scores;
        this.npsScore = npsScore;
        if (manifestationType != null) this.manifestationType = manifestationType;
        if (comment != null) this.comment = normalize(comment);
        if (tags != null) this.tags = List.copyOf(tags);
        if (anonymousPublicDisplay != null) this.anonymousPublicDisplay = anonymousPublicDisplay;
        if (requiresReview(this.manifestationType, this.comment)) this.status = EvaluationStatus.UNDER_REVIEW;
        this.updatedAt = now;
    }

    public void publish(String reason, OffsetDateTime now) {
        requireStatus(EvaluationStatus.SUBMITTED, EvaluationStatus.UNDER_REVIEW, EvaluationStatus.CONTESTED);
        status = EvaluationStatus.PUBLISHED;
        moderationReason = normalize(reason);
        updatedAt = now;
    }

    public void reject(String reason, OffsetDateTime now) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Rejection reason is required");
        }
        requireStatus(EvaluationStatus.SUBMITTED, EvaluationStatus.UNDER_REVIEW, EvaluationStatus.CONTESTED);
        status = EvaluationStatus.REJECTED;
        moderationReason = reason.trim();
        updatedAt = now;
    }

    public void respond(UUID actorBoatmanId, String response, OffsetDateTime now) {
        if (!boatmanId.equals(actorBoatmanId)) {
            throw new EvaluationConflictException("Evaluation does not belong to this boatman");
        }
        requireStatus(EvaluationStatus.PUBLISHED);
        if (boatmanResponse != null) {
            throw new EvaluationConflictException("Evaluation already has a boatman response");
        }
        if (response == null || response.isBlank()) {
            throw new IllegalArgumentException("Response is required");
        }
        boatmanResponse = response.trim();
        updatedAt = now;
    }

    public void contest(UUID actorBoatmanId, OffsetDateTime now) {
        if (!boatmanId.equals(actorBoatmanId)) {
            throw new EvaluationConflictException("Evaluation does not belong to this boatman");
        }
        requireStatus(EvaluationStatus.PUBLISHED);
        status = EvaluationStatus.CONTESTED;
        updatedAt = now;
    }

    public void flagForReview(OffsetDateTime now) {
        if (status == EvaluationStatus.PUBLISHED || status == EvaluationStatus.SUBMITTED) {
            status = EvaluationStatus.UNDER_REVIEW;
            updatedAt = now;
        }
    }

    public void anonymize(UUID actorPassengerId, OffsetDateTime now) {
        requireOwner(actorPassengerId);
        passengerId = null;
        comment = null;
        status = EvaluationStatus.ANONYMIZED;
        anonymousPublicDisplay = true;
        updatedAt = now;
    }

    public void requireOwner(UUID actorPassengerId) {
        if (passengerId == null || !passengerId.equals(actorPassengerId)) {
            throw new EvaluationConflictException("Evaluation does not belong to this passenger");
        }
    }

    private void requireStatus(EvaluationStatus... allowed) {
        if (List.of(allowed).stream().noneMatch(candidate -> candidate == status)) {
            throw new EvaluationConflictException("Operation is not allowed in status " + status);
        }
    }

    private static boolean requiresReview(ManifestationType type, String comment) {
        return type == ManifestationType.SAFETY_REPORT || containsPersonalData(comment);
    }

    private static boolean containsPersonalData(String value) {
        if (value == null) return false;
        return value.matches("(?s).*(\\b\\d{3}\\.?\\d{3}\\.?\\d{3}-?\\d{2}\\b|[\\w.%+-]+@[\\w.-]+\\.[A-Za-z]{2,}|\\b\\d{10,11}\\b).*");
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public UUID id() { return id; }
    public TargetType targetType() { return targetType; }
    public boolean isFreight() { return targetType == TargetType.FREIGHT; }
    public UUID shipmentId() { return shipmentId; }
    public FreightScores freightScores() { return freightScores; }
    public UUID tripId() { return tripId; }
    public UUID passengerId() { return passengerId; }
    public UUID bookingId() { return bookingId; }
    public UUID ticketId() { return ticketId; }
    public UUID boatmanId() { return boatmanId; }
    public UUID vesselId() { return vesselId; }
    public UUID routeId() { return routeId; }
    public EvaluationScores scores() { return scores; }
    public NpsScore npsScore() { return npsScore; }
    public ManifestationType manifestationType() { return manifestationType; }
    public String comment() { return comment; }
    public List<String> tags() { return tags; }
    public EvaluationStatus status() { return status; }
    public boolean anonymousPublicDisplay() { return anonymousPublicDisplay; }
    public String boatmanResponse() { return boatmanResponse; }
    public String moderationReason() { return moderationReason; }
    public String idempotencyKey() { return idempotencyKey; }
    public OffsetDateTime eligibleUntil() { return eligibleUntil; }
    public OffsetDateTime createdAt() { return createdAt; }
    public OffsetDateTime updatedAt() { return updatedAt; }
}