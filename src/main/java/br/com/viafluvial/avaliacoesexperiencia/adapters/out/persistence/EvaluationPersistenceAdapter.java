package br.com.viafluvial.avaliacoesexperiencia.adapters.out.persistence;

import br.com.viafluvial.avaliacoesexperiencia.adapters.out.persistence.entity.EvaluationEntity;
import br.com.viafluvial.avaliacoesexperiencia.adapters.out.persistence.repository.EvaluationJpaRepository;
import br.com.viafluvial.avaliacoesexperiencia.application.model.BoatmanRating;
import br.com.viafluvial.avaliacoesexperiencia.application.model.FreightCriteriaAverages;
import br.com.viafluvial.avaliacoesexperiencia.application.model.PageResult;
import br.com.viafluvial.avaliacoesexperiencia.application.port.out.EvaluationRepositoryPort;
import br.com.viafluvial.avaliacoesexperiencia.domain.exception.EvaluationConflictException;
import br.com.viafluvial.avaliacoesexperiencia.domain.model.EvaluationScore;
import br.com.viafluvial.avaliacoesexperiencia.domain.model.EvaluationScores;
import br.com.viafluvial.avaliacoesexperiencia.domain.model.EvaluationStatus;
import br.com.viafluvial.avaliacoesexperiencia.domain.model.ExperienceEvaluation;
import br.com.viafluvial.avaliacoesexperiencia.domain.model.FreightScores;
import br.com.viafluvial.avaliacoesexperiencia.domain.model.ManifestationType;
import br.com.viafluvial.avaliacoesexperiencia.domain.model.NpsScore;
import br.com.viafluvial.avaliacoesexperiencia.domain.model.TargetType;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

@Component
public class EvaluationPersistenceAdapter implements EvaluationRepositoryPort {

    private final EvaluationJpaRepository repository;

