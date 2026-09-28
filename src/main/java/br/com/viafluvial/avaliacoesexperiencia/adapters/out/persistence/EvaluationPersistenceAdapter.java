package br.com.viafluvial.avaliacoesexperiencia.adapters.out.persistence;

import br.com.viafluvial.avaliacoesexperiencia.adapters.out.persistence.entity.EvaluationEntity;
import br.com.viafluvial.avaliacoesexperiencia.adapters.out.persistence.repository.EvaluationJpaRepository;
import br.com.viafluvial.avaliacoesexperiencia.application.model.PageResult;
import br.com.viafluvial.avaliacoesexperiencia.application.port.out.EvaluationRepositoryPort;
import br.com.viafluvial.avaliacoesexperiencia.domain.model.EvaluationScore;
import br.com.viafluvial.avaliacoesexperiencia.domain.model.EvaluationScores;
import br.com.viafluvial.avaliacoesexperiencia.domain.model.EvaluationStatus;
import br.com.viafluvial.avaliacoesexperiencia.domain.model.ExperienceEvaluation;
import br.com.viafluvial.avaliacoesexperiencia.domain.model.ManifestationType;
import br.com.viafluvial.avaliacoesexperiencia.domain.model.NpsScore;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            return cb.and(predicates.toArray(Predicate[]::new));
        });
    }

    @Override
    public PageResult<ExperienceEvaluation> findPublishedByBoatman(UUID boatmanId, int page, int size) {
        return query(page, size, (root, query, cb) -> cb.and(
                cb.equal(root.get("boatmanId"), boatmanId), cb.equal(root.get("status"), EvaluationStatus.PUBLISHED)));
    }

    @Override
    public PageResult<ExperienceEvaluation> findForModeration(EvaluationStatus status, Integer rating, int page, int size) {
        return query(page, size, (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            if (rating != null) predicates.add(cb.equal(root.get("overallScore"), rating));
            return cb.and(predicates.toArray(Predicate[]::new));
        });
    }

    @Override public long countByStatus(EvaluationStatus status) { return repository.countByStatus(status); }
    @Override public double averagePublishedByBoatman(UUID id) { return repository.averagePublishedByBoatman(id); }
    @Override public long countPublishedByBoatman(UUID id) { return repository.countPublishedByBoatman(id); }
    @Override public long countPublishedByBoatmanAndRating(UUID id, int rating) { return repository.countPublishedByBoatmanAndRating(id, rating); }
    @Override public Double averagePublishedNpsByBoatman(UUID id) { return repository.averagePublishedNpsByBoatman(id); }
    @Override public double averagePublished() { return repository.averagePublished(); }
    @Override public long countPublished() { return repository.countPublished(); }
    @Override public long countPublishedByRating(int rating) { return repository.countPublishedByRating(rating); }
    @Override public Double averagePublishedNps() { return repository.averagePublishedNps(); }

    private PageResult<ExperienceEvaluation> query(int page, int size, Specification<EvaluationEntity> specification) {
        Page<EvaluationEntity> result = repository.findAll(specification, PageRequest.of(page, size));
        return new PageResult<>(result.map(this::toDomain).getContent(), page, size,
                result.getTotalElements(), result.getTotalPages());
    }

    private EvaluationEntity toEntity(ExperienceEvaluation value) {
        var entity = new EvaluationEntity();
        entity.setId(value.id());
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
        return ExperienceEvaluation.restore(value.getId(), value.getTripId(), value.getPassengerId(), value.getBookingId(),
                value.getTicketId(), value.getBoatmanId(), value.getVesselId(), value.getRouteId(), scores,
                value.getNpsScore() == null ? null : new NpsScore(value.getNpsScore()),
                ManifestationType.valueOf(value.getManifestationType()), value.getComment(), value.getTags(), value.getStatus(),
                value.isAnonymousPublicDisplay(), value.getBoatmanResponse(), value.getModerationReason(),
                value.getIdempotencyKey(), value.getEligibleUntil(), value.getCreatedAt(), value.getUpdatedAt());
    }

    private Integer score(EvaluationScore value) { return value == null ? null : value.value(); }
    private EvaluationScore score(Integer value) { return value == null ? null : new EvaluationScore(value); }
}