    public EvaluationPersistenceAdapter(EvaluationJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public ExperienceEvaluation save(ExperienceEvaluation evaluation) {
        return toDomain(repository.save(toEntity(evaluation)));
    }

    @Override public Optional<ExperienceEvaluation> findById(UUID id) { return repository.findById(id).map(this::toDomain); }
    @Override public Optional<ExperienceEvaluation> findActiveByPassengerAndTrip(UUID passengerId, UUID tripId) { return repository.findFirstByPassengerIdAndTripId(passengerId, tripId).map(this::toDomain); }
    @Override public Optional<ExperienceEvaluation> findByIdempotencyKey(UUID passengerId, String key) { return repository.findFirstByPassengerIdAndIdempotencyKey(passengerId, key).map(this::toDomain); }

    @Override
    public PageResult<ExperienceEvaluation> findByPassenger(UUID passengerId, EvaluationStatus status, int page, int size) {
        return query(page, size, (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            predicates.add(cb.equal(root.get("passengerId"), passengerId));
            predicates.add(cb.equal(root.get("targetType"), TargetType.TRIP));
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            return cb.and(predicates.toArray(Predicate[]::new));
        });
    }

    @Override
    public PageResult<ExperienceEvaluation> findPublishedByBoatman(UUID boatmanId, int page, int size) {
        return findPublishedByBoatman(boatmanId, TargetType.TRIP, page, size);
    }

    @Override
    public PageResult<ExperienceEvaluation> findPublishedByBoatman(UUID boatmanId, TargetType targetType, int page, int size) {
        TargetType type = TargetType.orDefault(targetType);
        return query(page, size, (root, query, cb) -> cb.and(
                cb.equal(root.get("boatmanId"), boatmanId), cb.equal(root.get("status"), EvaluationStatus.PUBLISHED),
                cb.equal(root.get("targetType"), type)));
    }

    @Override
    public PageResult<ExperienceEvaluation> findForModeration(EvaluationStatus status, Integer rating, int page, int size) {
        return findForModeration(status, rating, TargetType.TRIP, page, size);
    }

    @Override
    public PageResult<ExperienceEvaluation> findForModeration(EvaluationStatus status, Integer rating, TargetType targetType,
            int page, int size) {
        return query(page, size, (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            if (rating != null) predicates.add(cb.equal(root.get("overallScore"), rating));
            if (targetType != null) predicates.add(cb.equal(root.get("targetType"), targetType));
            return cb.and(predicates.toArray(Predicate[]::new));
        });
    }

    @Override public long countByStatus(EvaluationStatus status) { return repository.countByStatusAndTargetType(status, TargetType.TRIP); }
    @Override public double averagePublishedByBoatman(UUID id) { return repository.averagePublishedByBoatman(id, TargetType.TRIP); }
    @Override public long countPublishedByBoatman(UUID id) { return repository.countPublishedByBoatman(id, TargetType.TRIP); }
    @Override public long countPublishedByBoatmanAndRating(UUID id, int rating) { return repository.countPublishedByBoatmanAndRating(id, TargetType.TRIP, rating); }
    @Override public Double averagePublishedNpsByBoatman(UUID id) { return repository.averagePublishedNpsByBoatman(id, TargetType.TRIP); }
    @Override public double averagePublished() { return repository.averagePublished(TargetType.TRIP); }
    @Override public long countPublished() { return repository.countPublished(TargetType.TRIP); }
    @Override public long countPublishedByRating(int rating) { return repository.countPublishedByRating(TargetType.TRIP, rating); }
    @Override public Double averagePublishedNps() { return repository.averagePublishedNps(TargetType.TRIP); }
    @Override public double averagePublishedByBoatman(UUID id, TargetType type) { return repository.averagePublishedByBoatman(id, TargetType.orDefault(type)); }
    @Override public long countPublishedByBoatman(UUID id, TargetType type) { return repository.countPublishedByBoatman(id, TargetType.orDefault(type)); }
    @Override public long countPublishedByBoatmanAndRating(UUID id, TargetType type, int rating) { return repository.countPublishedByBoatmanAndRating(id, TargetType.orDefault(type), rating); }

    @Override
    public FreightCriteriaAverages averagePublishedFreightCriteriaByBoatman(UUID boatmanId) {
        var rows = repository.averagePublishedFreightCriteriaByBoatman(boatmanId);
        if (rows == null || rows.isEmpty() || rows.get(0) == null) return FreightCriteriaAverages.empty();
        Object[] row = rows.get(0);
        return new FreightCriteriaAverages(decimal(row[0]), decimal(row[1]), decimal(row[2]));
    }

    @Override
    public Optional<ExperienceEvaluation> findFreightByPassengerAndShipment(UUID passengerId, UUID shipmentId) {
        return repository.findFirstByPassengerIdAndShipmentIdAndTargetType(passengerId, shipmentId, TargetType.FREIGHT)
                .map(this::toDomain);
    }

    @Override
    public ExperienceEvaluation insertFreight(ExperienceEvaluation evaluation) {
        try {
            return toDomain(repository.saveAndFlush(toEntity(evaluation)));
        } catch (DataIntegrityViolationException duplicate) {
            throw new EvaluationConflictException("Rater already evaluated this shipment", "ALREADY_EVALUATED");
        }
    }

    @Override
    public List<BoatmanRating> summarizePublishedByBoatmen(Collection<UUID> boatmanIds) {
        if (boatmanIds == null || boatmanIds.isEmpty()) return List.of();
        return repository.summarizePublishedByBoatmen(boatmanIds, TargetType.TRIP).stream()
                .map(row -> new BoatmanRating((UUID) row[0], ((Number) row[1]).doubleValue(), ((Number) row[2]).longValue()))
                .toList();
    }

    @Override
    public List<ExperienceEvaluation> findPublishedTestimonials(int minScore, int minCommentLength, int limit) {
        Specification<EvaluationEntity> specification = (root, query, cb) -> cb.and(
                cb.equal(root.get("status"), EvaluationStatus.PUBLISHED),
                cb.equal(root.get("targetType"), TargetType.TRIP),
                cb.greaterThanOrEqualTo(root.get("overallScore"), minScore),
                cb.isNotNull(root.get("comment")),
                cb.greaterThanOrEqualTo(cb.length(cb.trim(root.get("comment"))), minCommentLength));
        return repository.findAll(specification, PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "updatedAt")))
                .map(this::toDomain)
                .getContent();
    }

    @Override
    public PageResult<ExperienceEvaluation> findFreight(UUID boatmanId, EvaluationStatus status, int page, int size) {
        Specification<EvaluationEntity> specification = (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            predicates.add(cb.equal(root.get("targetType"), TargetType.FREIGHT));
            if (boatmanId != null) predicates.add(cb.equal(root.get("boatmanId"), boatmanId));
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return query(PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))),
                specification);
    }

    private PageResult<ExperienceEvaluation> query(int page, int size, Specification<EvaluationEntity> specification) {
        return query(PageRequest.of(page, size), specification);
    }

    private PageResult<ExperienceEvaluation> query(PageRequest pageRequest, Specification<EvaluationEntity> specification) {
        int page = pageRequest.getPageNumber();
        int size = pageRequest.getPageSize();
        Page<EvaluationEntity> result = repository.findAll(specification, pageRequest);
        return new PageResult<>(result.map(this::toDomain).getContent(), page, size,
                result.getTotalElements(), result.getTotalPages());
    }

    private EvaluationEntity toEntity(ExperienceEvaluation value) {
        var entity = new EvaluationEntity();
        entity.setId(value.id());
        entity.setTargetType(value.targetType()); entity.setShipmentId(value.shipmentId());
        if (value.freightScores() != null) {
            entity.setScorePunctuality(smallint(value.freightScores().punctuality()));
            entity.setScoreCargoCare(smallint(value.freightScores().cargoCare()));
            entity.setScoreFreightService(smallint(value.freightScores().service()));
        }
        entity.setTripId(value.tripId()); entity.setPassengerId(value.passengerId());
        entity.setBookingId(value.bookingId()); entity.setTicketId(value.ticketId());
        entity.setBoatmanId(value.boatmanId()); entity.setVesselId(value.vesselId()); entity.setRouteId(value.routeId());
        entity.setOverallScore(value.scores().overall().value());
        entity.setPlatformScore(score(value.scores().platform())); entity.setBoatmanScore(score(value.scores().boatman()));
        entity.setVesselScore(score(value.scores().vessel())); entity.setRouteScore(score(value.scores().route()));
        entity.setBoardingScore(score(value.scores().boarding())); entity.setServiceScore(score(value.scores().service()));
        entity.setNpsScore(value.npsScore() == null ? null : value.npsScore().value());
        entity.setManifestationType(value.manifestationType().name()); entity.setComment(value.comment());
        entity.setTags(value.tags()); entity.setStatus(value.status()); entity.setAnonymousPublicDisplay(value.anonymousPublicDisplay());
        entity.setBoatmanResponse(value.boatmanResponse()); entity.setModerationReason(value.moderationReason());
        entity.setIdempotencyKey(value.idempotencyKey()); entity.setEligibleUntil(value.eligibleUntil());
        entity.setCreatedAt(value.createdAt()); entity.setUpdatedAt(value.updatedAt());
        return entity;
    }

    private ExperienceEvaluation toDomain(EvaluationEntity value) {
        var scores = new EvaluationScores(new EvaluationScore(value.getOverallScore()), score(value.getPlatformScore()),
                score(value.getBoatmanScore()), score(value.getVesselScore()), score(value.getRouteScore()),
                score(value.getBoardingScore()), score(value.getServiceScore()));
        FreightScores freightScores = value.getTargetType() == TargetType.FREIGHT
                ? FreightScores.of(value.getScorePunctuality(), value.getScoreCargoCare(), value.getScoreFreightService())
                : null;
        return ExperienceEvaluation.restore(value.getId(), value.getTargetType(), value.getShipmentId(), value.getTripId(),
                value.getPassengerId(), value.getBookingId(),
                value.getTicketId(), value.getBoatmanId(), value.getVesselId(), value.getRouteId(), scores, freightScores,
                value.getNpsScore() == null ? null : new NpsScore(value.getNpsScore()),
                ManifestationType.valueOf(value.getManifestationType()), value.getComment(), value.getTags(), value.getStatus(),
                value.isAnonymousPublicDisplay(), value.getBoatmanResponse(), value.getModerationReason(),
                value.getIdempotencyKey(), value.getEligibleUntil(), value.getCreatedAt(), value.getUpdatedAt());
    }

    private Integer score(EvaluationScore value) { return value == null ? null : value.value(); }
    private Short smallint(EvaluationScore value) { return value == null ? null : (short) value.value(); }
    private static Double decimal(Object value) { return value == null ? null : ((Number) value).doubleValue(); }
    private EvaluationScore score(Integer value) { return value == null ? null : new EvaluationScore(value); }
}